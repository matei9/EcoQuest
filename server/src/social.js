import { me } from './progress.js';
import { tx } from './db.js';

/** Only explicit acceptance creates a friendship or changes team membership. */
export function socialRoutes(r, db, auth, fail, limit) {
  const pair = (a, b) => [Math.min(a, b), Math.max(a, b)];
  const friendship = (a, b) => db.prepare('SELECT * FROM friendships WHERE low_id = ? AND high_id = ?').get(...pair(a, b));
  const id = (value) => {
    const n = Number(value);
    if (!Number.isSafeInteger(n) || n <= 0) fail(400, 'invalid_user');
    return n;
  };

  r.get('/friends', auth, (req, res) => {
    const relations = db.prepare(`SELECT u.id, u.name, u.city, u.team_id AS teamId, f.accepted, f.requested_by AS requestedBy
      FROM friendships f JOIN users u ON u.id = CASE WHEN f.low_id = ? THEN f.high_id ELSE f.low_id END
      WHERE f.low_id = ? OR f.high_id = ? ORDER BY u.name COLLATE NOCASE`).all(req.uid, req.uid, req.uid);
    const invitations = db.prepare(`SELECT i.id, t.name AS teamName, u.name AS senderName
      FROM team_invitations i JOIN teams t ON t.id = i.team_id JOIN users u ON u.id = i.sender_id
      WHERE i.recipient_id = ? AND u.team_id = i.team_id
      AND EXISTS (SELECT 1 FROM friendships f WHERE f.low_id = min(i.sender_id, i.recipient_id)
        AND f.high_id = max(i.sender_id, i.recipient_id) AND f.accepted = 1)
      ORDER BY i.id DESC`).all(req.uid);
    // Explicit projection avoids disclosing email addresses or other users' friend codes.
    const person = ({ id, name, city, teamId }) => ({ id, name, city, teamId });
    res.json({
      friends: relations.filter((f) => f.accepted).map(person),
      incoming: relations.filter((f) => !f.accepted && f.requestedBy !== req.uid).map(person),
      outgoing: relations.filter((f) => !f.accepted && f.requestedBy === req.uid).map(person),
      invitations,
      sentInvitations: db.prepare(`SELECT i.recipient_id AS userId FROM team_invitations i
        JOIN users u ON u.id = ? WHERE i.team_id = u.team_id`).all(req.uid),
    });
  });

  r.post('/friends', auth, limit, (req, res) => {
    const code = req.body?.code;
    if (typeof code !== 'string' || !/^[a-f0-9]{8}$/i.test(code.trim())) fail(400, 'invalid_ref_code');
    const other = db.prepare('SELECT id FROM users WHERE ref_code = ?').get(code.trim().toUpperCase());
    if (!other) fail(404, 'invalid_ref_code');
    if (other.id === req.uid) fail(400, 'friend_self');
    if (friendship(req.uid, other.id)) fail(409, 'friend_exists');
    db.prepare('INSERT INTO friendships (low_id, high_id, requested_by) VALUES (?, ?, ?)').run(...pair(req.uid, other.id), req.uid);
    res.status(201).json({ ok: true });
  });

  r.post('/friends/:id/accept', auth, (req, res) => {
    const other = id(req.params.id);
    const f = friendship(req.uid, other);
    if (!f || f.accepted || f.requested_by === req.uid) fail(404, 'unknown_request');
    db.prepare('UPDATE friendships SET accepted = 1 WHERE low_id = ? AND high_id = ?').run(...pair(req.uid, other));
    res.json({ ok: true });
  });

  r.delete('/friends/:id', auth, (req, res) => {
    const other = id(req.params.id);
    tx(db, () => {
      db.prepare('DELETE FROM friendships WHERE low_id = ? AND high_id = ?').run(...pair(req.uid, other));
      db.prepare('DELETE FROM team_invitations WHERE (sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?)')
        .run(req.uid, other, other, req.uid);
    });
    res.json({ ok: true });
  });

  r.post('/teams/invitations', auth, limit, (req, res) => {
    const other = id(req.body?.userId);
    const sender = db.prepare('SELECT team_id FROM users WHERE id = ?').get(req.uid);
    if (!sender.team_id) fail(409, 'no_team');
    if (!friendship(req.uid, other)?.accepted) fail(403, 'not_friends');
    const target = db.prepare('SELECT team_id FROM users WHERE id = ?').get(other);
    if (target.team_id) fail(409, 'team_already_joined');
    const result = db.prepare('INSERT OR IGNORE INTO team_invitations (team_id, sender_id, recipient_id) VALUES (?, ?, ?)')
      .run(sender.team_id, req.uid, other);
    if (!result.changes) fail(409, 'invite_exists');
    res.status(201).json({ ok: true });
  });

  r.post('/teams/invitations/:id/accept', auth, (req, res) => {
    const invitation = db.prepare('SELECT * FROM team_invitations WHERE id = ? AND recipient_id = ?').get(id(req.params.id), req.uid);
    if (!invitation) fail(404, 'unknown_request');
    const sender = db.prepare('SELECT team_id FROM users WHERE id = ?').get(invitation.sender_id);
    if (sender.team_id !== invitation.team_id || !friendship(req.uid, invitation.sender_id)?.accepted) fail(409, 'invite_expired');
    if (db.prepare('SELECT team_id FROM users WHERE id = ?').get(req.uid).team_id) fail(409, 'team_already_joined');
    tx(db, () => {
      db.prepare('UPDATE users SET team_id = ? WHERE id = ?').run(invitation.team_id, req.uid);
      db.prepare('DELETE FROM team_invitations WHERE recipient_id = ?').run(req.uid);
    });
    res.json(me(db, req.uid));
  });

  r.delete('/teams/invitations/:id', auth, (req, res) => {
    const result = db.prepare('DELETE FROM team_invitations WHERE id = ? AND (recipient_id = ? OR sender_id = ?)')
      .run(id(req.params.id), req.uid, req.uid);
    if (!result.changes) fail(404, 'unknown_request');
    res.json({ ok: true });
  });
}
