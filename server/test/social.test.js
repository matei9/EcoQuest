import assert from 'node:assert/strict';
import { test } from 'node:test';
import jwt from 'jsonwebtoken';

process.env.JWT_SECRET = 'social-test-secret-at-least-32-characters';
process.env.KILO_ENABLED = 'false';
const { createApp } = await import('../src/app.js');
const { openDb } = await import('../src/db.js');

async function fixture(t) {
  const db = openDb(':memory:');
  const users = ['Ana', 'Bogdan', 'Carmen'].map((name, i) => {
    const code = String(i + 1).padStart(8, '0');
    const id = Number(db.prepare('INSERT INTO users (email, pass, name, city, ref_code) VALUES (?, ?, ?, ?, ?)')
      .run(`${name}@example.test`, 'unused', name, 'Iași', code).lastInsertRowid);
    return { id, code, token: jwt.sign({ uid: id, typ: 'session' }, process.env.JWT_SECRET) };
  });
  const server = createApp(db).listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  t.after(async () => {
    await new Promise((resolve) => server.close(resolve));
    db.close();
  });
  const call = async (user, method, path, body) => {
    const res = await fetch(`http://127.0.0.1:${server.address().port}/api${path}`, {
      method,
      headers: { 'Content-Type': 'application/json', ...(user ? { Authorization: `Bearer ${user.token}` } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    return { status: res.status, body: await res.json() };
  };
  const befriend = async (a, b) => {
    assert.equal((await call(a, 'POST', '/friends', { code: b.code })).status, 201);
    assert.equal((await call(b, 'POST', `/friends/${a.id}/accept`)).status, 200);
  };
  return { db, users, call, befriend };
}

test('friend requests require recipient consent, stay private and can be cancelled or removed', async (t) => {
  const { users: [a, b, c], call } = await fixture(t);
  assert.equal((await call(null, 'GET', '/friends')).status, 401);
  assert.equal((await call(a, 'POST', '/friends', { code: a.code })).body.error, 'friend_self');
  assert.equal((await call(a, 'POST', '/friends', { code: 'FFFFFFFF' })).status, 404);
  assert.equal((await call(a, 'POST', '/friends', { code: b.code })).status, 201);
  assert.equal((await call(b, 'POST', '/friends', { code: a.code })).status, 409);
  let r = await call(b, 'GET', '/friends');
  assert.equal(r.body.incoming[0].name, 'Ana');
  assert.equal(r.body.friends.length, 0);
  assert.equal('email' in r.body.incoming[0], false);
  assert.equal('code' in r.body.incoming[0], false);
  assert.equal((await call(a, 'POST', `/friends/${b.id}/accept`)).status, 404);
  assert.equal((await call(c, 'POST', `/friends/${a.id}/accept`)).status, 404);
  assert.equal((await call(b, 'POST', `/friends/${a.id}/accept`)).status, 200);
  assert.equal((await call(a, 'GET', '/friends')).body.friends[0].id, b.id);
  assert.equal((await call(b, 'GET', '/friends')).body.friends[0].id, a.id);
  await call(a, 'DELETE', `/friends/${b.id}`);
  assert.equal((await call(b, 'GET', '/friends')).body.friends.length, 0);
  await call(a, 'POST', '/friends', { code: b.code });
  await call(a, 'DELETE', `/friends/${b.id}`);
  assert.equal((await call(b, 'GET', '/friends')).body.incoming.length, 0);
  await call(a, 'POST', '/friends', { code: b.code });
  await call(b, 'DELETE', `/friends/${a.id}`);
  assert.equal((await call(a, 'GET', '/friends')).body.outgoing.length, 0);
});

test('team invitations require friendship and only the recipient can accept or decline', async (t) => {
  const { db, users: [a, b, c], call, befriend } = await fixture(t);
  assert.equal((await call(a, 'POST', '/teams/invitations', { userId: b.id })).body.error, 'no_team');
  await call(a, 'POST', '/teams', { name: 'Green Team' });
  assert.equal((await call(a, 'POST', '/teams/invitations', { userId: b.id })).status, 403);
  await befriend(a, b);
  assert.equal((await call(a, 'POST', '/teams/invitations', { userId: b.id })).status, 201);
  assert.equal((await call(a, 'POST', '/teams/invitations', { userId: b.id })).status, 409);
  let r = await call(b, 'GET', '/friends');
  const invite = r.body.invitations[0];
  assert.equal(invite.teamName, 'Green Team');
  assert.equal(db.prepare('SELECT team_id FROM users WHERE id = ?').get(b.id).team_id, null);
  assert.equal((await call(c, 'POST', `/teams/invitations/${invite.id}/accept`)).status, 404);
  assert.equal((await call(c, 'DELETE', `/teams/invitations/${invite.id}`)).status, 404);
  assert.equal((await call(a, 'POST', `/teams/invitations/${invite.id}/accept`)).status, 404);
  assert.equal((await call(b, 'DELETE', `/teams/invitations/${invite.id}`)).status, 200);
  await call(a, 'POST', '/teams/invitations', { userId: b.id });
  r = await call(b, 'GET', '/friends');
  const accepted = await call(b, 'POST', `/teams/invitations/${r.body.invitations[0].id}/accept`);
  assert.equal(accepted.status, 200);
  const team = db.prepare('SELECT team_id FROM users WHERE id = ?').get(a.id).team_id;
  assert.equal(accepted.body.user.teamId, team);
  assert.equal((await call(b, 'GET', '/friends')).body.invitations.length, 0);
  assert.equal((await call(a, 'POST', '/teams/invitations', { userId: b.id })).body.error, 'team_already_joined');
});

test('leaving a team and removing a friend invalidate outstanding invitations', async (t) => {
  const { users: [a, b], call, befriend } = await fixture(t);
  await befriend(a, b);
  await call(a, 'POST', '/teams', { name: 'Eco Team' });
  await call(a, 'POST', '/teams/invitations', { userId: b.id });
  const old = (await call(b, 'GET', '/friends')).body.invitations[0].id;
  await call(a, 'POST', '/teams/leave');
  assert.equal((await call(b, 'GET', '/friends')).body.invitations.length, 0);
  assert.equal((await call(b, 'POST', `/teams/invitations/${old}/accept`)).status, 404);
  await call(a, 'POST', '/teams', { name: 'New Eco Team' });
  assert.equal((await call(a, 'POST', '/teams/invitations', { userId: b.id })).status, 201);
  await call(b, 'DELETE', `/friends/${a.id}`);
  assert.equal((await call(b, 'GET', '/friends')).body.invitations.length, 0);
});

test('an invitation never replaces an existing team and joining clears other invitations', async (t) => {
  const { db, users: [a, b, c], call, befriend } = await fixture(t);
  await befriend(a, b);
  await befriend(c, b);
  await call(a, 'POST', '/teams', { name: 'First Team' });
  await call(c, 'POST', '/teams', { name: 'Second Team' });
  await call(a, 'POST', '/teams/invitations', { userId: b.id });
  await call(c, 'POST', '/teams/invitations', { userId: b.id });
  const invitations = (await call(b, 'GET', '/friends')).body.invitations;
  assert.equal(invitations.length, 2);
  assert.equal((await call(b, 'POST', `/teams/invitations/${invitations[0].id}/accept`)).status, 200);
  assert.equal((await call(b, 'GET', '/friends')).body.invitations.length, 0);
  assert.equal((await call(b, 'POST', `/teams/invitations/${invitations[1].id}/accept`)).status, 404);
  // Also guard against stale invitations if membership changed outside the API.
  const other = invitations[1].id;
  const team = db.prepare('SELECT team_id FROM users WHERE id = ?').get(a.id).team_id;
  db.prepare('INSERT INTO team_invitations (id, team_id, sender_id, recipient_id) VALUES (?, ?, ?, ?)').run(other, team, a.id, b.id);
  assert.equal((await call(b, 'POST', `/teams/invitations/${other}/accept`)).body.error, 'team_already_joined');
});
