// All periods are calendar days in Romania (Europe/Bucharest), as ISO "YYYY-MM-DD".
const fmt = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Europe/Bucharest',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

export const today = (d = new Date()) => fmt.format(d);

const parse = (day) => {
  const [y, m, d] = day.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
};
const iso = (d) => d.toISOString().slice(0, 10);

export function addDays(day, n) {
  const d = parse(day);
  d.setUTCDate(d.getUTCDate() + n);
  return iso(d);
}

/** First day of the daily / weekly (Monday, ISO) / monthly period containing `day`. */
export function periodStart(kind, day) {
  if (kind === 'daily') return day;
  if (kind === 'monthly') return `${day.slice(0, 8)}01`;
  const d = parse(day);
  return addDays(day, -((d.getUTCDay() + 6) % 7));
}

export const periodKey = (kind, day) => `${kind[0]}:${periodStart(kind, day)}`;
