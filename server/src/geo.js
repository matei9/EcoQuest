export function haversineKm(a, b) {
  const R = 6371;
  const rad = Math.PI / 180;
  const dLat = (b.lat - a.lat) * rad;
  const dLon = (b.lon - a.lon) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

export const validPoint = (p) =>
  p && Number.isFinite(p.lat) && Number.isFinite(p.lon) && Math.abs(p.lat) <= 90 && Math.abs(p.lon) <= 180;

// Avg / per-segment speed caps (km/h). Car trips break these; GPS jitter may break a few segments.
const LIMITS = { walk: { avg: 9, seg: 20 }, cycle: { avg: 35, seg: 55 } };

/** Validates a recorded GPS track ([{lat, lon, t(ms)}]) for walking or cycling. */
export function analyzeTrack(points, mode) {
  const lim = LIMITS[mode];
  if (!lim) return { ok: false, reason: 'bad_mode' };
  if (!Array.isArray(points) || points.length < 2 || points.length > 20000) return { ok: false, reason: 'bad_points' };
  if (!points.every((p) => validPoint(p) && Number.isFinite(p.t))) return { ok: false, reason: 'bad_points' };
  let km = 0;
  let fast = 0;
  for (let i = 1; i < points.length; i++) {
    const dt = (points[i].t - points[i - 1].t) / 3_600_000;
    if (dt <= 0) return { ok: false, reason: 'bad_timestamps' };
    const d = haversineKm(points[i - 1], points[i]);
    km += d;
    if (d / dt > lim.seg) fast++;
  }
  const hours = (points.at(-1).t - points[0].t) / 3_600_000;
  if (hours < 1 / 60) return { ok: false, reason: 'too_short' };
  if (km < 0.05) return { ok: false, reason: 'too_short' };
  if (km / hours > lim.avg || fast > (points.length - 1) * 0.1) return { ok: false, reason: 'too_fast' };
  return { ok: true, km };
}
