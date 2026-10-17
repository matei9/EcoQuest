import { randomBytes, randomUUID, scryptSync, timingSafeEqual } from 'node:crypto';
import express from 'express';
import rateLimit from 'express-rate-limit';
import jwt from 'jsonwebtoken';
import { aiEnabled, chat, streamChat } from './ai.js';
import { CITIES, SCHOOLS, leagueRange, questById, scoreQuiz, vouchers } from './catalog.js';
import { config } from './config.js';
import { tx } from './db.js';
import { analyzeTrack, haversineKm, validPoint } from './geo.js';
import { factById, facts, selectTrivia, triviaReward } from './facts.js';
import { award, evaluate, isDone, me } from './progress.js';
import { isHash, labelsMatch, looksLikeEnergyBill, looksLikeReceipt, parseSgrCount } from './proofs.js';
import { periodStart, today } from './time.js';
import { inIasi, nearbyRoutes, transitEnabled, vehicleNear } from './transit.js';

class ApiError extends Error {
  constructor(status, code) {
    super(code);
    this.status = status;
    this.code = code;
  }
}
const fail = (status, code) => { throw new ApiError(status, code); };
const str = (v, min, max) => typeof v === 'string' && v.trim().length >= min && v.trim().length <= max;

function hashPassword(pw) {
  const salt = randomBytes(16);
  return `${salt.toString('hex')}:${scryptSync(pw, salt, 64).toString('hex')}`;
}
function checkPassword(pw, stored) {
  const [salt, hash] = stored.split(':');
  const got = scryptSync(pw, Buffer.from(salt, 'hex'), 64);
  return timingSafeEqual(got, Buffer.from(hash, 'hex'));
}

const signSession = (uid) => jwt.sign({ uid, typ: 'session' }, config.jwtSecret, { expiresIn: '30d' });

export function createApp(db) {
  const app = express();
  app.set('trust proxy', 1); // behind nginx
  app.use(express.json({ limit: '2mb' }));
  app.use(rateLimit({ windowMs: 60_000, limit: 300, standardHeaders: 'draft-7', legacyHeaders: false }));
  const authLimit = rateLimit({ windowMs: 15 * 60_000, limit: 20, standardHeaders: 'draft-7', legacyHeaders: false });

  const auth = (req, _res, next) => {
    const token = (req.get('authorization') || '').replace(/^Bearer /, '');
    try {
      const p = jwt.verify(token, config.jwtSecret);
      if (p.typ !== 'session') throw new Error('typ');
      if (!db.prepare('SELECT 1 FROM users WHERE id = ?').get(p.uid)) throw new Error('gone');
      req.uid = p.uid;
      next();
    } catch {
      next(new ApiError(401, 'unauthorized'));
    }
  };
  const userLimit = (limit, windowMs) =>
    rateLimit({ windowMs, limit, keyGenerator: (req) => `u${req.uid}`, standardHeaders: 'draft-7', legacyHeaders: false });

  const r = express.Router();
  app.use('/api', r);

  r.get('/health', (_req, res) => res.json({ ok: true }));
  r.get('/config', (_req, res) =>
    res.json({ demoMode: config.demoMode, aiEnabled: aiEnabled(), transitEnabled: transitEnabled(), cities: CITIES, schools: SCHOOLS }));

  // ---------- auth ----------
  r.post('/auth/signup', authLimit, (req, res) => {
    const { email, password, name, city, school, refCode } = req.body ?? {};
    if (!str(email, 3, 120) || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) fail(400, 'invalid_email');
    if (typeof password !== 'string' || password.length < 8 || password.length > 200) fail(400, 'weak_password');
    if (!str(name, 2, 40)) fail(400, 'invalid_name');
    if (!CITIES.includes(city)) fail(400, 'invalid_city');
    if (school != null && !SCHOOLS.includes(school)) fail(400, 'invalid_school');
    const referrer = refCode ? db.prepare('SELECT id FROM users WHERE ref_code = ?').get(String(refCode).toUpperCase()) : null;
    if (refCode && !referrer) fail(400, 'invalid_ref_code');
    if (db.prepare('SELECT 1 FROM users WHERE email = ?').get(email.trim())) fail(409, 'email_taken');
    const { lastInsertRowid } = db.prepare(
      'INSERT INTO users (email, pass, name, city, school, ref_code, referred_by) VALUES (?, ?, ?, ?, ?, ?, ?)',
    ).run(email.trim(), hashPassword(password), name.trim(), city, school ?? null, randomBytes(4).toString('hex').toUpperCase(), referrer?.id ?? null);
    const uid = Number(lastInsertRowid);
    res.status(201).json({ token: signSession(uid), ...me(db, uid) });
  });

  r.post('/auth/login', authLimit, (req, res) => {
    const { email, password } = req.body ?? {};
    const u = typeof email === 'string' ? db.prepare('SELECT id, pass FROM users WHERE email = ?').get(email.trim()) : null;
    if (!u || typeof password !== 'string' || !checkPassword(password, u.pass)) fail(401, 'bad_credentials');
    res.json({ token: signSession(u.id), ...me(db, u.id) });
  });

  r.get('/me', auth, (req, res) => res.json(me(db, req.uid)));

  r.delete('/me', auth, (req, res) => {
    db.prepare('UPDATE users SET referred_by = NULL WHERE referred_by = ?').run(req.uid);
    db.prepare('DELETE FROM users WHERE id = ?').run(req.uid);
    res.status(204).end();
  });

  // ---------- quiz (self-declared EP, never CO2) ----------
  r.post('/quiz', auth, (req, res) => {
    const cats = scoreQuiz(req.body?.answers);
    if (!cats) fail(400, 'invalid_answers');
    const ep = Math.min(cats.reduce((a, b) => a + b, 0) * 25, 2500);
    db.prepare('UPDATE users SET quiz_done = 1, cat_scores = ?, initial_ep = ? WHERE id = ?').run(cats.join(','), ep, req.uid);
    res.json(me(db, req.uid));
  });

  const useProof = (hash, uid, kind) => {
    const r = db.prepare('INSERT OR IGNORE INTO proof_hashes (hash, user_id, kind) VALUES (?, ?, ?)').run(hash, uid, kind);
    if (r.changes === 0) fail(409, 'proof_already_used');
  };

  // ---------- direct quest verification ----------
  r.post('/quests/:id/complete', auth, (req, res) => {
    const q = questById.get(req.params.id) ?? fail(404, 'unknown_quest');
    const proof = req.body?.proof ?? {};
    const day = today();
    const out = tx(db, () => {
      if (isDone(db, req.uid, q, day)) fail(409, 'already_done');
      if (q.requires && !isDone(db, req.uid, questById.get(q.requires), day)) fail(409, 'locked');
      if (proof.type === 'demo') {
        if (!config.demoMode) fail(403, 'demo_disabled');
      } else if (q.verification === 'metric') {
        fail(400, 'auto_quest');
      } else if (q.verification === 'confirm') {
        if (proof.type !== 'confirm') fail(400, 'bad_proof');
      } else if (q.verification === 'timer') {
        if (proof.type !== 'timer' || !(proof.seconds >= 180)) fail(400, 'bad_proof');
      } else if (q.verification === 'photo') {
        const hashes = q.twoImages ? proof.imageHashes : [proof.imageHash];
        if (proof.type !== 'labels' || !Array.isArray(hashes) || hashes.length !== (q.twoImages ? 2 : 1) || !hashes.every(isHash)) fail(400, 'bad_proof');
        if (new Set(hashes).size !== hashes.length) fail(400, 'bad_proof');
        if (!labelsMatch(proof.labels, q.labels)) fail(422, 'labels_mismatch');
        hashes.forEach((h) => useProof(h, req.uid, 'photo'));
      } else if (q.verification === 'ocr' || q.verification === 'bill') {
        if (proof.type !== 'ocr' || typeof proof.text !== 'string' || !isHash(proof.imageHash)) fail(400, 'bad_proof');
        const ok = q.verification === 'bill' ? looksLikeEnergyBill(proof.text) : looksLikeReceipt(proof.text);
        if (!ok) fail(422, 'ocr_mismatch');
        useProof(proof.imageHash, req.uid, 'ocr');
      } else {
        fail(400, 'bad_proof');
      }
      award(db, req.uid, q, day);
      return [q.id, ...evaluate(db, req.uid, day)];
    });
    res.json({ awarded: out, ...me(db, req.uid) });
  });

  // ---------- SGR receipts (on-device OCR text) ----------
  r.post('/receipts', auth, (req, res) => {
    const { text, imageHash } = req.body ?? {};
    if (typeof text !== 'string' || text.length > 20_000 || !isHash(imageHash)) fail(400, 'bad_proof');
    const count = parseSgrCount(text);
    if (count === 0) fail(422, 'not_sgr_receipt');
    const day = today();
    const awarded = tx(db, () => {
      useProof(imageHash, req.uid, 'receipt');
      db.prepare('INSERT INTO receipts (user_id, count, day) VALUES (?, ?, ?)').run(req.uid, count, day);
      db.prepare('UPDATE users SET sgr = sgr + ? WHERE id = ?').run(count, req.uid);
      return evaluate(db, req.uid, day);
    });
    res.json({ count, awarded, ...me(db, req.uid) });
  });

  // ---------- trips: walk/cycle GPS tracks, transit via CTP live vehicles ----------
  r.post('/transit/nearby', auth, async (req, res) => {
    const { lat, lon } = req.body ?? {};
    if (!validPoint({ lat, lon })) fail(400, 'bad_location');
    if (!inIasi(lat, lon)) return res.json({ inIasi: false, routes: [] });
    if (!transitEnabled()) fail(503, 'transit_unavailable');
    res.json({ inIasi: true, routes: await nearbyRoutes(lat, lon) });
  });

  r.post('/transit/board', auth, async (req, res) => {
    const { routeId, lat, lon } = req.body ?? {};
    if (!validPoint({ lat, lon }) || routeId == null) fail(400, 'bad_request');
    if (!inIasi(lat, lon)) fail(422, 'outside_iasi');
    if (!transitEnabled()) fail(503, 'transit_unavailable');
    if (!(await vehicleNear(routeId, lat, lon))) fail(422, 'no_vehicle_nearby');
    const boardingToken = jwt.sign({ typ: 'board', uid: req.uid, routeId, lat, lon, jti: randomUUID() }, config.jwtSecret, { expiresIn: '3h' });
    res.json({ boardingToken });
  });

  r.post('/trips', auth, async (req, res) => {
    const { mode } = req.body ?? {};
    let km;
    if (mode === 'walk' || mode === 'cycle') {
      const a = analyzeTrack(req.body.points, mode);
      if (!a.ok) fail(422, a.reason);
      km = a.km;
    } else if (mode === 'transit') {
      let b;
      try {
        b = jwt.verify(req.body.boardingToken, config.jwtSecret);
      } catch {
        fail(400, 'bad_boarding_token');
      }
      const end = req.body.end;
      if (b.typ !== 'board' || b.uid !== req.uid || !validPoint(end)) fail(400, 'bad_boarding_token');
      km = haversineKm(b, end);
      const hours = (Date.now() / 1000 - b.iat) / 3600;
      if (km < 0.3) fail(422, 'too_short');
      if (km / hours > 70) fail(422, 'too_fast');
      // Alighting must also happen next to a vehicle of the same route.
      if (!(await vehicleNear(b.routeId, end.lat, end.lon))) fail(422, 'no_vehicle_nearby');
      useProof(`board:${b.jti}`, req.uid, 'board');
    } else {
      fail(400, 'bad_mode');
    }
    const day = today();
    const awarded = tx(db, () => {
      db.prepare('INSERT INTO trips (user_id, mode, km, day) VALUES (?, ?, ?, ?)').run(req.uid, mode, km, day);
      return evaluate(db, req.uid, day);
    });
    res.json({ km, awarded, ...me(db, req.uid) });
  });

  // ---------- QR check-in at community events ----------
  r.post('/events/checkin', auth, (req, res) => {
    const code = String(req.body?.code ?? '').trim();
    if (!db.prepare('SELECT 1 FROM event_codes WHERE code = ? AND active = 1').get(code)) fail(404, 'unknown_event');
    const day = today();
    const awarded = tx(db, () => {
      const r = db.prepare('INSERT OR IGNORE INTO checkins (user_id, code, day) VALUES (?, ?, ?)').run(req.uid, code, day);
      if (r.changes === 0) fail(409, 'already_checked_in');
      return evaluate(db, req.uid, day);
    });
    res.json({ awarded, ...me(db, req.uid) });
  });

  // ---------- leaderboards ----------
  const TOTAL = '(initial_ep + verified_ep)';

  r.get('/leaderboard/individual', auth, (req, res) => {
    const col = req.query.metric === 'verified' ? 'verified_ep' : TOTAL;
    const u = db.prepare(`SELECT ${TOTAL} AS total FROM users WHERE id = ?`).get(req.uid);
    const lg = leagueRange(u.total);
    const rows = db.prepare(
      `SELECT id, name, city, ${TOTAL} AS total, verified_ep AS verified FROM users
       WHERE ${TOTAL} >= ? AND ${TOTAL} < ? ORDER BY ${col} DESC, id LIMIT 50`,
    ).all(lg.min, lg.max);
    res.json({ league: lg.name, players: rows.map((p) => ({ ...p, isYou: p.id === req.uid })) });
  });

  r.get('/leaderboard/schools', auth, (_req, res) => {
    res.json(db.prepare(
      `SELECT school AS name, COUNT(*) AS users, SUM(${TOTAL}) AS totalEP, CAST(AVG(${TOTAL}) AS INTEGER) AS avgEP
       FROM users WHERE school IS NOT NULL GROUP BY school ORDER BY totalEP DESC`,
    ).all());
  });

  // Normalized by active participants (verified EP > 0) so small cities compete fairly.
  r.get('/leaderboard/cities', auth, (_req, res) => {
    res.json(db.prepare(
      `SELECT city AS name, COUNT(*) AS users,
        SUM(verified_ep > 0) AS active,
        CAST(COALESCE(SUM(CASE WHEN verified_ep > 0 THEN ${TOTAL} END) * 1.0 / NULLIF(SUM(verified_ep > 0), 0), 0) AS INTEGER) AS avgEP,
        COALESCE(SUM(CASE WHEN verified_ep > 0 THEN co2 END) / NULLIF(SUM(verified_ep > 0), 0), 0) AS avgCO2
       FROM users WHERE city != 'Altul' GROUP BY city ORDER BY avgEP DESC`,
    ).all());
  });

  // ---------- teams ----------
  const TEAM_GOAL = 500;
  r.get('/teams', auth, (_req, res) => {
    const from = periodStart('weekly', today());
    res.json({
      goal: TEAM_GOAL,
      teams: db.prepare(
        `SELECT t.id, t.name, t.city, COUNT(u.id) AS members,
          (SELECT COALESCE(SUM(r.count), 0) FROM receipts r JOIN users m ON m.id = r.user_id WHERE m.team_id = t.id AND r.day >= ?) AS sgrThisWeek
         FROM teams t LEFT JOIN users u ON u.team_id = t.id GROUP BY t.id ORDER BY sgrThisWeek DESC LIMIT 100`,
      ).all(from),
    });
  });

  r.post('/teams', auth, (req, res) => {
    const name = req.body?.name;
    if (!str(name, 3, 30)) fail(400, 'invalid_name');
    const u = db.prepare('SELECT city FROM users WHERE id = ?').get(req.uid);
    if (db.prepare('SELECT 1 FROM teams WHERE name = ?').get(name.trim())) fail(409, 'team_exists');
    tx(db, () => {
      const { lastInsertRowid } = db.prepare('INSERT INTO teams (name, city, created_by) VALUES (?, ?, ?)').run(name.trim(), u.city, req.uid);
      db.prepare('UPDATE users SET team_id = ? WHERE id = ?').run(lastInsertRowid, req.uid);
    });
    res.status(201).json(me(db, req.uid));
  });

  r.post('/teams/:id/join', auth, (req, res) => {
    const id = Number(req.params.id);
    if (!db.prepare('SELECT 1 FROM teams WHERE id = ?').get(id)) fail(404, 'unknown_team');
    db.prepare('UPDATE users SET team_id = ? WHERE id = ?').run(id, req.uid);
    res.json(me(db, req.uid));
  });

  r.post('/teams/leave', auth, (req, res) => {
    db.prepare('UPDATE users SET team_id = NULL WHERE id = ?').run(req.uid);
    res.json(me(db, req.uid));
  });

  // ---------- community: boss battle + polar bears ----------
  r.get('/community', auth, (_req, res) => {
    const t = db.prepare('SELECT COALESCE(SUM(co2), 0) AS co2, COUNT(*) AS users FROM users').get();
    const km = db.prepare('SELECT COALESCE(SUM(km), 0) AS km FROM trips WHERE day >= ?').get(config.bossStart).km;
    res.json({
      users: t.users,
      co2Kg: t.co2,
      polarBears: t.co2 / 100,
      iceM2: t.co2 * 0.003,
      boss: { name: 'Smogzilla', goalKm: 10_000, km },
    });
  });

  // ---------- EcoCoins marketplace ----------
  r.get('/vouchers', auth, (req, res) =>
    res.json({
      vouchers,
      redeemed: db.prepare('SELECT voucher_id AS voucherId, code, created_at AS at FROM redemptions WHERE user_id = ? ORDER BY id DESC').all(req.uid),
    }));

  r.post('/vouchers/:id/redeem', auth, (req, res) => {
    const v = vouchers.find((x) => x.id === req.params.id) ?? fail(404, 'unknown_voucher');
    const code = `GG-${randomBytes(3).toString('hex').toUpperCase()}`;
    tx(db, () => {
      const r = db.prepare('UPDATE users SET coins = coins - ? WHERE id = ? AND coins >= ?').run(v.cost, req.uid, v.cost);
      if (r.changes === 0) fail(402, 'not_enough_coins');
      db.prepare('INSERT INTO redemptions (user_id, voucher_id, code) VALUES (?, ?, ?)').run(req.uid, v.id, code);
    });
    res.json({ code, ...me(db, req.uid) });
  });

  // ---------- fun facts + daily trivia ----------
  const seenFacts = (uid) => new Set(db.prepare('SELECT fact_id FROM seen_facts WHERE user_id = ?').all(uid).map((r) => r.fact_id));

  /** A fact the user hasn't seen yet (random), or any fact once all were seen; marks it as seen. */
  r.get('/facts/next', auth, (req, res) => {
    const seen = seenFacts(req.uid);
    const pool = facts.filter((f) => !seen.has(f.id));
    const f = (pool.length ? pool : facts)[Math.floor(Math.random() * (pool.length || facts.length))];
    db.prepare('INSERT OR IGNORE INTO seen_facts (user_id, fact_id) VALUES (?, ?)').run(req.uid, f.id);
    res.json({ id: f.id, text: f.fact, seen: seen.size + (seen.has(f.id) ? 0 : 1), total: facts.length });
  });

  r.get('/trivia', auth, (req, res) => {
    const day = today();
    const done = db.prepare('SELECT score, ep FROM trivia_attempts WHERE user_id = ? AND day = ?').get(req.uid, day);
    if (done) return res.json({ done: true, score: done.score, ep: done.ep });
    const picked = selectTrivia(seenFacts(req.uid));
    // Answers stay on the server; the signed token pins which questions this attempt must answer.
    const token = jwt.sign({ typ: 'trivia', uid: req.uid, day, ids: picked.map((f) => f.id) }, config.jwtSecret, { expiresIn: '2h' });
    res.json({
      done: false,
      token,
      questions: picked.map((f) => ({ id: f.id, difficulty: f.difficulty, question: f.question, options: f.options })),
    });
  });

  r.post('/trivia/submit', auth, (req, res) => {
    let t;
    try {
      t = jwt.verify(req.body?.token, config.jwtSecret);
    } catch {
      fail(400, 'bad_trivia_token');
    }
    const day = today();
    if (t.typ !== 'trivia' || t.uid !== req.uid || t.day !== day) fail(400, 'bad_trivia_token');
    const answers = req.body?.answers;
    if (!Array.isArray(answers) || answers.length !== t.ids.length) fail(400, 'bad_answers');
    const results = t.ids.map((id, i) => {
      const f = factById.get(id);
      const chosen = Number.isInteger(answers[i]) ? answers[i] : -1;
      return { id, question: f.question, options: f.options, chosen, correct: f.correct, ok: chosen === f.correct, fact: f.fact };
    });
    const score = results.filter((x) => x.ok).length;
    const reward = triviaReward(score);
    tx(db, () => {
      const ins = db.prepare('INSERT OR IGNORE INTO trivia_attempts (user_id, day, score, ep) VALUES (?, ?, ?, ?)').run(req.uid, day, score, reward.ep);
      if (ins.changes === 0) fail(409, 'trivia_done_today');
      // Trivia EP counts as earned in-app (verified_ep); it never adds CO2.
      db.prepare('UPDATE users SET verified_ep = verified_ep + ?, coins = coins + ? WHERE id = ?').run(reward.ep, reward.coins, req.uid);
    });
    res.json({ score, total: results.length, ...reward, results, ...me(db, req.uid) });
  });

  // ---------- Guardian AI (Kilo gateway) ----------
  const chatInput = (req) => {
    if (!aiEnabled()) fail(503, 'ai_unavailable');
    const msgs = req.body?.messages;
    if (!Array.isArray(msgs) || msgs.length === 0 || msgs.length > 10) fail(400, 'bad_messages');
    const clean = msgs.map((m) => {
      if (!['user', 'assistant'].includes(m?.role) || !str(m?.content, 1, 1000)) fail(400, 'bad_messages');
      return { role: m.role, content: m.content.trim() };
    });
    return { progress: me(db, req.uid).progress, lang: req.body.lang === 'en' ? 'en' : 'ro', clean };
  };
  const aiLimit = userLimit(20, 10 * 60_000);

  r.post('/ai/chat', auth, aiLimit, async (req, res) => {
    const { progress, lang, clean } = chatInput(req);
    try {
      res.json({ reply: await chat(progress, lang, clean) });
    } catch (e) {
      console.error(e.message);
      // 503, not 502: Cloudflare replaces origin 502 bodies with its own HTML page.
      fail(503, 'ai_failed');
    }
  });

  // Server-Sent Events: {type:'thinking'} / {type:'delta', text} / {type:'done'} / {type:'error', error}.
  r.post('/ai/chat/stream', auth, aiLimit, async (req, res) => {
    const { progress, lang, clean } = chatInput(req);
    res.writeHead(200, {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      'X-Accel-Buffering': 'no',
    });
    const send = (o) => res.write(`data: ${JSON.stringify(o)}\n\n`);
    const ac = new AbortController();
    res.on('close', () => ac.abort()); // client went away: stop paying for tokens
    const timer = setTimeout(() => ac.abort(), 120_000);
    let text = false;
    let thinking = false;
    try {
      for await (const ev of streamChat(progress, lang, clean, ac.signal)) {
        if (ev.type === 'delta') {
          text = true;
          send(ev);
        } else if (!thinking) {
          thinking = true;
          send(ev);
        }
      }
      send(text ? { type: 'done' } : { type: 'error', error: 'ai_failed' });
    } catch (e) {
      if (!res.writableEnded) {
        console.error(e.message);
        send({ type: 'error', error: 'ai_failed' });
      }
    } finally {
      clearTimeout(timer);
      res.end();
    }
  });

  app.use((_req, _res, next) => next(new ApiError(404, 'not_found')));
  app.use((err, _req, res, _next) => {
    if (err instanceof ApiError) return res.status(err.status).json({ error: err.code });
    if (err.type === 'entity.parse.failed') return res.status(400).json({ error: 'bad_json' });
    if (err.type === 'entity.too.large') return res.status(413).json({ error: 'too_large' });
    console.error(err);
    res.status(500).json({ error: 'internal' });
  });
  return app;
}
