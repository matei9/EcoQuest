// Usage: npm run add-event -- <CODE> "<label>"   (QR content for a community clean-up)
// Deactivate: npm run add-event -- <CODE> --off
import { config } from './config.js';
import { openDb } from './db.js';

const [code, label = 'Community event'] = process.argv.slice(2);
if (!code) {
  console.error('Usage: npm run add-event -- <CODE> "<label>" | --off');
  process.exit(1);
}
const db = openDb(config.dbPath);
if (label === '--off') {
  db.prepare('UPDATE event_codes SET active = 0 WHERE code = ?').run(code);
  console.log(`Deactivated ${code}`);
} else {
  db.prepare('INSERT INTO event_codes (code, label) VALUES (?, ?) ON CONFLICT(code) DO UPDATE SET label = excluded.label, active = 1').run(code, label);
  console.log(`Event ${code} active: ${label}`);
}
