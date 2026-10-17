import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

export function openDb(path) {
  if (path !== ':memory:') mkdirSync(dirname(path), { recursive: true });
  const db = new DatabaseSync(path);
  db.exec(`
    PRAGMA journal_mode = WAL;
    PRAGMA foreign_keys = ON;
    CREATE TABLE IF NOT EXISTS teams (
      id INTEGER PRIMARY KEY,
      name TEXT NOT NULL UNIQUE COLLATE NOCASE,
      city TEXT NOT NULL,
      created_by INTEGER NOT NULL,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY,
      email TEXT NOT NULL UNIQUE COLLATE NOCASE,
      pass TEXT NOT NULL,
      name TEXT NOT NULL,
      city TEXT NOT NULL,
      school TEXT,
      team_id INTEGER REFERENCES teams(id) ON DELETE SET NULL,
      ref_code TEXT NOT NULL UNIQUE,
      referred_by INTEGER REFERENCES users(id),
      quiz_done INTEGER NOT NULL DEFAULT 0,
      cat_scores TEXT NOT NULL DEFAULT '0,0,0,0,0',
      initial_ep INTEGER NOT NULL DEFAULT 0,
      verified_ep INTEGER NOT NULL DEFAULT 0,
      co2 REAL NOT NULL DEFAULT 0,
      coins INTEGER NOT NULL DEFAULT 0,
      sgr INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE TABLE IF NOT EXISTS completions (
      id INTEGER PRIMARY KEY,
      user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      quest_id TEXT NOT NULL,
      kind TEXT NOT NULL,
      period TEXT NOT NULL,
      day TEXT NOT NULL,
      UNIQUE (user_id, quest_id, period)
    );
    CREATE TABLE IF NOT EXISTS trips (
      id INTEGER PRIMARY KEY,
      user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      mode TEXT NOT NULL,
      km REAL NOT NULL,
      day TEXT NOT NULL,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE TABLE IF NOT EXISTS receipts (
      id INTEGER PRIMARY KEY,
      user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      count INTEGER NOT NULL,
      day TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS checkins (
      user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      code TEXT NOT NULL,
      day TEXT NOT NULL,
      PRIMARY KEY (user_id, code)
    );
    -- One proof (image hash, boarding token) can be used only once, by anyone.
    CREATE TABLE IF NOT EXISTS proof_hashes (
      hash TEXT PRIMARY KEY,
      user_id INTEGER NOT NULL,
      kind TEXT NOT NULL,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE TABLE IF NOT EXISTS redemptions (
      id INTEGER PRIMARY KEY,
      user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      voucher_id TEXT NOT NULL,
      code TEXT NOT NULL,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE TABLE IF NOT EXISTS event_codes (
      code TEXT PRIMARY KEY,
      label TEXT NOT NULL,
      active INTEGER NOT NULL DEFAULT 1
    );
    CREATE TABLE IF NOT EXISTS seen_facts (
      user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      fact_id TEXT NOT NULL,
      PRIMARY KEY (user_id, fact_id)
    );
    -- One daily trivia attempt per user.
    CREATE TABLE IF NOT EXISTS trivia_attempts (
      user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      day TEXT NOT NULL,
      score INTEGER NOT NULL,
      ep INTEGER NOT NULL,
      PRIMARY KEY (user_id, day)
    );
    CREATE INDEX IF NOT EXISTS idx_completions_user_day ON completions(user_id, day);
    CREATE INDEX IF NOT EXISTS idx_trips_user_day ON trips(user_id, day);
    CREATE INDEX IF NOT EXISTS idx_receipts_user_day ON receipts(user_id, day);
  `);
  return db;
}

/** node:sqlite has no transaction helper; the API is synchronous so this is safe. */
export function tx(db, fn) {
  db.exec('BEGIN IMMEDIATE');
  try {
    const out = fn();
    db.exec('COMMIT');
    return out;
  } catch (e) {
    db.exec('ROLLBACK');
    throw e;
  }
}
