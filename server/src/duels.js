import { tx } from './db.js';

const WEEK = 7 * 24 * 60 * 60 * 1000;
const score = (db, uid, start, end) => uid == null ? 0 : db.prepare(
  'SELECT COALESCE(SUM(points), 0) AS n FROM earned_points WHERE user_id = ? AND earned_at >= ? AND earned_at < ?',
).get(uid, start, end).n;

export function settleDuels(db, uid, now = Date.now()) {
  const rows = db.prepare("SELECT * FROM duels WHERE status = 'active' AND (player1 = ? OR player2 = ?)").all(uid, uid);
  for (const d of rows) {
    if (now < d.ends_at && d.player1 != null && d.player2 != null) continue;
    const a = score(db, d.player1, d.starts_at, d.ends_at);
    const b = score(db, d.player2, d.starts_at, d.ends_at);
    const winner = d.player1 == null ? d.player2 : d.player2 == null ? d.player1 : a > b ? d.player1 : b > a ? d.player2 : null;
    db.prepare("UPDATE duels SET status = 'finished', score1 = ?, score2 = ?, winner_id = ? WHERE id = ? AND status = 'active'")
      .run(a, b, winner, d.id);
  }
}

export function duelStats(db, uid, now = Date.now()) {
  settleDuels(db, uid, now);
  const s = db.prepare(`SELECT COUNT(*) AS played,
    COALESCE(SUM(winner_id = ?), 0) AS wins, COALESCE(SUM(winner_id IS NULL), 0) AS draws
    FROM duels WHERE mode = 'ranked' AND status = 'finished' AND (player1 = ? OR player2 = ?)`).get(uid, uid, uid);
  return { ...s, points: s.wins * 3 + s.draws };
}

/** A queue entry is consent to random matching; friendship invitations need explicit acceptance. */
export function duelRoutes(r, db, auth, fail, limit, now = Date.now) {
  const friend = (a, b) => db.prepare('SELECT 1 FROM friendships WHERE low_id = min(?, ?) AND high_id = max(?, ?) AND accepted = 1').get(a, b, a, b);
  const busy = (uid) => {
    settleDuels(db, uid, now());
    return !!db.prepare("SELECT 1 FROM duels WHERE status = 'active' AND (player1 = ? OR player2 = ?)").get(uid, uid);
  };
  const expire = () => {
    db.prepare('DELETE FROM duel_queue WHERE queued_at < ?').run(now() - 30 * 60_000);
    db.prepare("UPDATE duels SET status = 'cancelled' WHERE status = 'pending' AND created_at < ?").run(now() - WEEK);
  };
  const start = (id, a, b) => {
    const time = now();
    db.prepare("UPDATE duels SET status = 'active', starts_at = ?, ends_at = ? WHERE id = ?").run(time, time + WEEK, id);
    db.prepare('DELETE FROM duel_queue WHERE user_id IN (?, ?)').run(a, b);
    db.prepare("UPDATE duels SET status = 'cancelled' WHERE status = 'pending' AND (player1 IN (?, ?) OR player2 IN (?, ?)) AND id != ?")
      .run(a, b, a, b, id);
  };
  const get = (id, uid) => db.prepare('SELECT * FROM duels WHERE id = ? AND (player1 = ? OR player2 = ?)')
    .get(Number(id) || 0, uid, uid) ?? fail(404, 'unknown_duel');

  r.get('/duels', auth, (req, res) => {
    expire();
    const stats = duelStats(db, req.uid, now());
    const rows = db.prepare(`SELECT d.*, a.name AS name1, b.name AS name2 FROM duels d
      LEFT JOIN users a ON a.id = d.player1 LEFT JOIN users b ON b.id = d.player2
      WHERE player1 = ? OR player2 = ?
      ORDER BY CASE d.status WHEN 'active' THEN 0 WHEN 'pending' THEN 1 WHEN 'finished' THEN 2 ELSE 3 END, d.id DESC LIMIT 30`).all(req.uid, req.uid);
    res.json({ stats, queued: !!db.prepare('SELECT 1 FROM duel_queue WHERE user_id = ?').get(req.uid), duels: rows.map((d) => {
      const first = d.player1 === req.uid;
      const a = d.status === 'active' ? score(db, d.player1, d.starts_at, Math.min(now(), d.ends_at)) : d.score1;
      const b = d.status === 'active' ? score(db, d.player2, d.starts_at, Math.min(now(), d.ends_at)) : d.score2;
      return { id: d.id, mode: d.mode, status: d.status, incoming: !first,
        opponent: first ? d.name2 : d.name1, myScore: first ? a : b, opponentScore: first ? b : a,
        endsAt: d.ends_at, outcome: d.status !== 'finished' ? null : d.winner_id == null ? 'draw' : d.winner_id === req.uid ? 'win' : 'loss' };
    }) });
  });

  r.post('/duels/friends', auth, limit, (req, res) => {
    const other = req.body?.userId;
    if (!Number.isSafeInteger(other) || !friend(req.uid, other)) fail(403, 'not_friends');
    const id = tx(db, () => {
      expire();
      if (busy(req.uid) || busy(other)) fail(409, 'duel_busy');
      if (db.prepare("SELECT 1 FROM duels WHERE status = 'pending' AND ((player1 = ? AND player2 = ?) OR (player1 = ? AND player2 = ?))")
        .get(req.uid, other, other, req.uid)) fail(409, 'duel_invite_exists');
      return Number(db.prepare("INSERT INTO duels (player1, player2, mode, status, created_at) VALUES (?, ?, 'friendly', 'pending', ?)")
        .run(req.uid, other, now()).lastInsertRowid);
    });
    res.status(201).json({ id });
  });

  r.post('/duels/:id/accept', auth, (req, res) => {
    tx(db, () => {
      expire();
      const d = get(req.params.id, req.uid);
      if (d.status !== 'pending' || d.player2 !== req.uid) fail(409, 'duel_invite_expired');
      if (!friend(d.player1, d.player2)) fail(403, 'not_friends');
      if (busy(d.player1) || busy(d.player2)) fail(409, 'duel_busy');
      start(d.id, d.player1, d.player2);
    });
    res.json({ ok: true });
  });

  r.delete('/duels/queue', auth, (req, res) => {
    db.prepare('DELETE FROM duel_queue WHERE user_id = ?').run(req.uid);
    res.json({ ok: true });
  });

  r.delete('/duels/:id', auth, (req, res) => {
    const d = get(req.params.id, req.uid);
    if (d.status !== 'pending') fail(409, 'duel_invite_expired');
    db.prepare("UPDATE duels SET status = 'cancelled' WHERE id = ?").run(d.id);
    res.json({ ok: true });
  });

  r.post('/duels/queue', auth, limit, (req, res) => {
    const matched = tx(db, () => {
      expire();
      if (busy(req.uid)) fail(409, 'duel_busy');
      const candidates = db.prepare('SELECT user_id FROM duel_queue WHERE user_id != ? ORDER BY random()').all(req.uid);
      const other = candidates.find((p) => !friend(req.uid, p.user_id) && !busy(p.user_id));
      if (!other) {
        db.prepare('INSERT OR IGNORE INTO duel_queue (user_id, queued_at) VALUES (?, ?)').run(req.uid, now());
        return false;
      }
      const id = db.prepare("INSERT INTO duels (player1, player2, mode, status, created_at) VALUES (?, ?, 'ranked', 'pending', ?)")
        .run(other.user_id, req.uid, now()).lastInsertRowid;
      start(Number(id), other.user_id, req.uid);
      return true;
    });
    res.json({ matched });
  });

}
