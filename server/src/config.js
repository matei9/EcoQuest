const env = process.env;

export const config = {
  port: Number(env.PORT || 5200),
  host: env.HOST || '127.0.0.1',
  dbPath: env.DB_PATH || './data/green-guardians.db',
  jwtSecret: env.JWT_SECRET || '',
  demoMode: env.DEMO_MODE === 'true',
  // Kilo free models work without a key; a key only raises limits / unlocks paid models.
  kiloEnabled: env.KILO_ENABLED !== 'false',
  kiloKey: env.KILO_API_KEY || '',
  kiloUrl: (env.KILO_BASE_URL || 'https://api.kilo.ai/api/gateway').replace(/\/$/, ''),
  // Owner requirement: kilo-auto/free (fixed free models failed in production). Comma list = fallbacks in order.
  kiloModels: (env.KILO_MODEL || 'kilo-auto/free').split(',').map((s) => s.trim()).filter(Boolean),
  // Auto routing can stay silent ~30 s before the first byte on the owner's server; don't give up early.
  kiloFirstEventMs: Number(env.KILO_FIRST_EVENT_MS || 90_000),
  kiloFirstTextMs: Number(env.KILO_FIRST_TEXT_MS || 90_000),
  tranzyKey: env.TRANZY_API_KEY || '',
  tranzyAgencyId: env.TRANZY_AGENCY_ID || '',
  boardRadiusM: Number(env.BOARD_RADIUS_M || 300),
  bossStart: env.BOSS_START || '2026-10-01',
};

if (config.jwtSecret.length < 32) {
  throw new Error('JWT_SECRET must be set and at least 32 characters long');
}
