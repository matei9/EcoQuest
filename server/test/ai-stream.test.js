import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { after, before, test } from 'node:test';

// Fake Kilo gateway: streams reasoning, then two content chunks, then [DONE].
const fake = createServer((req, res) => {
  let body = '';
  req.on('data', (c) => (body += c));
  req.on('end', () => {
    const { stream, model } = JSON.parse(body);
    assert.equal(stream, true);
    if (model === 'broken') return res.writeHead(429).end('{"error":"rate limited"}');
    if (model === 'silent') return; // never answers: server must give up and try the next model
    res.writeHead(200, { 'Content-Type': 'text/event-stream' });
    const chunk = (delta) => `data: ${JSON.stringify({ choices: [{ delta }] })}\n\n`;
    res.write(chunk({ role: 'assistant' }));
    res.write(chunk({ reasoning: 'thinking...' }));
    res.write(chunk({ content: 'Salut, ' }));
    res.write(chunk({ content: 'Guardian!' }));
    res.end('data: [DONE]\n\n');
  });
});
await new Promise((r) => fake.listen(0, r));

process.env.JWT_SECRET = 'test-secret-test-secret-test-secret-123';
process.env.KILO_ENABLED = 'true';
process.env.KILO_BASE_URL = `http://127.0.0.1:${fake.address().port}`;
process.env.KILO_MODEL = 'broken,silent,good';
process.env.KILO_FIRST_EVENT_MS = '300';
const { createApp } = await import('../src/app.js');
const { openDb } = await import('../src/db.js');

let server;
let base;
before(async () => {
  server = createApp(openDb(':memory:')).listen(0);
  await new Promise((r) => server.once('listening', r));
  base = `http://127.0.0.1:${server.address().port}/api`;
});
after(() => {
  server.close();
  fake.closeAllConnections();
  fake.close();
});

test('AI chat falls back past failing/silent models and streams thinking + deltas + done', async () => {
  const j = { 'Content-Type': 'application/json' };
  const s = await (await fetch(`${base}/auth/signup`, {
    method: 'POST', headers: j, body: JSON.stringify({ email: 's@t.ro', password: 'parola-buna', name: 'Stream', city: 'Altul' }),
  })).json();
  assert.ok(s.token, JSON.stringify(s));
  const res = await fetch(`${base}/ai/chat/stream`, {
    method: 'POST',
    headers: { ...j, Authorization: `Bearer ${s.token}` },
    body: JSON.stringify({ lang: 'ro', messages: [{ role: 'user', content: 'salut' }] }),
  });
  assert.equal(res.status, 200);
  assert.match(res.headers.get('content-type'), /text\/event-stream/);
  const events = (await res.text()).split('\n').filter((l) => l.startsWith('data: ')).map((l) => JSON.parse(l.slice(6)));
  assert.deepEqual(events, [
    { type: 'thinking' },
    { type: 'delta', text: 'Salut, ' },
    { type: 'delta', text: 'Guardian!' },
    { type: 'done' },
  ]);
});
