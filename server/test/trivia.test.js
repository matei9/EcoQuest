import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';

process.env.JWT_SECRET = 'test-secret-test-secret-test-secret-123';
process.env.KILO_ENABLED = 'false';
const { createApp } = await import('../src/app.js');
const { openDb } = await import('../src/db.js');
const { facts, factById, selectTrivia, triviaReward } = await import('../src/facts.js');

let server;
let base;
before(async () => {
  server = createApp(openDb(':memory:')).listen(0);
  await new Promise((r) => server.once('listening', r));
  base = `http://127.0.0.1:${server.address().port}/api`;
});
after(() => server.close());

test('facts are well-formed', () => {
  assert.equal(new Set(facts.map((f) => f.id)).size, facts.length);
  for (const f of facts) {
    assert.ok(f.fact.ro && f.fact.en && f.question.ro && f.question.en, f.id);
    assert.equal(f.options.length, 4, f.id);
    assert.ok(f.correct >= 0 && f.correct < 4, f.id);
  }
});

test('selection: 10 questions, easy -> hard, seen facts first', () => {
  const seen = new Set(['eu-2050', 'beef']);
  const q = selectTrivia(seen);
  assert.equal(q.length, 10);
  assert.deepEqual(q.map((f) => f.difficulty), [1, 1, 1, 2, 2, 2, 2, 3, 3, 3]);
  assert.equal(q[3].id, 'beef'); // first medium = the seen one
  assert.equal(q[7].id, 'eu-2050'); // first hard = the seen one
});

test('partial credit only from 8/10', () => {
  assert.deepEqual([10, 9, 8, 7, 0].map((s) => triviaReward(s).ep), [100, 70, 50, 0, 0]);
});

test('daily trivia flow: no answers leaked, review at the end, once per day', async () => {
  const j = { 'Content-Type': 'application/json' };
  const s = await (await fetch(`${base}/auth/signup`, {
    method: 'POST', headers: j, body: JSON.stringify({ email: 'q@t.ro', password: 'parola-buna', name: 'Quiz', city: 'Altul' }),
  })).json();
  const H = { ...j, Authorization: `Bearer ${s.token}` };

  const fact = await (await fetch(`${base}/facts/next`, { headers: H })).json();
  assert.ok(fact.text.ro && fact.seen === 1);

  const t = await (await fetch(`${base}/trivia`, { headers: H })).json();
  assert.equal(t.done, false);
  assert.equal(t.questions.length, 10);
  assert.ok(t.questions.every((q) => !('correct' in q)));

  // 8 correct + 2 wrong -> partial 50 EP
  const answers = t.questions.map((q, i) => (i < 8 ? factById.get(q.id).correct : (factById.get(q.id).correct + 1) % 4));
  const r = await (await fetch(`${base}/trivia/submit`, { method: 'POST', headers: H, body: JSON.stringify({ token: t.token, answers }) })).json();
  assert.equal(r.score, 8);
  assert.equal(r.ep, 50);
  assert.equal(r.results.filter((x) => !x.ok).length, 2);
  assert.equal(r.progress.verifiedQuestEP, 50);
  assert.equal(r.progress.verifiedCO2SavedKg, 0);

  const again = await fetch(`${base}/trivia/submit`, { method: 'POST', headers: H, body: JSON.stringify({ token: t.token, answers }) });
  assert.equal((await again.json()).error, 'trivia_done_today');
  assert.deepEqual(await (await fetch(`${base}/trivia`, { headers: H })).json(), { done: true, score: 8, ep: 50 });
});
