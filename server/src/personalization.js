import { createHash } from 'node:crypto';
import { quests, quiz } from './catalog.js';

const category = { d1: 0, d2: 0, d3: 0, d4: 0, d14: 0, d15: 0, d9: 1, d10: 1, d11: 2, d12: 2, d5: 3, d6: 3, d7: 3, d8: 3, d13: 4 };
const transit = new Set(['d4', 'd14', 'w2a', 'w2b', 'w2c', 'm4']);
export const canUseTransit = (u) => u.locality === 'urban' && !!u.public_transport && u.city === 'Iași';
export const isTransitQuest = (id) => transit.has(id);

/** Small, stable daily set; context is explicit, never inferred from GPS. */
export function selectDaily(u, day) {
  const answers = JSON.parse(u.quiz_answers || '[]');
  const scores = u.cat_scores.split(',').map(Number);
  const maxima = [30, 25, 20, 15, 10];
  const noise = (id) => createHash('sha256').update(`${u.id}:${day}:${id}`).digest().readUInt32BE() / 0xffffffff;
  const priority = (q) => {
    const c = category[q.id];
    let value = (1 - (scores[c] || 0) / maxima[c]) * 5 + noise(q.id) * 2;
    if (['d1', 'd2', 'd15'].includes(q.id) && answers[0] >= 2) value += 2;
    if (q.id === 'd9' && answers[3] >= 2) value += 2;
    if (q.id === 'd10' && answers[2] >= 2) value += 2;
    if (['d11', 'd12'].includes(q.id) && answers[5] >= 2) value += 2;
    if (['d8', 'd13'].includes(q.id) && answers[8] >= 1) value += 2;
    return value;
  };
  const selected = ['d16', 'd5']; // community/local shopping and accessible SGR starting target
  const counts = { 3: 1 };
  const candidates = quests.filter((q) => /^d\d+$/.test(q.id) && !selected.includes(q.id)
    && (!transit.has(q.id) || canUseTransit(u))).sort((a, b) => priority(b) - priority(a));
  for (const q of candidates) {
    const c = category[q.id];
    if ((counts[c] || 0) >= 2) continue;
    selected.push(q.id);
    counts[c] = (counts[c] || 0) + 1;
    if (selected.length === 6) break;
  }
  return selected;
}

export function dailyQuestIds(db, uid, day) {
  const saved = db.prepare('SELECT quest_ids FROM daily_assignments WHERE user_id = ? AND day = ?').get(uid, day);
  const u = db.prepare('SELECT * FROM users WHERE id = ?').get(uid);
  if (saved) {
    const ids = JSON.parse(saved.quest_ids);
    const allowed = ids.filter((id) => !transit.has(id) || canUseTransit(u));
    if (allowed.length !== ids.length) {
      const replacements = selectDaily(u, day).filter((id) => !allowed.includes(id));
      const updated = [...allowed, ...replacements].slice(0, 6);
      db.prepare('UPDATE daily_assignments SET quest_ids = ? WHERE user_id = ? AND day = ?').run(JSON.stringify(updated), uid, day);
      return updated;
    }
    return ids;
  }
  const ids = selectDaily(u, day);
  db.prepare('INSERT INTO daily_assignments (user_id, day, quest_ids) VALUES (?, ?, ?)').run(uid, day, JSON.stringify(ids));
  return ids;
}
