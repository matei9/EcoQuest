import assert from 'node:assert/strict';
import { test } from 'node:test';
import { randomUUID } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import jwt from 'jsonwebtoken';

process.env.JWT_SECRET = 'trip-test-secret-at-least-32-characters';
process.env.KILO_ENABLED = 'false';
const { createApp } = await import('../src/app.js');
const { openDb } = await import('../src/db.js');

test('GPS draft retry records distance and rewards once; IDs are scoped to the account', async (t) => {
  const db = openDb(':memory:');
  const users = [1, 2].map((id) => {
    db.prepare('INSERT INTO users (id, email, pass, name, city, ref_code) VALUES (?, ?, ?, ?, ?, ?)')
      .run(id, `${id}@example.test`, 'unused', `User ${id}`, 'Iași', `CODE000${id}`);
    return jwt.sign({ uid: id, typ: 'session' }, process.env.JWT_SECRET);
  });
  const server = createApp(db).listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  t.after(async () => { await new Promise((resolve) => server.close(resolve)); db.close(); });
  const post = async (token, body) => {
    const r = await fetch(`http://127.0.0.1:${server.address().port}/api/trips`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, body: JSON.stringify(body),
    });
    return { status: r.status, body: await r.json() };
  };
  const points = Array.from({ length: 31 }, (_, i) => ({ lat: 47.16 + i * 0.0003, lon: 27.58, t: i * 20_000 }));
  const clientId = randomUUID();
  let r = await post(users[0], { mode: 'walk', points, clientId: 'not-a-uuid' });
  assert.equal(r.status, 400);
  r = await post(users[0], { mode: 'walk', points: [], clientId });
  assert.equal(r.status, 422);
  assert.equal(db.prepare('SELECT COUNT(*) AS n FROM trips').get().n, 0);
  const first = await post(users[0], { mode: 'walk', points, clientId });
  assert.equal(first.status, 200);
  // Simulate a lost success response: the saved draft is sent again.
  r = await post(users[0], { mode: 'walk', points, clientId });
  assert.equal(r.status, 200);
  assert.equal(r.body.km, first.body.km);
  assert.deepEqual(r.body.awarded, []);
  assert.deepEqual(r.body.progress, first.body.progress);
  assert.equal(db.prepare('SELECT COUNT(*) AS n FROM trips').get().n, 1);
  assert.equal((await post(users[0], { mode: 'cycle', points, clientId })).status, 409);
  assert.equal((await post(users[1], { mode: 'walk', points, clientId })).status, 200);
  assert.equal(db.prepare('SELECT COUNT(*) AS n FROM trips').get().n, 2);
  // Older app versions can still submit without a client ID.
  assert.equal((await post(users[0], { mode: 'walk', points })).status, 200);
});

test('existing trip databases gain the retry key without losing saved trips', () => {
  const directory = mkdtempSync(join(tmpdir(), 'gg-trip-migration-'));
  const path = join(directory, 'old.db');
  try {
    const old = new DatabaseSync(path);
    old.exec(`CREATE TABLE trips (id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL, mode TEXT NOT NULL,
      km REAL NOT NULL, day TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
      INSERT INTO trips (user_id, mode, km, day) VALUES (1, 'walk', 1.2, '2026-10-18')`);
    old.close();
    let db = openDb(path);
    assert.equal(db.prepare('SELECT km, client_id FROM trips').get().km, 1.2);
    assert.equal(db.prepare('SELECT client_id FROM trips').get().client_id, null);
    db.close();
    db = openDb(path); // migration is safe to run on every startup
    assert.equal(db.prepare('SELECT COUNT(*) AS n FROM trips').get().n, 1);
    db.close();
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
