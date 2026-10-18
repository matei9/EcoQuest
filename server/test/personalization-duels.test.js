import assert from 'node:assert/strict';
import { test } from 'node:test';
import jwt from 'jsonwebtoken';
import { selectDaily, dailyQuestIds } from '../src/personalization.js';
import { today, addDays } from '../src/time.js';

process.env.JWT_SECRET = 'personal-duels-test-secret-at-least-32';
process.env.KILO_ENABLED = 'false';
const { createApp } = await import('../src/app.js');
const { openDb } = await import('../src/db.js');
const WEEK = 7 * 86400_000;

async function fixture(t) {
  const db = openDb(':memory:');
  const tokens = [1, 2, 3, 4].map((uid) => {
    db.prepare('INSERT INTO users (id, email, pass, name, city, ref_code) VALUES (?, ?, ?, ?, ?, ?)')
      .run(uid, `u${uid}@test.invalid`, 'unused', `User ${uid}`, 'Iași', `DUEL000${uid}`);
    return jwt.sign({ uid, typ: 'session' }, process.env.JWT_SECRET);
  });
  let clock = Date.now();
  const server = createApp(db, { now: () => clock }).listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  t.after(async () => { await new Promise((resolve) => server.close(resolve)); db.close(); });
  const call = async (uid, method, path, body) => {
    const r = await fetch(`http://127.0.0.1:${server.address().port}/api${path}`, {
      method, headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokens[uid - 1]}` },
      body: body ? JSON.stringify(body) : undefined,
    });
    return { status: r.status, body: await r.json() };
  };
  const friends = (a, b) => db.prepare('INSERT INTO friendships (low_id, high_id, requested_by, accepted) VALUES (?, ?, ?, 1)').run(Math.min(a, b), Math.max(a, b), a);
  const earn = (uid, points, at) => db.prepare('INSERT INTO earned_points (user_id, points, earned_at) VALUES (?, ?, ?)').run(uid, points, at);
  return { db, call, friends, earn, time: () => clock, advance: (ms) => { clock += ms; } };
}

test('daily selection is small, stable, answer-driven and excludes rural/unavailable transit', () => {
  const base = { id: 11, city: 'Iași', locality: 'rural', public_transport: 1, cat_scores: '30,0,20,15,10', quiz_answers: '[0,0,3,3,2,0,0,0,0,0]' };
  const ids = selectDaily(base, '2026-10-18');
  assert.equal(ids.length, 6);
  assert.equal(new Set(ids).size, 6);
  assert.ok(ids.includes('d16'));
  assert.ok(ids.includes('d9') && ids.includes('d10'));
  assert.ok(!ids.includes('d4') && !ids.includes('d14'));
  assert.deepEqual(selectDaily(base, '2026-10-18'), ids);
  const food = selectDaily({ ...base, cat_scores: '30,25,0,15,10', quiz_answers: '[0,0,0,0,0,3,3,0,0,0]' }, '2026-10-18');
  assert.ok(food.includes('d11') && food.includes('d12'));
  assert.notDeepEqual(food, ids);
  for (let n = 0; n < 10; n++) {
    for (const context of [{ locality: 'rural', public_transport: 1 }, { locality: 'urban', public_transport: 0 }, { locality: 'urban', public_transport: 1, city: 'Bacău' }]) {
      assert.ok(selectDaily({ ...base, ...context }, addDays('2026-10-18', n)).every((id) => !['d4', 'd14'].includes(id)));
    }
  }
});

test('quiz persists context/answers; assignments cannot be rerolled or bypassed, rural context replaces transit', async (t) => {
  const { db, call } = await fixture(t);
  let r = await call(1, 'POST', '/quiz', { answers: Array(10).fill(0), locality: 'rural', publicTransport: false });
  assert.equal(r.status, 200);
  const ids = r.body.progress.dailyQuestIds;
  assert.equal(ids.length, 6);
  assert.equal(r.body.user.locality, 'rural');
  assert.equal(db.prepare('SELECT quiz_answers FROM users WHERE id = 1').get().quiz_answers, JSON.stringify(Array(10).fill(0)));
  assert.deepEqual((await call(1, 'GET', '/me')).body.progress.dailyQuestIds, ids);
  await call(1, 'POST', '/quiz', { answers: [3, 3, 3, 3, 2, 3, 3, 3, 2, 3], locality: 'rural', publicTransport: true });
  assert.deepEqual((await call(1, 'GET', '/me')).body.progress.dailyQuestIds, ids);
  const hidden = ['d8', 'd9', 'd10', 'd11', 'd12', 'd13', 'd15'].find((id) => !ids.includes(id));
  assert.equal((await call(1, 'POST', `/quests/${hidden}/complete`, { proof: { type: 'confirm' } })).body.error, 'quest_not_assigned');
  assert.equal((await call(1, 'POST', '/quiz', { answers: Array(10).fill(0), locality: 'moon' })).status, 400);
  assert.equal((await call(1, 'POST', '/quests/d16/complete', { proof: { type: 'confirm' } })).status, 200);
  assert.equal(db.prepare('SELECT SUM(points) AS n FROM earned_points WHERE user_id = 1').get().n, 25);
  const tomorrow = dailyQuestIds(db, 1, addDays(today(), 1));
  assert.equal(tomorrow.length, 6);
  // Existing users changing from urban to rural keep other assignments and get replacements.
  db.prepare('UPDATE daily_assignments SET quest_ids = ? WHERE user_id = 1 AND day = ?')
    .run(JSON.stringify(['d4', 'd14', 'd5', 'd16', 'd8', 'd9']), today());
  const adjusted = (await call(1, 'GET', '/me')).body.progress.dailyQuestIds;
  assert.equal(adjusted.length, 6);
  assert.ok(!adjusted.includes('d4') && !adjusted.includes('d14'));
});

test('friendly invitations require friendship and consent; only points inside the seven days count and profile stays unranked', async (t) => {
  const f = await fixture(t);
  const { call, friends, earn, db } = f;
  assert.equal((await call(1, 'POST', '/duels/friends', { userId: 2 })).status, 403);
  friends(1, 2);
  const r = await call(1, 'POST', '/duels/friends', { userId: 2 });
  const id = r.body.id;
  assert.equal(r.status, 201);
  assert.equal((await call(1, 'POST', `/duels/${id}/accept`)).status, 409);
  assert.equal((await call(3, 'POST', `/duels/${id}/accept`)).status, 404);
  assert.equal((await call(3, 'DELETE', `/duels/${id}`)).status, 404);
  const starts = f.time();
  earn(1, 2500, starts - 1); // never counts historic points
  assert.equal((await call(2, 'POST', `/duels/${id}/accept`)).status, 200);
  assert.equal((await call(1, 'POST', '/duels/queue')).body.error, 'duel_busy');
  earn(1, 25, starts + 100);
  earn(2, 50, starts + 101);
  earn(1, 999, starts + WEEK); // excluded at exact deadline
  f.advance(WEEK + 1000);
  const done = (await call(1, 'GET', '/duels')).body;
  assert.equal(done.duels[0].outcome, 'loss');
  assert.equal(done.duels[0].myScore, 25);
  assert.equal(done.duels[0].opponentScore, 50);
  assert.equal(done.stats.played, 0);
  assert.equal((await call(2, 'GET', '/me')).body.progress.duelStats.wins, 0);
  assert.equal(db.prepare("SELECT COUNT(*) AS n FROM duels WHERE status = 'finished'").get().n, 1);
});

test('random queue excludes self/friends, starts once, finalizes once, and ranked win/draw points appear on profile', async (t) => {
  const f = await fixture(t);
  const { call, friends, earn, db } = f;
  friends(1, 2);
  assert.equal((await call(1, 'POST', '/duels/queue')).body.matched, false);
  assert.equal((await call(1, 'POST', '/duels/queue')).body.matched, false);
  assert.equal((await call(2, 'POST', '/duels/queue')).body.matched, false);
  const results = await Promise.all([call(3, 'POST', '/duels/queue'), call(4, 'POST', '/duels/queue')]);
  assert.ok(results.every((r) => r.body.matched));
  assert.equal(db.prepare("SELECT COUNT(*) AS n FROM duels WHERE status = 'active'").get().n, 2);
  assert.equal(db.prepare('SELECT COUNT(*) AS n FROM duel_queue').get().n, 0);
  const d = db.prepare('SELECT * FROM duels WHERE player1 = 1 OR player2 = 1').get();
  earn(1, 100, f.time() + 1);
  earn(d.player1 === 1 ? d.player2 : d.player1, 25, f.time() + 1);
  f.advance(WEEK + 10);
  assert.equal((await call(1, 'GET', '/duels')).body.stats.wins, 1);
  assert.equal((await call(1, 'GET', '/duels')).body.stats.points, 3);
  const profile = (await call(1, 'GET', '/me')).body.progress;
  assert.equal(profile.duelStats.played, 1);
  assert.equal(profile.duelStats.wins, 1);
  assert.equal(profile.duelStats.points, 3);
  assert.equal(profile.verifiedQuestEP, 0); // duel points never inflate EcoPoints
  const otherMatch = db.prepare('SELECT * FROM duels WHERE id != ?').get(d.id);
  assert.equal((await call(otherMatch.player1, 'GET', '/duels')).body.stats.draws, 1);
  assert.equal((await call(otherMatch.player1, 'GET', '/duels')).body.stats.points, 1);
});

test('queue cancellation/expiry, invitation decline and account deletion do not leave a player stuck', async (t) => {
  const f = await fixture(t);
  const { call, friends, db } = f;
  await call(1, 'POST', '/duels/queue');
  assert.equal((await call(1, 'DELETE', '/duels/queue')).status, 200);
  assert.equal((await call(1, 'GET', '/duels')).body.queued, false);
  await call(1, 'POST', '/duels/queue');
  f.advance(31 * 60_000);
  assert.equal((await call(1, 'GET', '/duels')).body.queued, false);
  friends(1, 2);
  let id = (await call(1, 'POST', '/duels/friends', { userId: 2 })).body.id;
  assert.equal((await call(2, 'DELETE', `/duels/${id}`)).status, 200);
  id = (await call(1, 'POST', '/duels/friends', { userId: 2 })).body.id;
  f.advance(WEEK + 1);
  await call(2, 'GET', '/duels');
  assert.equal((await call(2, 'POST', `/duels/${id}/accept`)).status, 409);
  await call(1, 'POST', '/duels/queue');
  await call(3, 'POST', '/duels/queue');
  db.prepare('DELETE FROM users WHERE id = 3').run();
  const result = (await call(1, 'GET', '/duels')).body;
  assert.equal(result.duels.find((d) => d.mode === 'ranked').outcome, 'win');
  assert.equal(result.stats.wins, 1);
});
