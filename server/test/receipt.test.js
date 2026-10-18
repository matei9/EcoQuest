import assert from 'node:assert/strict';
import { test } from 'node:test';
import jwt from 'jsonwebtoken';
import { createHash } from 'node:crypto';
import { parseReceiptTimestamp, parseSgrCount } from '../src/proofs.js';
import { fakeReceiptImage, fakeReceiptOcr } from './receipt-fixture.js';

// LIDL layout transcribed from the supplied voucher; identifying numbers replaced.
const lidl = `LIDL
Valabilitate voucher 12 luni
Receipt 12345
RON 1.50
3x PET SGRM 1.50
3 1.50
Tomra 9
000000-00000000-00000-00
08:22:45 18-OCT-2026`;

test('LIDL bottom timestamp and SGRM quantity are read without keyword gating', () => {
  const result = parseReceiptTimestamp(lidl);
  assert.equal(result.day, '2026-10-18');
  assert.equal(result.time, '08:22:45');
  assert.equal(parseSgrCount(lidl), 3);
  assert.equal(parseSgrCount('3x PET'), 3);
  assert.equal(parseSgrCount('10 ambalaje\n18-OCT-2026 08:22:45'), 10);
  assert.equal(parseSgrCount('SGR 18.10.2026 08:22:45'), 0);
  assert.equal(parseReceiptTimestamp('08:22:4518-0CT-2026').day, '2026-10-18');
  assert.equal(parseReceiptTimestamp('08:22:4518-0CT-2026').time, '08:22:45');
});

test('receipt dates accept numeric, month names and either clock order, with calendar validation', () => {
  for (const text of ['18.10.2026 08:22:45', '08:22:45\n18/10/2026', '2026-10-18 08:22:45', '18 OCT 2026\n08:22:45']) {
    assert.equal(parseReceiptTimestamp(text).key, parseReceiptTimestamp(lidl).key);
  }
  assert.equal(parseReceiptTimestamp('29-FEB-2024 23:59:59').day, '2024-02-29');
  assert.equal(parseReceiptTimestamp('08:22 18-oct-2026').time, '08:22:00');
  for (const text of ['31.02.2026 08:22:45', '29-FEB-2025 08:22:45', '18.13.2026 08:22:45',
    '18.10.2026 24:22:45', '18.10.2026 08:99:45', '18.10.2026 08:22:99',
    '18.10.2026', '08:22:45', 'RON 1.50\n18.10.2026', '201001502000000139341682']) {
    assert.equal(parseReceiptTimestamp(text), null, text);
  }
});

process.env.JWT_SECRET = 'receipt-test-secret-at-least-32-characters';
process.env.KILO_ENABLED = 'false';
const { createApp } = await import('../src/app.js');
const { openDb } = await import('../src/db.js');

test('receipts require only valid printed datetime, credit packages, and reject rephotographed duplicates across users', async (t) => {
  const db = openDb(':memory:');
  const users = [1, 2].map((id) => {
    db.prepare('INSERT INTO users (id, email, pass, name, city, ref_code) VALUES (?, ?, ?, ?, ?, ?)')
      .run(id, `${id}@example.test`, 'unused', `User ${id}`, 'Iași', `RCPT000${id}`);
    return jwt.sign({ uid: id, typ: 'session' }, process.env.JWT_SECRET);
  });
  const server = createApp(db, { receiptOcr: fakeReceiptOcr }).listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  t.after(async () => { await new Promise((resolve) => server.close(resolve)); db.close(); });
  const post = async (text, n, user = 0, count) => {
    const r = await fetch(`http://127.0.0.1:${server.address().port}/api/receipts${count == null ? '' : `?count=${count}`}`, {
      method: 'POST', headers: { 'Content-Type': 'image/png', Authorization: `Bearer ${users[user]}` },
      body: fakeReceiptImage(text, n),
    });
    return { status: r.status, body: await r.json() };
  };
  const oldClient = await fetch(`http://127.0.0.1:${server.address().port}/api/receipts`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${users[0]}` },
    body: JSON.stringify({ text: lidl, imageHash: '0'.repeat(64) }),
  });
  assert.equal(oldClient.status, 426);
  assert.equal((await oldClient.json()).error, 'receipt_upgrade_required');
  let r = await post(lidl, 1);
  assert.equal(r.status, 200);
  assert.equal(r.body.count, 3);
  assert.equal(r.body.receiptTime, '08:22:45');
  assert.equal(r.body.progress.sgrReturned, 3);
  assert.ok(r.body.awarded.includes('d5'));
  assert.ok(r.body.awarded.includes('mystery'));
  assert.equal((await post(lidl, 1)).body.error, 'receipt_already_used');
  assert.equal((await post('3 PET\n18/10/2026 08:22:45', 2)).body.error, 'receipt_already_used');
  assert.equal((await post(lidl, 3, 1)).body.error, 'receipt_already_used');
  assert.equal((await post('3 PET\n18/10/2026 08:22', 4)).body.error, 'receipt_already_used');
  assert.equal(db.prepare('SELECT COUNT(*) AS n FROM receipts').get().n, 1);
  // Different seconds are different timestamps when both are legible.
  assert.equal((await post(lidl.replace('08:22:45', '08:22:46'), 2)).status, 200);
  assert.equal((await post('3 PET 31.02.2026 08:22', 5)).body.error, 'receipt_datetime_missing');
  // No keyword or age restriction. Quantity can be entered when OCR misses it.
  assert.equal((await post('01.01.2001 00:01', 5, 0, 7)).status, 200);
  assert.equal((await post('01.01.2099 00:02', 6, 0, 2)).status, 200);
  assert.equal((await post('01.01.2001 00:01:59', 7, 1, 1)).body.error, 'receipt_already_used');
  assert.equal((await post('01.01.2001 00:03', 8, 0, 0)).status, 422);
  assert.equal((await post('01.01.2001 00:03', 8, 0, 201)).status, 422);
  r = await post('01.01.2001 00:03', 8);
  assert.equal(r.status, 200);
  assert.equal(r.body.count, 0);
  assert.equal((await post('01.01.2001 00:03', 8, 0, 1)).status, 409);
});

test('mystery uses datetime-only receipt validation; failures remain retryable and accepted receipts cannot be reused', async (t) => {
  const db = openDb(':memory:');
  db.prepare('INSERT INTO users (id, email, pass, name, city, ref_code) VALUES (1, ?, ?, ?, ?, ?)')
    .run('retry@example.test', 'unused', 'Retry', 'Iași', 'RETRY001');
  const token = jwt.sign({ uid: 1, typ: 'session' }, process.env.JWT_SECRET);
  let attempts = 0;
  const server = createApp(db, { receiptOcr: async () => ++attempts === 1 ? 'no timestamp' : '18-OCT-2026 09:10:11' }).listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  t.after(async () => { await new Promise((resolve) => server.close(resolve)); db.close(); });
  const base = `http://127.0.0.1:${server.address().port}/api`;
  const image = fakeReceiptImage('fixture', 100);
  // A previous photo entry is not an accepted receipt and must not poison this flow.
  db.prepare('INSERT INTO proof_hashes (hash, user_id, kind) VALUES (?, 1, ?)')
    .run(createHash('sha256').update(image).digest('hex'), 'photo');
  const post = (body = image) => fetch(`${base}/receipts`, {
    method: 'POST', headers: { 'Content-Type': 'image/png', Authorization: `Bearer ${token}` }, body,
  });
  let r = await fetch(`${base}/quests/mystery/complete`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ proof: { type: 'labels', labels: [], imageHash: 'a'.repeat(64) } }),
  });
  assert.equal((await r.json()).error, 'auto_quest');
  r = await post();
  assert.equal((await r.json()).error, 'receipt_datetime_missing');
  assert.equal(db.prepare('SELECT COUNT(*) AS n FROM proof_hashes').get().n, 1);
  assert.equal(db.prepare('SELECT COUNT(*) AS n FROM receipts').get().n, 0);
  r = await post(); // exact same bytes after rejection
  assert.equal(r.status, 200);
  const accepted = await r.json();
  assert.equal(accepted.count, 0);
  assert.equal(accepted.progress.sgrReturned, 0);
  assert.deepEqual(accepted.awarded, ['mystery']);
  assert.deepEqual(accepted.progress.questProgress.mystery, [1, 1]);
  assert.equal((await (await post()).json()).error, 'receipt_already_used');
  const before = db.prepare('SELECT COUNT(*) AS n FROM proof_hashes').get().n;
  assert.equal((await (await post(fakeReceiptImage('different photo', 101))).json()).error, 'receipt_already_used');
  assert.equal(db.prepare('SELECT COUNT(*) AS n FROM proof_hashes').get().n, before);
  assert.equal(db.prepare('SELECT COUNT(*) AS n FROM receipts').get().n, 1);
  // Legacy accepted receipt hashes remain protected after the key migration.
  const legacy = fakeReceiptImage('legacy accepted receipt', 102);
  db.prepare('INSERT INTO proof_hashes (hash, user_id, kind) VALUES (?, 1, ?)')
    .run(createHash('sha256').update(legacy).digest('hex'), 'receipt');
  assert.equal((await (await post(legacy)).json()).error, 'receipt_already_used');
});
