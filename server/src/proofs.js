// Proofs come from on-device ML Kit (OCR / image labels). The server re-parses
// the raw text instead of trusting client-side numbers. No server-side AI here.

export const norm = (s) => String(s).toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');

export const isHash = (h) => typeof h === 'string' && /^[a-f0-9]{64}$/.test(h);

const SGR_DEPOSIT_LEI = 0.5;
const MAX_PER_RECEIPT = 200; // a single reverse-vending voucher above this is implausible

/** Number of SGR packages on a receipt / RVM voucher text, 0 if not an SGR receipt. */
export function parseSgrCount(text) {
  const t = norm(text);
  if (!/\bsgr\b|garantie/.test(t)) return 0;
  const counts = [
    ...t.matchAll(/(\d{1,3})\s*(?:x\s*)?(?:ambalaje|recipiente|buc|bucati|sticle|doze|pet)\b/g),
    ...t.matchAll(/(?:ambalaje|recipiente|bucati|nr\.?\s*ambalaje)\s*[:=]?\s*(\d{1,3})\b/g),
  ].map((m) => Number(m[1]));
  let count = counts.length ? Math.max(...counts) : 0;
  if (!count) {
    // Fall back to the deposit value on SGR / garantie lines: 0.50 lei per package.
    for (const line of t.split('\n')) {
      if (!/\bsgr\b|garantie/.test(line)) continue;
      for (const m of line.matchAll(/(\d{1,4})[.,](\d{2})/g)) {
        count = Math.max(count, Math.round(Number(`${m[1]}.${m[2]}`) / SGR_DEPOSIT_LEI));
      }
    }
  }
  return Math.min(count, MAX_PER_RECEIPT);
}

export const looksLikeReceipt = (text) => {
  const t = norm(text);
  return t.length >= 20 && /\blei\b|\bron\b|total/.test(t);
};

export const looksLikeEnergyBill = (text) => /kwh/.test(norm(text));

export function labelsMatch(labels, wanted) {
  if (!Array.isArray(labels) || labels.length === 0) return false;
  if (!wanted) return true;
  const got = labels.map((l) => norm(l));
  return got.some((g) => wanted.some((w) => g.includes(w)));
}
