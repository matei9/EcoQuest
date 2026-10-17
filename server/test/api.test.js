import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';

process.env.JWT_SECRET = 'test-secret-test-secret-test-secret-123';
process.env.DEMO_MODE = 'false';
process.env.KILO_ENABLED = 'false'; // no network calls in tests
const { createApp } = await import('../src/app.js');
const { openDb } = await import('../src/db.js');
const { parseSgrCount } = await import('../src/proofs.js');
const { analyzeTrack } = await import('../src/geo.js');
const { periodStart } = await import('../src/time.js');

let server;
let base;
let db;
before(async () => {
  db = openDb(':memory:');
  server = createApp(db).listen(0);
  await new Promise((r) => server.once('listening', r));
  base = `http://127.0.0.1:${server.address().port}/api`;
});
after(() => server.close());

const call = async (method, path, body, token) => {
  const res = await fetch(base + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, body: res.status === 204 ? null : await res.json() };
};
const hash = (n) => n.toString(16).padStart(64, '0');

test('periods', () => {
  assert.equal(periodStart('weekly', '2026-10-17'), '2026-10-12'); // Saturday -> Monday
  assert.equal(periodStart('weekly', '2026-10-12'), '2026-10-12');
  assert.equal(periodStart('monthly', '2026-10-17'), '2026-10-01');
});

test('SGR receipt parsing', () => {
  assert.equal(parseSgrCount('RVM Voucher\nAmbalaje: 10\nValoare garantie 5,00 lei'), 10);
  assert.equal(parseSgrCount('Bon fiscal\nGaranție SGR 2.50 LEI'), 5);
  assert.equal(parseSgrCount('Bon fiscal\nPaine 4,50\nTotal 4,50'), 0);
});

test('GPS track validation rejects car speed', () => {
  const walk = Array.from({ length: 31 }, (_, i) => ({ lat: 47.16 + i * 0.0003, lon: 27.58, t: i * 20_000 })); // ~5.4 km/h
  assert.equal(analyzeTrack(walk, 'walk').ok, true);
  const car = walk.map((p, i) => ({ ...p, t: i * 2_000 })); // ~54 km/h
  assert.equal(analyzeTrack(car, 'walk').reason, 'too_fast');
});

test('signup, quiz, quests, receipts, leaderboard, vouchers', async () => {
  let r = await call('POST', '/auth/signup', { email: 'a@b.ro', password: 'short', name: 'Ana', city: 'Iași' });
  assert.equal(r.body.error, 'weak_password');

  r = await call('POST', '/auth/signup', { email: 'a@b.ro', password: 'parola-buna', name: 'Ana', city: 'Iași', school: 'UAIC Iași' });
  assert.equal(r.status, 201);
  const token = r.body.token;
  assert.equal(r.body.progress.verifiedCO2SavedKg, 0);

  r = await call('POST', '/auth/login', { email: 'a@b.ro', password: 'gresit-gresit' });
  assert.equal(r.status, 401);
  r = await call('POST', '/auth/login', { email: 'A@B.ro', password: 'parola-buna' });
  assert.equal(r.status, 200);

  assert.equal((await call('GET', '/me')).status, 401);

  // All best answers -> 100 raw -> 2500 EP, CO2 still 0.
  r = await call('POST', '/quiz', { answers: Array(10).fill(0) }, token);
  assert.equal(r.body.progress.initialSelfDeclaredEP, 2500);
  assert.equal(r.body.progress.verifiedCO2SavedKg, 0);

  r = await call('POST', '/quests/d8/complete', { proof: { type: 'confirm' } }, token);
  assert.equal(r.status, 200);
  assert.equal(r.body.progress.verifiedQuestEP, 25);
  r = await call('POST', '/quests/d8/complete', { proof: { type: 'confirm' } }, token);
  assert.equal(r.body.error, 'already_done');

  assert.equal((await call('POST', '/quests/d9/complete', { proof: { type: 'timer', seconds: 30 } }, token)).status, 400);
  assert.equal((await call('POST', '/quests/d1/complete', { proof: { type: 'confirm' } }, token)).body.error, 'auto_quest');
  assert.equal((await call('POST', '/quests/d1/complete', { proof: { type: 'demo' } }, token)).body.error, 'demo_disabled');
  assert.equal((await call('POST', '/quests/chain2/complete', { proof: { type: 'confirm' } }, token)).body.error, 'locked');

  r = await call('POST', '/quests/d11/complete', { proof: { type: 'labels', labels: ['Car'], imageHash: hash(1) } }, token);
  assert.equal(r.body.error, 'labels_mismatch');
  r = await call('POST', '/quests/d11/complete', { proof: { type: 'labels', labels: ['Food', 'Plate'], imageHash: hash(1) } }, token);
  assert.equal(r.status, 200);

  // Receipt with 10 packages completes d5 (3) and d6 (10); the same image can't be reused.
  r = await call('POST', '/receipts', { text: 'SGR voucher\n10 ambalaje', imageHash: hash(2) }, token);
  assert.equal(r.status, 200);
  assert.deepEqual(r.body.awarded.sort(), ['d5', 'd6']);
  assert.equal(r.body.progress.sgrReturned, 10);
  assert.deepEqual(r.body.progress.questProgress.d7, [10, 20]);
  assert.equal((await call('POST', '/receipts', { text: 'SGR voucher\n10 ambalaje', imageHash: hash(2) }, token)).body.error, 'proof_already_used');

  // Walking 1.0 km completes d1 and chain1.
  const pts = Array.from({ length: 31 }, (_, i) => ({ lat: 47.16 + i * 0.0003, lon: 27.58, t: Date.now() - (30 - i) * 20_000 }));
  r = await call('POST', '/trips', { mode: 'walk', points: pts }, token);
  assert.equal(r.status, 200);
  assert.ok(r.body.awarded.includes('d1'));
  assert.ok(r.body.awarded.includes('chain1'));

  r = await call('GET', '/leaderboard/individual?metric=verified', null, token);
  assert.equal(r.body.league, 'Platinum');
  assert.equal(r.body.players[0].isYou, true);
  assert.equal((await call('GET', '/leaderboard/cities', null, token)).body[0].name, 'Iași');

  const coins = (await call('GET', '/me', null, token)).body.progress.ecoCoins;
  r = await call('POST', '/vouchers/coffee/redeem', {}, token);
  assert.equal(r.status, coins >= 150 ? 200 : 402);

  r = await call('POST', '/teams', { name: 'Eco Squad' }, token);
  assert.equal(r.status, 201);
  r = await call('GET', '/teams', null, token);
  assert.equal(r.body.teams[0].sgrThisWeek, 10);

  assert.equal((await call('POST', '/ai/chat', { messages: [{ role: 'user', content: 'salut' }] }, token)).body.error, 'ai_unavailable');
  assert.equal((await call('DELETE', '/me', null, token)).status, 204);
});
