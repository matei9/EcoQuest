# Green Guardians API

Node.js 24 + Express 5 + SQLite (`node:sqlite`, no native deps). Listens on **127.0.0.1:5200**; nginx terminates HTTPS on the domain and proxies `/api/` to it.

## Run locally

```bash
cd server
npm install
python3 -m venv .venv-ocr     # Python 3.12 recommended
.venv-ocr/bin/python -m pip install -r requirements-ocr.txt
cp .env.example .env        # set JWT_SECRET (>= 32 chars)
npm run dev                 # http://127.0.0.1:5200/api/health
npm test
```

Android emulator → local server: in ignored `local.properties` set `GG_API_URL=http://10.0.2.2:5200` (cleartext is allowed only for 10.0.2.2) and start the server with `HOST=0.0.0.0`.

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
4. App: `GG_API_URL=https://api.domeniul-tau.ro` in ignored `local.properties`, rebuild.

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

## How verification works

| Proof | On the phone | Server checks |
|---|---|---|
| SGR receipt (`POST /receipts`) | gallery or integrated CameraX capture, orientation normalization and JPEG upload | local neural RapidOCR/ONNX reads the photo, validates printed date/time with no age restriction or SGR keyword requirement, and blocks reused image or date/time across accounts. Extracts quantity or uses optional manual `count` (1–200) if unreadable. An unreadable quantity does not reject the receipt or invent package credit. Every accepted receipt completes the daily mystery quest; known packages count toward SGR thresholds. |
| Photo (`labels`) | ML Kit image labels + hash | labels match quest (food etc.), hash unique; before/after needs 2 different images |
| Bill / second-hand (`ocr`) | OCR text + hash | contains `kWh` / looks like a receipt; hash unique |
| Walk / cycle (`POST /trips`) | GPS points every ~5 s | distance, duration, avg + per-segment speed caps (car speeds rejected) → km quests auto-complete |
| CTP Iași (`/transit/nearby`, `/transit/board`, `POST /trips` transit) | location | 3 nearest live routes; a vehicle of the chosen route must be ≤ 300 m at boarding AND at alighting; signed one-time boarding token; speed ≤ 70 km/h |
| Community event (`POST /events/checkin`) | QR scan | code exists in `event_codes` (add with `npm run add-event -- CODE "label"`), once per user |
| Timer / confirm | — | trust-based, low reward |

Rewards (EP, CO₂, EcoCoins) come only from `src/catalog.js`. All proofs from a modified client can still be faked in principle; hashes, speed checks and live-vehicle checks raise the cost.

## Endpoints (under `/api`, JSON except receipt image upload, `Authorization: Bearer <token>` except auth/health/config)

Guardian AI is scoped to app usage and practical eco actions relevant to its quests. Both JSON and SSE chat paths use the same server topic check: clearly unrelated questions receive a short localized redirect without calling the model. Recognized short follow-ups can inherit the preceding relevant question; prior eco history cannot unlock a new unrelated question. The system prompt also instructs models to decline unrelated/pretext requests and ignore role changes in user messages. This is a product topic filter with model instructions, not a guarantee that every ambiguous phrasing is classified perfectly. Deploy the updated backend for this behavior.

Tree garden: authenticated `GET /tree` returns the profile with `progress.tree`; `GET /leaderboard/trees` returns top 50 trees by current height and `mine` even outside the top 50. Heights tied share a rank; no emails or private activity dates appear in the ranking. Quest completions, including existing history, are the only source of active days; multiple completions count once a day. Quiz/trivia alone do not grow the tree. `tree_states` persists a server-owned cache, updated after quest awards and profile reads. The leaderboard also settles dormant users' missed days before ranking; there is no client growth/write endpoint.

Seven tree stages begin at stage 1 (seed). Each three consecutive active days within a stage advance it, reaching stage 7 after 18 uninterrupted active days. Stage heights are 0.20 / 0.50 / 1 / 2 / 3.5 / 5 / 7 metres. Before maturity, one missed day wilts it and resets the current three-day progress; every two consecutive missed days drop one stage, floored at seed. Returning restores health and starts fresh three-day stage progress. After maturity (owner-confirmed), it stays at stage 7: every active day adds 1 m and every closed missed day subtracts 1 m, minimum living height 1 m. Today's inactivity is never penalized until Romanian midnight. Missing-day gaps are calculated arithmetically rather than iterating across every dormant day. The existing weekly 5-of-7 activity widget stays separate; the tree uses consecutive quest days from the same source.

Android's bottom Tree tab displays seven original vector growth forms in a gently animated garden, health and stage progress, total height and the tree leaderboard. Above 20 m it renders only a bounded canopy/trunk viewport instead of allocating an enormous image. State remains available from the cached profile offline, with a warning when refresh fails. Update both backend and Android app; tree history backfills automatically, without resetting quests or points.

Personalized daily quests: `POST /quiz` also accepts `locality: "urban" | "rural"` and `publicTransport: boolean`, retaining the ten scored answers. `/me` exposes `progress.dailyQuestIds` (six assignments stable for the Romanian calendar day), `user.locality`, and `user.publicTransport`. Selection prioritizes weak quiz categories and specific answers, with deterministic daily variety and at most two quests from one category. Local-market community quest `d16` and entry SGR target `d5` are included. Standard daily quests outside the assigned set cannot be completed or auto-awarded. Rural users, those without practical transport access, and cities outside the supported Iași transit validation do not receive transit quests. Retaking the quiz changes the next day's selection; newly unavailable transit quests are replaced immediately without rerolling other assignments. Existing accounts can retake the quiz to supply locality/access. Weekly community market quest `w10` is also available. Market quests use the existing honour-confirmation proof flow.

Duels: `GET /duels`, `POST /duels/friends {userId}`, `POST /duels/:id/accept`, `DELETE /duels/:id` (pending invitations only), `POST /duels/queue`, `DELETE /duels/queue`. Friendly invitations require accepted friendship and recipient acceptance. Queueing opts into ranked matching with a random nonfriend; searches expire after 30 minutes and pending friend invitations after seven days. A player can have one active duel; starting it cancels other pending invitations and removes both queue entries. Duels last exactly seven days from acceptance/matching. Only server-issued earned EP (quests and trivia) recorded in that half-open interval count, never baseline quiz points or historic EP. An append-only `earned_points` ledger records new awards; historical totals are not backfilled into matches. Results finalize on the next duel/profile request after the deadline, retaining final scores. Deleting an account forfeits its active duel. `/me.progress.duelStats` contains ranked `played`, `wins`, `draws`, and separate 1v1 `points` (3/win, 1/draw, 0/loss); friendly results never change these stats or grant bonus EcoPoints. The Android UI is under Leagues → 1v1; friend challenges are under Teams → Friends.

Schema upgrades are additive and applied by `openDb`; update/restart the backend and install the new Android build together. Assignment, queue and duel state persist in SQLite across service restarts. Active duels are shown before invitations and history; the response retains at most 30 matches per participant.

`GET /health` · `GET /config` · `POST /auth/signup` · `POST /auth/login` · `GET /me` · `DELETE /me` · `POST /quiz {answers:[10 option indices]}` · `POST /quests/:id/complete {proof}` · `POST /receipts` · `POST /trips` · `POST /transit/nearby` · `POST /transit/board` · `POST /events/checkin` · `GET /leaderboard/individual?metric=total|verified` · `GET /leaderboard/cities` · `GET /teams` · `POST /teams` · `POST /teams/:id/join` · `POST /teams/leave` · `GET /community` · `GET /vouchers` · `POST /vouchers/:id/redeem` · `GET /facts/next` · `GET /trivia` · `POST /trivia/submit {token, answers[10]}` · `POST /ai/chat {messages, lang}` · `POST /ai/chat/stream {messages, lang}` (SSE: `data: {"type":"thinking"|"delta"|"done"|"error"}`; used by the app)

Errors: `{ "error": "<code>" }` with HTTP status; codes are mapped to RO/EN text in `app/.../ui/ApiState.kt`.

Receipt dates support `DD.MM.YYYY`, `DD/MM/YYYY`, `DD-MM-YYYY`, `YYYY-MM-DD` and abbreviated Romanian/English month names (including LIDL `08:22:45 18-OCT-2026`). Date and time must both be present and calendar-valid; past and future dates are accepted per the owner's requested policy. Full timestamps are normalized for duplicate checks; when seconds are unreadable, matching any saved timestamp in the same minute is rejected conservatively. The optional manual quantity is used only when text extraction finds no count. Rewards are credited to the upload day's period. Previous uploads that stored only image hashes cannot retroactively be deduplicated by timestamp. Update the backend and Android app together.

## Server receipt OCR setup

The deployment installers install Python, create `.venv-ocr`, and install the pinned packages in `requirements-ocr.txt`. RapidOCR's models are bundled in its package; inference uses the local CPU and does not call a cloud OCR API. `GG_OCR_PYTHON` can override the interpreter path; otherwise the backend uses `.venv-ocr/bin/python` when present. On minimal Linux hosts, OpenCV also requires `libgl1` and `libglib2.0-0`, which the installers install.

`POST /api/receipts` now takes a **raw JPEG or PNG body**, content type `image/jpeg` or `image/png`, up to 12 MiB; optional manual quantity is `?count=3`. Text-only JSON submissions return HTTP 426 `receipt_upgrade_required`. The server computes the image hash itself. Images are placed in private temporary files and deleted after processing, including failures. Only timestamp deduplication keys, image hashes, quantity and existing progress records remain. There is one active OCR job per server process, a 75-second worker timeout, and a per-user limit of 20 submissions per minute.

`GET /api/config` includes `receiptOcr: "server-v1"` and `receiptValidation: "datetime-v2"`; the Android app checks this before uploading so an old backend gives an explicit update-required message. Missing Python dependencies return `receipt_ocr_unavailable`; a busy worker returns `receipt_ocr_busy`. For a local real-image regression test, set `GG_RECEIPT_TEST_IMAGE` to the private LIDL fixture path and `GG_OCR_PYTHON` to the configured interpreter, then run the test suite. The fixture is not committed.

After copying the updated project onto the deployment host, run `sudo bash deploy/install-cloudflare.sh` from `server/` (or the existing nginx installer if that is your setup). This installs OCR dependencies and restarts the API while keeping its DB and environment file. Rebuild/reinstall Android as well. The nginx template now allows 12 MiB uploads and a 100-second proxy read timeout.

## Saved GPS trips

Walking/cycling recordings use a foreground location service and a local per-account draft in the Android app. Closing the quest does not end the recording. The notification can stop GPS while keeping the route for submission. After process death, a restored draft is stopped; it must be submitted or discarded before recording another route, so unrecorded movement is not counted.

`POST /api/trips` accepts an optional UUID `clientId`. Retrying an accepted ID for the same account/mode returns the saved distance and current profile with no new rewards or trip row. Old clients without this field still work. The backend automatically migrates existing trip tables and preserves their data. Update/restart the backend before installing this app change to ensure retries cannot duplicate saved distances.
## Friends and team invitations

Deploy the updated backend before using the friends UI in the Android app. Restarting the backend creates the new `friendships` and `team_invitations` tables automatically; existing accounts and teams are preserved.

In **Leagues → Teams → Friends and invitations**, users share their existing personal code, send a friend request, and accept or decline incoming requests. Either friend can remove the relationship. A team member can invite an accepted friend who has no team; the recipient must explicitly accept to join. Changing teams or removing the friendship clears affected outstanding invitations. Lists refresh when the screen opens or the user taps Refresh list; there are no push notifications.

All endpoints require a session token:

- `GET /api/friends`: friends, incoming/outgoing friend requests, incoming team invitations and recipients already invited to the current team. Responses exclude emails and other users' personal codes.
- `POST /api/friends` with `{ "code": "PERSONAL_CODE" }`: send a friend request.
- `POST /api/friends/:id/accept`: accept a request from this user.
- `DELETE /api/friends/:id`: decline/cancel a request or remove a friend.
- `POST /api/teams/invitations` with `{ "userId": 123 }`: invite a friend to the caller's team.
- `POST /api/teams/invitations/:id/accept`: accept an incoming invitation; returns the updated profile.
- `DELETE /api/teams/invitations/:id`: decline or withdraw a team invitation.

The mystery quest now validates an SGR receipt rather than collection-point image labels. Only a calendar-valid printed date/time is required. Missing quantity is stored as zero; optional `count` must be 1–200 when provided. Duplicate protection applies only to accepted receipts (including historical receipt hashes), globally by image and printed timestamp. Rejected attempts do not reserve either key. Unrelated photo-proof hashes do not block receipt uploads. Update both backend and Android app for this policy; the new app checks the `datetime-v2` capability.

School/university selection and the schools leaderboard have been removed. Signups no longer store school input, the profile no longer returns it, and `/leaderboard/schools` is unavailable. `/config.schools` remains an empty array for older-client compatibility. The legacy nullable SQLite column is retained so existing databases can upgrade without a destructive schema change.
