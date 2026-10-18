import { addDays } from './time.js';

export const STAGE_HEIGHTS_CM = [20, 50, 100, 200, 350, 500, 700];
const distance = (a, b) => Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / 86400_000);
export const seedTree = () => ({ stage: 1, stage_days: 0, height_cm: 20, streak: 0, missed_days: 0,
  active_days: 0, processed_day: null, last_active_day: null });

/** Completed Romanian calendar days only; a gap is processed in constant time. */
export function missTreeDays(state, days) {
  if (days <= 0 || !state.last_active_day) return;
  if (state.stage === 7) {
    // Owner policy: mature trees stay mature, shrink 1 m/day, minimum living height 1 m.
    state.height_cm = Math.max(100, state.height_cm - days * 100);
  } else {
    const steps = Math.floor((state.missed_days + days) / 2) - Math.floor(state.missed_days / 2);
    state.stage = Math.max(1, state.stage - steps);
    state.height_cm = STAGE_HEIGHTS_CM[state.stage - 1];
  }
  state.stage_days = 0;
  state.streak = 0;
  state.missed_days += days;
}

export function growTreeDay(state, day) {
  state.streak++;
  state.active_days++;
  state.missed_days = 0;
  if (state.stage === 7) state.height_cm += 100;
  else {
    state.stage_days++;
    if (state.stage_days === 3) {
      state.stage++;
      state.stage_days = 0;
      state.height_cm = STAGE_HEIGHTS_CM[state.stage - 1];
    }
  }
  state.last_active_day = day;
  state.processed_day = day;
}

/** Replay distinct mission days, preserving today's chance to act until midnight. */
export function advanceTree(state, activityDays, day) {
  const s = { ...state };
  const days = [...new Set(activityDays)].filter((d) => d <= day && (!s.processed_day || d > s.processed_day)).sort();
  for (const d of days) {
    if (s.processed_day) missTreeDays(s, distance(s.processed_day, d) - 1);
    growTreeDay(s, d);
  }
  const yesterday = addDays(day, -1);
  if (s.processed_day && s.processed_day < yesterday) {
    missTreeDays(s, distance(s.processed_day, yesterday));
    s.processed_day = yesterday;
  }
  return s;
}

const payload = (s, day) => ({ stage: s.stage, stageDays: s.stage_days, heightMeters: s.height_cm / 100,
  streak: s.streak, missedDays: s.missed_days, activeDays: s.active_days,
  lastActiveDay: s.last_active_day, activeToday: s.last_active_day === day, serverDay: day,
  health: s.missed_days === 0 ? 'healthy' : s.missed_days === 1 ? 'wilting' : 'withered',
  daysToNextStage: s.stage === 7 ? 0 : 3 - s.stage_days, mature: s.stage === 7,
  topOnly: s.height_cm > 2000 });

/** Persisted cache derived exclusively from server quest completions; never client-supplied growth. */
export function syncTree(db, uid, day) {
  const saved = db.prepare('SELECT * FROM tree_states WHERE user_id = ?').get(uid);
  const initial = saved ?? seedTree();
  const activity = db.prepare('SELECT DISTINCT day FROM completions WHERE user_id = ? AND day > ? AND day <= ? ORDER BY day')
    .all(uid, initial.processed_day ?? '', day).map((r) => r.day);
  const s = advanceTree(initial, activity, day);
  if (!saved || Object.keys(seedTree()).some((k) => s[k] !== saved[k])) {
    db.prepare(`INSERT INTO tree_states (user_id, stage, stage_days, height_cm, streak, missed_days, active_days, processed_day, last_active_day)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(user_id) DO UPDATE SET
      stage = excluded.stage, stage_days = excluded.stage_days, height_cm = excluded.height_cm,
      streak = excluded.streak, missed_days = excluded.missed_days, active_days = excluded.active_days,
      processed_day = excluded.processed_day, last_active_day = excluded.last_active_day`)
      .run(uid, s.stage, s.stage_days, s.height_cm, s.streak, s.missed_days, s.active_days, s.processed_day, s.last_active_day);
  }
  return payload(s, day);
}

export function treeLeaderboard(db, uid, day) {
  // Settle dormant players too, so opening the app cannot improve someone's ranking.
  const stale = db.prepare(`SELECT DISTINCT c.user_id FROM completions c LEFT JOIN tree_states t ON t.user_id = c.user_id
    WHERE c.day <= ? AND (t.user_id IS NULL OR t.processed_day IS NULL OR t.processed_day < ? OR c.day > t.processed_day)`)
    .all(day, addDays(day, -1));
  for (const u of stale) syncTree(db, u.user_id, day);
  const cte = `WITH ranked AS (SELECT u.id, u.name, u.city, t.stage, t.height_cm / 100.0 AS heightMeters,
    RANK() OVER (ORDER BY t.height_cm DESC) AS rank FROM tree_states t JOIN users u ON u.id = t.user_id
    WHERE t.last_active_day IS NOT NULL)`;
  const project = (r) => r ? { ...r, isYou: r.id === uid } : null;
  return { serverDay: day, players: db.prepare(`${cte} SELECT * FROM ranked ORDER BY rank, id LIMIT 50`).all().map(project),
    mine: project(db.prepare(`${cte} SELECT * FROM ranked WHERE id = ?`).get(uid)) };
}
