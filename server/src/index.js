import { createApp } from './app.js';
import { config } from './config.js';
import { openDb } from './db.js';

const db = openDb(config.dbPath);
const server = createApp(db).listen(config.port, config.host, () => {
  console.log(`Green Guardians API on http://${config.host}:${config.port} (demo=${config.demoMode})`);
});

const stop = () => server.close(() => { db.close(); process.exit(0); });
process.on('SIGTERM', stop);
process.on('SIGINT', stop);
