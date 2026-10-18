import { quests, questById } from './catalog.js';
import { addDays, periodKey, periodStart, today } from './time.js';
import { dailyQuestIds, canUseTransit, isTransitQuest } from './personalization.js';
import { duelStats } from './duels.js';
import { syncTree } from './tree.js';

const metricQuests = quests.filter((q) => q.verification === 'metric');

/** Value of a metric for a user since `from` (inclusive ISO day). */
export function metricValue(db, uid, metric, from) {
  const one = (sql, ...args) => Object.values(db.prepare(sql).get(uid, ...args))[0] ?? 0;
  const tripKm = (modes) =>
    one(`SELECT COALESCE(SUM(km), 0) FROM trips WHERE user_id = ? AND day >= ? AND mode IN (${modes.map(() => '?').join(',')})`, from, ...modes);
  switch (metric) {
    case 'walkKm': return tripKm(['walk']);
    case 'cycleKm': return tripKm(['cycle']);
    case 'transitKm': return tripKm(['transit']);
    case 'greenKm': return tripKm(['transit', 'cycle']);
    case 'transitTrips': return one(`SELECT COUNT(*) FROM trips WHERE user_id = ? AND day >= ? AND mode = 'transit'`, from);
    case 'transitDays': return one(`SELECT COUNT(DISTINCT day) FROM trips WHERE user_id = ? AND day >= ? AND mode = 'transit'`, from);
    case 'receiptCount': return one('SELECT COUNT(*) FROM receipts WHERE user_id = ? AND day >= ?', from);
    case 'sgr': return one('SELECT COALESCE(SUM(count), 0) FROM receipts WHERE user_id = ? AND day >= ?', from);
    case 'eventCount': return one('SELECT COUNT(*) FROM checkins WHERE user_id = ? AND day >= ?', from);
    case 'dailyCount': return one(`SELECT COUNT(*) FROM completions WHERE user_id = ? AND day >= ? AND kind = 'daily'`, from);
    case 'weeklyCount': {
      // Auto "do N daily quests" weeklies don't count toward "complete 4 weekly challenges".
      const autos = quests.filter((q) => q.kind === 'weekly' && q.metric === 'dailyCount').map((q) => q.id);
      return one(
        `SELECT COUNT(*) FROM completions WHERE user_id = ? AND day >= ? AND kind = 'weekly' AND quest_id NOT IN (${autos.map(() => '?').join(',')})`,
        from, ...autos,
      );
    }
    case 'referrals':
      return one('SELECT COUNT(*) FROM users u WHERE u.referred_by = ? AND EXISTS (SELECT 1 FROM completions c WHERE c.user_id = u.id)');
    default: return 0;
  }
}

export function isDone(db, uid, quest, day = today()) {
  return !!db.prepare('SELECT 1 FROM completions WHERE user_id = ? AND quest_id = ? AND period = ?').get(uid, quest.id, periodKey(quest.kind, day));
}

/** Records a completion and its reward. Returns false if already done this period. */
export function award(db, uid, quest, day = today()) {
  const r = db.prepare('INSERT OR IGNORE INTO completions (user_id, quest_id, kind, period, day) VALUES (?, ?, ?, ?, ?)')
    .run(uid, quest.id, quest.kind, periodKey(quest.kind, day), day);
  if (r.changes === 0) return false;
  db.prepare('UPDATE users SET verified_ep = verified_ep + ?, co2 = co2 + ?, coins = coins + ? WHERE id = ?')
    .run(quest.ep, quest.co2, quest.coins, uid);
  if (quest.ep > 0) db.prepare('INSERT INTO earned_points (user_id, points, earned_at) VALUES (?, ?, ?)').run(uid, quest.ep, Date.now());
  syncTree(db, uid, day);
  return true;
}

/** Completes every metric quest that reached its target; repeats because completions feed counters. */
export function evaluate(db, uid, day = today()) {
  const awarded = [];
  const assigned = dailyQuestIds(db, uid, day);
  const user = db.prepare('SELECT * FROM users WHERE id = ?').get(uid);
  for (;;) {
    const ready = metricQuests.filter(
      (q) => (!/^d\d+$/.test(q.id) || assigned.includes(q.id))
        && (!isTransitQuest(q.id) || canUseTransit(user))
        && !isDone(db, uid, q, day) && metricValue(db, uid, q.metric, periodStart(q.kind, day)) >= q.target,
    );
    if (ready.length === 0) return awarded;
    for (const q of ready) if (award(db, uid, q, day)) awarded.push(q.id);
  }
}

/** Full profile + progress payload used by the app after every mutation. */
export function me(db, uid, day = today()) {
  const u = db.prepare('SELECT * FROM users WHERE id = ?').get(uid);
  if (!u) return null;
  const completedKeys = db.prepare('SELECT quest_id, day FROM completions WHERE user_id = ? AND day >= ?')
    .all(uid, addDays(day, -35)).map((r) => `${r.quest_id}@${r.day}`);
  const activeDays = db.prepare('SELECT DISTINCT day FROM completions WHERE user_id = ? AND day >= ?')
    .all(uid, addDays(day, -7)).map((r) => r.day);
  const questProgress = {};
  for (const q of metricQuests) {
    questProgress[q.id] = [metricValue(db, uid, q.metric, periodStart(q.kind, day)), q.target];
  }
  return {
    user: {
      id: u.id, email: u.email, name: u.name, city: u.city, school: u.school,
      teamId: u.team_id, refCode: u.ref_code,
      locality: u.locality, publicTransport: !!u.public_transport,
    },
    progress: {
      quizDone: !!u.quiz_done,
      categoryScores: u.cat_scores.split(',').map(Number),
      initialSelfDeclaredEP: u.initial_ep,
      verifiedQuestEP: u.verified_ep,
      verifiedCO2SavedKg: u.co2,
      ecoCoins: u.coins,
      sgrReturned: u.sgr,
      dailyQuestIds: dailyQuestIds(db, uid, day),
      duelStats: duelStats(db, uid),
      tree: syncTree(db, uid, day),
      completedKeys,
      activeDays,
      questProgress,
    },
  };
}

export { questById };
