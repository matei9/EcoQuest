# Green Guardians API

Node.js 24 + Express 5 + SQLite (`node:sqlite`, no native deps). Listens on **127.0.0.1:5200**; nginx terminates HTTPS on the domain and proxies `/api/` to it.

## Run locally

```bash
cd server
npm install
cp .env.example .env        # set JWT_SECRET (>= 32 chars)
npm run dev                 # http://127.0.0.1:5200/api/health
npm test
```

Android emulator → local server: in `gradle.properties` set `GG_API_URL=http://10.0.2.2:5200` (cleartext is allowed only for 10.0.2.2) and start the server with `HOST=0.0.0.0`.

## Deploy behind Cloudflare (owner's chosen setup)

See [deploy/CLOUDFLARE.md](deploy/CLOUDFLARE.md): Node on 0.0.0.0:5200, no TLS on the server, Cloudflare proxy does HTTPS, Origin Rule rewrites the port to 5200. Script: `deploy/install-cloudflare.sh` (does not touch the firewall).

## Alternative deploy: nginx + Let's Encrypt on the server

1. DNS: `A` record `api.domeniul-tau.ro` → server IP.
2. Copy the repo to the server, then from `server/`:
   ```bash
   sudo DOMAIN=api.domeniul-tau.ro EMAIL=admin@domeniul-tau.ro bash deploy/install.sh
   ```
   Installs Node 24, nginx, certbot (Let's Encrypt), ufw, a `greenguardians` system user, systemd unit `green-guardians-api`, daily SQLite backups. Port 5200 is not opened in the firewall.
3. Edit `/etc/green-guardians/api.env` (keys below), then `sudo systemctl restart green-guardians-api`.
4. App: `GG_API_URL=https://api.domeniul-tau.ro` in `gradle.properties`, rebuild.

Logs: `journalctl -u green-guardians-api -f`. Update: pull the repo and re-run `install.sh` (keeps DB + env).

## Environment

| Var | Purpose |
|---|---|
| `PORT` / `HOST` | `5200` / `127.0.0.1` |
| `DB_PATH` | SQLite file (`/var/lib/green-guardians/green-guardians.db` in prod) |
| `JWT_SECRET` | session + boarding-token signing, >= 32 chars (installer generates it) |
| `DEMO_MODE` | `true` lets the app send `{type:"demo"}` proofs (jury demo). Keep `false` live |
| `KILO_ENABLED`, `KILO_BASE_URL` | Guardian AI chat via Kilo gateway (`https://api.kilo.ai/api/gateway`, OpenAI-compatible). **Free models need no key** (verified 2026-10-17) |
| `KILO_MODEL` | comma list tried in order, default `kilo-auto/free` (**owner requirement, keep it**); extra entries = fallbacks on error or silence (`KILO_FIRST_EVENT_MS` / `KILO_FIRST_TEXT_MS`, both 90000) |
| `KILO_API_KEY` | optional, only for higher limits / paid models |
| `TRANZY_API_KEY`, `TRANZY_AGENCY_ID` | CTP Iași live vehicles via Tranzy OpenData (`https://api.tranzy.ai/v1/opendata`, headers `X-API-KEY`, `X-Agency-Id`). Agency id auto-detected by name if empty. Key: sign up at https://apps.tranzy.ai/accounts (Open Data Portal) → create API key |
| `BOARD_RADIUS_M` | max user↔vehicle distance when boarding/alighting (300) |
| `BOSS_START` | community boss counts trips from this day |

If Kilo fails (or `KILO_ENABLED=false`) the app falls back to its offline answers; without `TRANZY_API_KEY` transit quests return `transit_unavailable`.

## How verification works (no server-side AI)

| Proof | On the phone | Server checks |
|---|---|---|
| SGR receipt (`POST /receipts`) | ML Kit OCR text + SHA-256 of image | re-parses text (`SGR`/`garanție`, count or 0,50 lei deposit), image hash unique, adds packages → SGR quests auto-complete at thresholds |
| Photo (`labels`) | ML Kit image labels + hash | labels match quest (food etc.), hash unique; before/after needs 2 different images |
| Bill / second-hand (`ocr`) | OCR text + hash | contains `kWh` / looks like a receipt; hash unique |
| Walk / cycle (`POST /trips`) | GPS points every ~5 s | distance, duration, avg + per-segment speed caps (car speeds rejected) → km quests auto-complete |
| CTP Iași (`/transit/nearby`, `/transit/board`, `POST /trips` transit) | location | 3 nearest live routes; a vehicle of the chosen route must be ≤ 300 m at boarding AND at alighting; signed one-time boarding token; speed ≤ 70 km/h |
| Community event (`POST /events/checkin`) | QR scan | code exists in `event_codes` (add with `npm run add-event -- CODE "label"`), once per user |
| Timer / confirm | — | trust-based, low reward |

Rewards (EP, CO₂, EcoCoins) come only from `src/catalog.js`. All proofs from a modified client can still be faked in principle; hashes, speed checks and live-vehicle checks raise the cost.

## Endpoints (all under `/api`, JSON, `Authorization: Bearer <token>` except auth/health/config)

`GET /health` · `GET /config` · `POST /auth/signup` · `POST /auth/login` · `GET /me` · `DELETE /me` · `POST /quiz {answers:[10 option indices]}` · `POST /quests/:id/complete {proof}` · `POST /receipts` · `POST /trips` · `POST /transit/nearby` · `POST /transit/board` · `POST /events/checkin` · `GET /leaderboard/individual?metric=total|verified` · `GET /leaderboard/schools` · `GET /leaderboard/cities` · `GET /teams` · `POST /teams` · `POST /teams/:id/join` · `POST /teams/leave` · `GET /community` · `GET /vouchers` · `POST /vouchers/:id/redeem` · `GET /facts/next` · `GET /trivia` · `POST /trivia/submit {token, answers[10]}` · `POST /ai/chat {messages, lang}` · `POST /ai/chat/stream {messages, lang}` (SSE: `data: {"type":"thinking"|"delta"|"done"|"error"}`; used by the app)

Errors: `{ "error": "<code>" }` with HTTP status; codes are mapped to RO/EN text in `app/.../ui/ApiState.kt`.
