// Proofs come from on-device ML Kit (OCR / image labels). The server re-parses
// the raw text; receipt quantity may be entered manually if unreadable, per owner policy.
// No server-side AI here.

export const norm = (s) => String(s).toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');

export const isHash = (h) => typeof h === 'string' && /^[a-f0-9]{64}$/.test(h);

const SGR_DEPOSIT_LEI = 0.5;
const MAX_PER_RECEIPT = 200; // a single reverse-vending voucher above this is implausible

/** Quantity extraction only; receipt acceptance is based on its printed date/time. */
export function parseSgrCount(text) {
  const t = norm(text);
  const counts = [
    ...t.matchAll(/(\d{1,3})[ \t]*(?:x[ \t]*)?(?:ambalaje|recipiente|buc|bucati|sticle|doze|pet)\b/g),
    ...t.matchAll(/(?:ambalaje|recipiente|bucati|nr\.?[ \t]*ambalaje)[ \t]*[:=]?[ \t]*(\d{1,3})\b/g),
  ].map((m) => Number(m[1]));
  let count = counts.length ? Math.max(...counts) : 0;
  if (!count) {
    // Fall back to the deposit value on SGR / garantie lines: 0.50 lei per package.
    for (const line of t.split('\n')) {
      if (!/\bsgr\w*\b|garantie/.test(line)) continue;
      for (const m of line.matchAll(/(?<![\d./-])(\d{1,4})[.,](\d{2})(?![\d./-])/g)) {
        const amount = Number(`${m[1]}.${m[2]}`);
        if (Math.round(amount * 100) % 50 === 0) count = Math.max(count, Math.round(amount / SGR_DEPOSIT_LEI));
      }
    }
  }
  return Math.min(count, MAX_PER_RECEIPT);
}

const MONTHS = { jan: 1, ian: 1, feb: 2, mar: 3, apr: 4, may: 5, mai: 5, jun: 6, iun: 6,
  jul: 7, iul: 7, aug: 8, sep: 9, sept: 9, oct: 10, nov: 11, noi: 11, dec: 12 };

/** Printed date and time anywhere on the voucher, including LIDL's bottom line. */
export function parseReceiptTimestamp(text) {
  // OCR may join the footer's clock and date, or read the O in OCT as zero.
  const t = norm(text).replace(/(\d{1,2}:\d{2}:\d{2})(?=\d{1,2}[-./][a-z0]{3,4}[-./]\d{4})/g, '$1 ');
  const dates = [];
  const spans = [];
  const add = (match, year, month, day) => {
    spans.push([match.index, match.index + match[0].length]);
    const date = new Date(0);
    date.setUTCFullYear(year, month - 1, day);
    if (year < 1 || date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return;
    dates.push({ start: match.index, end: match.index + match[0].length,
      day: `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}` });
  };
  for (const m of t.matchAll(/(?<!\d)(\d{1,2})\s*[./-]\s*(\d{1,2})\s*[./-]\s*(\d{4})(?!\d)/g)) add(m, +m[3], +m[2], +m[1]);
  for (const m of t.matchAll(/(?<!\d)(\d{4})\s*-\s*(\d{1,2})\s*-\s*(\d{1,2})(?!\d)/g)) add(m, +m[1], +m[2], +m[3]);
  for (const m of t.matchAll(/(?<!\d)(\d{1,2})[\s./-]+([a-z0]{3,4})[\s./-]+(\d{4})(?!\d)/g)) {
    const month = MONTHS[m[2].replace(/0/g, 'o')];
    if (month) add(m, +m[3], month, +m[1]);
  }
  // Don't accidentally read a numeric date as a clock time.
  const chars = t.split('');
  for (const [a, b] of spans) chars.fill(' ', a, b);
  const times = [...chars.join('').matchAll(/(?<![\d:])(\d{1,2})\s*:\s*(\d{2})(?:\s*:\s*(\d{2}))?(?![\d:])/g)]
    .filter((m) => +m[1] < 24 && +m[2] < 60 && +(m[3] ?? 0) < 60);
  const pairs = dates.flatMap((date) => times.map((time) => ({ date, time,
    gap: time.index >= date.end ? time.index - date.end : date.start >= time.index + time[0].length ? date.start - time.index - time[0].length : 0,
  }))).filter((p) => p.gap <= 160).sort((a, b) => a.gap - b.gap || b.date.start - a.date.start);
  if (!pairs.length) return null;
  const { date, time: m } = pairs[0];
  const time = `${m[1].padStart(2, '0')}:${m[2]}:${m[3] ?? '00'}`;
  const minuteKey = `receipt-time:${date.day}T${time.slice(0, 5)}`;
  return { day: date.day, time, hasSeconds: m[3] != null, minuteKey, key: m[3] != null ? `${minuteKey}:${m[3]}` : minuteKey };
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
