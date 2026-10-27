# Zweetnekken RuneLite API contract

The plugin communicates with the Zweetnekken backend using the production API origin below.
The production origin is centralized in `ZweetnekkenApiClient.ZWEETNEKKEN_API_ORIGIN`:
`https://api-production-81001.up.railway.app`.

## Authentication

The player links RuneLite from the Zweetnekken Discord bot and puts their permanent plugin code in **Zweetnekken → Authentication → Permanent link code**.
Authenticated requests use `Authorization: Bearer <code>`. Never place `DISCORD_BOT_API_TOKEN` in the RuneLite plugin; it is reserved for the trusted Discord/backend integration.

## Player-facing requests

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/clan/profile?name=RSN` | Clan membership, PvM/clan points, rank progress, available competition standings. `member.avatarUrl` is a relative path on the backend origin. Public roster lookup. |
| GET | `/api/competitions` | Current, upcoming and previous SOTW/BOTW/ROTM competitions. |
| GET | `/api/competitions/panels` | Compact dashboard panels and leading participants. |
| GET | `/api/profile-portrait?name=RSN` | Player portrait proxy through the Zweetnekken backend. |
| GET | `/api/clan/member-avatar?discordId=DiscordID` | Proxied Discord avatar for a roster member. Profile avatar paths must be prefixed with `ZweetnekkenApiClient.ZWEETNEKKEN_API_ORIGIN`; external avatar URLs are not returned by the profile API. |
| GET | `/zweetnekken-banner.jpg` | Public current clan banner used by the RuneLite side panel. |
| GET | `/botw.png` | Generated Boss of the Week banner based on the backend competition snapshot. |
| GET | `/sotw.png` | Generated Skill of the Week banner based on the backend competition snapshot. |
| GET | `/api/runelite/event-alerts` | Current event-alert state, bounded history of recent announcements, and backend time for clock-skew-safe delivery. |
| GET | `/api/pvm-diary/status?rsn=RSN` | The published PvM Diary status for a rostered player. |
| GET | `/api/runelite/session` | Validates the permanent bearer code/session. Authenticated. |
| GET | `/api/runelite/rules` | Current server-side loot capture rules. |
| GET | `/api/runelite/bingo` | Active Bingo event and team mapping. Authenticated. |
| GET | `/api/runelite/bingo/rules` | Server-authoritative Bingo capture rules. Authenticated. |
| GET | `/api/runelite/pvm-diary/contract` | Versioned PvM Diary activity contract. Authenticated. |

The API intentionally returns an inactive Bingo configuration when no Bingo event is active. The PvM Diary contract/status can return a clear 503/404 while no catalogue/status has been published; a missing source is not treated as a successful sync.

## Evidence and synchronization

All writes below require the bearer code and are tied server-side to the Discord identity represented by that code.

| Method | Path | Purpose |
| --- | --- | --- |
| POST multipart | `/api/runelite/events` | Upload event metadata and optional evidence screenshot. The backend persists the event as a draft. |
| PATCH | `/api/runelite/submissions/{submissionId}` | Save confirmed party size, clan/gast counts, participant names/statuses, and notes. |
| POST | `/api/runelite/submissions/submit` | Explicitly submit the draft for further processing. JSON body: `{"submissionId":"..."}`. |
| GET | `/api/runelite/submissions?player=RSN` | Return submission status and server-side failure reason for the authenticated player. |
| POST | `/api/runelite/personal-bests` | Sync a PB record batch. |
| POST | `/api/runelite/personal-bests/bulk` | Sync a full PB snapshot. |
| POST | `/api/runelite/player-progress` | Sync Combat Achievements, quests, achievement diaries and skill progress. |
| POST | `/api/runelite/collection-log` | Backend endpoint retained for compatibility; the plugin currently does not send collection-log snapshots. |

PB, progress and collection-log snapshots are stored idempotently using sync ID and authenticated Discord identity. The backend validates that the submitted RSN is listed among that member's synced clan aliases. Do not use client-supplied account IDs as an authorization decision.

## Trusted Discord-to-backend configuration

These routes are **server-to-server only** and use `X-Discord-Bot-Token`; they must never be called with a plugin bearer code.

- `POST /v1/discord/runelite/config`: publish `event_alert`, `bingo_event`, `bingo_rules`, or `pvm_diary_contract`.
- `POST /v1/discord/runelite/pvm-diary-status`: publish a status snapshot for a rostered RSN.

The Discord integration remains the source of truth for staff-managed event configuration. If no trusted integration has published an event/configuration, the plugin should display the unavailable/inactive state instead of fabricating data.

## Local build

Run `./gradlew --no-daemon clean build` with JDK 17 or newer to run the Gradle 8.10 build. The source target remains Java 11. CI runs the same Gradle build on pushes and pull requests.


## PvM Diary — parked for later

The custom PvM Diary is intentionally **not active in the RuneLite plugin** for now. Its panel, status polling, contract refresh, completion-based event capture, and automatic retry/upload of legacy `diary` outbox records are disabled. The full pre-park implementation is preserved on the `feature/pvm-diary-later` branch. The production plugin no longer contains the Diary DTOs, API client methods, UI, or capture logic; backend routes/config storage are left staged for later.

Do not re-enable the Diary API calls or `diary` event captures as part of unrelated plugin fixes. Reintroduce the feature only when explicitly requested.
