import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFile } from 'node:fs/promises';
import { readReceiptImage } from '../src/receipt-ocr.js';
import jwt from 'jsonwebtoken';

test('OCR rejects empty, non-image and oversized uploads before running tools', async () => {
  for (const image of [Buffer.alloc(0), Buffer.from('not an image'), Buffer.alloc(13 * 1024 * 1024)]) {
    await assert.rejects(readReceiptImage(image), { code: 'receipt_image_invalid', status: 422 });
  }
});

// Run locally with the supplied private fixture; never add the user's photo to Git.
test('actual image upload reads LIDL date/time/quantity and prevents repeat credit', { skip: !process.env.GG_RECEIPT_TEST_IMAGE }, async (t) => {
  process.env.JWT_SECRET = 'ocr-upload-test-secret-at-least-32-characters';
  process.env.KILO_ENABLED = 'false';
  const { createApp } = await import('../src/app.js');
  const { openDb } = await import('../src/db.js');
  const db = openDb(':memory:');
  db.prepare('INSERT INTO users (id, email, pass, name, city, ref_code) VALUES (1, ?, ?, ?, ?, ?)')
    .run('ocr@example.test', 'unused', 'OCR test', 'Iași', 'OCR00001');
  const token = jwt.sign({ uid: 1, typ: 'session' }, process.env.JWT_SECRET);
  const server = createApp(db).listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  t.after(async () => { await new Promise((resolve) => server.close(resolve)); db.close(); });
  const image = await readFile(process.env.GG_RECEIPT_TEST_IMAGE);
  const post = () => fetch(`http://127.0.0.1:${server.address().port}/api/receipts`, {
    method: 'POST', headers: { 'Content-Type': 'image/jpeg', Authorization: `Bearer ${token}` }, body: image,
  });
  let r = await post();
  assert.equal(r.status, 200);
  const body = await r.json();
  assert.equal(body.receiptDay, '2026-10-18');
  assert.equal(body.receiptTime, '08:22:45');
  assert.equal(body.count, 3);
  assert.equal(body.progress.sgrReturned, 3);
  r = await post();
  assert.equal(r.status, 409);
  assert.equal((await r.json()).error, 'receipt_already_used');
  assert.equal(db.prepare('SELECT COUNT(*) AS n FROM receipts').get().n, 1);
});
