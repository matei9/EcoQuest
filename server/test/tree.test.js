import assert from 'node:assert/strict';
import { test } from 'node:test';
import jwt from 'jsonwebtoken';
import { addDays, today } from '../src/time.js';
import { advanceTree, seedTree, syncTree, treeLeaderboard } from '../src/tree.js';

process.env.JWT_SECRET = 'tree-test-secret-at-least-32-characters';
process.env.KILO_ENABLED = 'false';
const { createApp } = await import('../src/app.js');
const { openDb } = await import('../src/db.js');

const first = '2026-09-01';
const days = (n, from = first) => Array.from({ length: n }, (_, i) => addDays(from, i));
const user = (db, id = 1) => db.prepare('INSERT INTO users (id, email, pass, name, city, ref_code) VALUES (?, ?, ?, ?, ?, ?)')
  .run(id, `${id}@example.test`, 'unused', `Tree ${id}`, 'Iași', `TREE000${id}`);
const complete = (db, uid, day, id = 'd16') => db.prepare('INSERT INTO completions (user_id, quest_id, kind, period, day) VALUES (?, ?, ?, ?, ?)')
  .run(uid, id, 'daily', `d:${day}`, day);

test('seven stages advance once per three consecutive quest days; mature growth continues in metres', () => {
  for (let n = 1; n <= 18; n++) {
    const result = advanceTree(seedTree(), days(n), addDays(first, n - 1));
    assert.equal(result.stage, Math.min(7, 1 + Math.floor(n / 3)));
    assert.equal(result.stage_days, n === 18 ? 0 : n % 3);
    assert.equal(result.streak, n);
    assert.equal(result.active_days, n);
  }
  const mature = advanceTree(seedTree(), days(18), addDays(first, 17));
  assert.equal(mature.height_cm, 700);
  const taller = advanceTree(mature, days(14, addDays(first, 18)), addDays(first, 31));
  assert.equal(taller.stage, 7);
  assert.equal(taller.height_cm, 2100);
  assert.equal(taller.streak, 32);
});

test('today is never penalized, first missed day wilts, each pair drops a stage and return restarts stage progress', () => {
  const s = advanceTree(seedTree(), days(11), addDays(first, 10));
  assert.equal(s.stage, 4);
  assert.equal(s.stage_days, 2);
  const grace = advanceTree(s, [], addDays(first, 11));
  assert.equal(grace.missed_days, 0);
  const wilted = advanceTree(grace, [], addDays(first, 12));
  assert.equal(wilted.stage, 4);
  assert.equal(wilted.missed_days, 1);
  assert.equal(wilted.stage_days, 0);
  assert.equal(wilted.streak, 0);
  const dropped = advanceTree(wilted, [], addDays(first, 13));
  assert.equal(dropped.stage, 3);
  assert.equal(dropped.height_cm, 100);
  assert.deepEqual(advanceTree(dropped, [], addDays(first, 13)), dropped);
  const returned = advanceTree(dropped, [addDays(first, 13)], addDays(first, 13));
  assert.equal(returned.stage, 3);
  assert.equal(returned.stage_days, 1);
  assert.equal(returned.streak, 1);
  assert.equal(returned.missed_days, 0);
  const regrown = advanceTree(returned, days(2, addDays(first, 14)), addDays(first, 15));
  assert.equal(regrown.stage, 4);
  assert.equal(advanceTree(seedTree(), [], '2099-01-01').missed_days, 0);
});

test('mature trees stay mature, lose one metre per closed missed day and retain minimum living height', () => {
  const grown = advanceTree(seedTree(), days(32), addDays(first, 31));
  const missed = advanceTree(grown, [], addDays(first, 35)); // three closed missed days; today still open
  assert.equal(missed.stage, 7);
  assert.equal(missed.height_cm, 1800);
  assert.equal(missed.missed_days, 3);
  const resumed = advanceTree(missed, [addDays(first, 35)], addDays(first, 35));
  assert.equal(resumed.height_cm, 1900);
  assert.equal(resumed.missed_days, 0);
  const dormant = advanceTree(resumed, [], '2099-01-01');
  assert.equal(dormant.stage, 7);
  assert.equal(dormant.height_cm, 100);
});

test('bootstrap preserves full historical streak, repeated missions/reads cannot grow twice and future dates do not grow trees', () => {
  const db = openDb(':memory:');
  try {
    user(db);
    for (const d of days(40)) complete(db, 1, d);
    const last = addDays(first, 39);
    complete(db, 1, last, 'extra');
    complete(db, 1, '2099-01-01', 'future');
    const tree = syncTree(db, 1, last);
    assert.equal(tree.activeDays, 40); // not limited to me.activeDays' seven-day window
    assert.equal(tree.streak, 40);
    assert.equal(tree.heightMeters, 29);
    assert.equal(tree.topOnly, true);
    assert.deepEqual(syncTree(db, 1, last), tree);
    assert.equal(db.prepare('SELECT COUNT(*) AS n FROM tree_states').get().n, 1);
  } finally { db.close(); }
});

test('leaderboard settles dormant users, handles ties, preserves own rank beyond top 50 and removes deleted accounts', () => {
  const db = openDb(':memory:');
  try {
    for (let id = 1; id <= 53; id++) {
      user(db, id);
      for (const d of days(id === 53 ? 1 : 18)) complete(db, id, d);
    }
    // A seed cache must not hide subsequently recorded activity from leaderboard settlement.
    syncTree(db, 53, addDays(first, -1));
    const last = addDays(first, 17);
    let ranks = treeLeaderboard(db, 53, last);
    assert.equal(ranks.players.length, 50);
    assert.ok(ranks.players.every((p) => p.rank === 1 && p.heightMeters === 7));
    assert.equal(ranks.mine.rank, 53);
    assert.equal(ranks.mine.isYou, true);
    const later = addDays(first, 21);
    ranks = treeLeaderboard(db, 1, later);
    assert.equal(ranks.mine.heightMeters, 4); // three closed missing days even without opening own tree
    const p = ranks.players[0];
    assert.deepEqual(Object.keys(p).sort(), ['city', 'heightMeters', 'id', 'isYou', 'name', 'rank', 'stage'].sort());
    db.prepare('DELETE FROM users WHERE id = 1').run();
    assert.equal(treeLeaderboard(db, 1, later).mine, null);
    assert.equal(db.prepare('SELECT COUNT(*) AS n FROM tree_states WHERE user_id = 1').get().n, 0);
  } finally { db.close(); }
});

test('tree API is private, follows Romanian midnight and ignores client height writes', async (t) => {
  const db = openDb(':memory:');
  user(db);
  for (const d of days(9, '2026-10-10')) complete(db, 1, d);
  let clock = Date.parse('2026-10-19T20:59:59Z'); // 23:59:59 in Romania
  const server = createApp(db, { now: () => clock }).listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  t.after(async () => { await new Promise((resolve) => server.close(resolve)); db.close(); });
  const token = jwt.sign({ uid: 1, typ: 'session' }, process.env.JWT_SECRET);
  const call = (path, method = 'GET', auth = true) => fetch(`http://127.0.0.1:${server.address().port}/api${path}`, {
    method, headers: { ...(auth ? { Authorization: `Bearer ${token}` } : {}), 'Content-Type': 'application/json' },
    body: method === 'POST' ? JSON.stringify({ heightMeters: 99999 }) : undefined,
  });
  assert.equal((await call('/tree', 'GET', false)).status, 401);
  let tree = (await (await call('/tree')).json()).progress.tree;
  assert.equal(tree.serverDay, '2026-10-19');
  assert.equal(tree.health, 'healthy');
  assert.equal(tree.activeToday, false);
  assert.equal(tree.stage, 4);
  clock += 1000;
  tree = (await (await call('/tree')).json()).progress.tree;
  assert.equal(tree.serverDay, '2026-10-20');
  assert.equal(tree.health, 'wilting');
  assert.equal(tree.stage, 4);
  clock += 86400_000;
  tree = (await (await call('/tree')).json()).progress.tree;
  assert.equal(tree.health, 'withered');
  assert.equal(tree.stage, 3);
  assert.equal((await call('/tree', 'POST')).status, 404);
  assert.equal((await (await call('/leaderboard/trees')).json()).mine.heightMeters, 1);
});

test('actual quest awards grow once a day; quiz-only users remain seeds', async (t) => {
  const db = openDb(':memory:');
  user(db);
  const server = createApp(db).listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  t.after(async () => { await new Promise((resolve) => server.close(resolve)); db.close(); });
  const token = jwt.sign({ uid: 1, typ: 'session' }, process.env.JWT_SECRET);
  const post = async (path, body) => {
    const r = await fetch(`http://127.0.0.1:${server.address().port}/api${path}`, {
      method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify(body),
    });
    assert.equal(r.status, 200);
    return r.json();
  };
  let r = await post('/quiz', { answers: Array(10).fill(0), locality: 'rural', publicTransport: false });
  assert.equal(r.progress.tree.activeDays, 0);
  r = await post('/quests/d16/complete', { proof: { type: 'confirm' } });
  assert.equal(r.progress.tree.activeDays, 1);
  assert.equal(r.progress.tree.stageDays, 1);
  r = await post('/quests/w10/complete', { proof: { type: 'confirm' } });
  assert.equal(r.progress.tree.activeDays, 1);
  assert.equal(r.progress.tree.activeToday, true);
  assert.deepEqual(syncTree(db, 1, today()), r.progress.tree);
});
