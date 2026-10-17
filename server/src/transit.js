import { config } from './config.js';
import { haversineKm } from './geo.js';

// Tranzy OpenData (CTP Iasi). Docs: https://api.tranzy.ai/v1/opendata/docs/
const BASE = 'https://api.tranzy.ai/v1/opendata';
const FRESH_MS = 5 * 60_000; // ignore vehicles that have not reported for 5 min

let agencyId = config.tranzyAgencyId;
const cache = new Map();

export const transitEnabled = () => !!config.tranzyKey;

/** Rough bounding box of Iasi municipality. */
export const inIasi = (lat, lon) => lat > 47.1 && lat < 47.23 && lon > 27.45 && lon < 27.72;

async function call(path, headers) {
  const res = await fetch(BASE + path, {
    headers: { 'X-API-KEY': config.tranzyKey, Accept: 'application/json', ...headers },
    signal: AbortSignal.timeout(10_000),
  });
  if (!res.ok) throw new Error(`tranzy ${path} -> ${res.status}`);
  return res.json();
}

async function ensureAgency() {
  if (agencyId) return agencyId;
  const list = await call('/agency');
  const a = list.find((x) => /ia[sș]i/i.test(x.agency_name));
  if (!a) throw new Error('tranzy: Iasi agency not found');
  agencyId = String(a.agency_id);
  return agencyId;
}

async function cached(path, ttlMs) {
  const hit = cache.get(path);
  if (hit && Date.now() - hit.at < ttlMs) return hit.data;
  const data = await call(path, { 'X-Agency-Id': await ensureAgency() });
  cache.set(path, { at: Date.now(), data });
  return data;
}

const freshVehicles = async () => {
  const now = Date.now();
  return (await cached('/vehicles', 15_000)).filter((v) => {
    const ts = Date.parse(v.timestamp); // keep vehicles whose timestamp format we can't parse
    return v.route_id != null && Number.isFinite(v.latitude) && Number.isFinite(v.longitude) &&
      (Number.isNaN(ts) || now - ts < FRESH_MS);
  });
};

/** Up to 3 routes with a live vehicle closest to the user. */
export async function nearbyRoutes(lat, lon) {
  const [vehicles, routes] = await Promise.all([freshVehicles(), cached('/routes', 3_600_000)]);
  const best = new Map();
  for (const v of vehicles) {
    const m = haversineKm({ lat, lon }, { lat: v.latitude, lon: v.longitude }) * 1000;
    if (!best.has(v.route_id) || m < best.get(v.route_id)) best.set(v.route_id, m);
  }
  const byId = new Map(routes.map((r) => [r.route_id, r]));
  return [...best.entries()]
    .sort((a, b) => a[1] - b[1])
    .slice(0, 3)
    .map(([routeId, m]) => {
      const r = byId.get(routeId) ?? {};
      return { routeId, shortName: r.route_short_name ?? String(routeId), longName: r.route_long_name ?? '', type: r.route_type ?? null, nearestVehicleM: Math.round(m) };
    });
}

/** True if a live vehicle of `routeId` is within the boarding radius. */
export async function vehicleNear(routeId, lat, lon) {
  const vehicles = await freshVehicles();
  return vehicles.some(
    (v) => v.route_id === routeId && haversineKm({ lat, lon }, { lat: v.latitude, lon: v.longitude }) * 1000 <= config.boardRadiusM,
  );
}
