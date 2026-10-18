import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const exec = promisify(execFile);
const MAX_IMAGE_BYTES = 12 * 1024 * 1024;
const bundledPython = fileURLToPath(new URL('../.venv-ocr/bin/python', import.meta.url));
let active = false;

export class ReceiptOcrError extends Error {
  constructor(code, status = 422) { super(code); this.code = code; this.status = status; }
}

/** Local CPU OCR only. Images are temporary and never written to the DB or logs. */
export async function readReceiptImage(image) {
  if (!Buffer.isBuffer(image) || !image.length || image.length > MAX_IMAGE_BYTES) throw new ReceiptOcrError('receipt_image_invalid');
  const jpeg = image[0] === 0xff && image[1] === 0xd8;
  const png = image.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]));
  if (!jpeg && !png) throw new ReceiptOcrError('receipt_image_invalid');
  if (active) throw new ReceiptOcrError('receipt_ocr_busy', 503);
  active = true;
  let directory;
  try {
    directory = await mkdtemp(join(tmpdir(), 'gg-receipt-'));
    const input = join(directory, jpeg ? 'input.jpg' : 'input.png');
    await writeFile(input, image, { mode: 0o600 });
    const python = process.env.GG_OCR_PYTHON || (existsSync(bundledPython) ? bundledPython : 'python3');
    let stdout;
    try {
      ({ stdout } = await exec(python, [fileURLToPath(new URL('./receipt-ocr.py', import.meta.url)), input], {
        timeout: 75_000, maxBuffer: 256 * 1024,
        env: { ...process.env, OMP_THREAD_LIMIT: '1', OPENBLAS_NUM_THREADS: '1' },
      }));
    } catch (e) {
      if (e.code === 'ENOENT' || /No module named|cannot open shared object file/.test(e.stderr || '')) {
        throw new ReceiptOcrError('receipt_ocr_unavailable', 503);
      }
      if (/DecompressionBomb|unsupported image format|cannot identify image file/.test(e.stderr || '')) {
        throw new ReceiptOcrError('receipt_image_invalid');
      }
      throw new ReceiptOcrError('receipt_ocr_failed');
    }
    const result = JSON.parse(stdout);
    if (typeof result.text !== 'string' || result.text.length > 20_000) throw new ReceiptOcrError('receipt_image_invalid');
    return result.text;
  } finally {
    try { if (directory) await rm(directory, { recursive: true, force: true }); }
    finally { active = false; }
  }
}
