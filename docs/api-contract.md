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
| GET | `/api/clan/profile?name=RSN` | Clan membership, PvM/clan points, rank progress, available competition standings, and the latest synced Combat Achievement and Collection Log summaries for the requested RuneScape name. `member.linkedNames` lists linked RSNs for the same Discord member in primary-first order so the plugin can load each account's progress separately. Both summaries omit their detailed task/item lists; Combat Achievements include `updatedAt` for the `Synced` label. `member.avatarUrl` is a relative path on the backend origin. Public roster lookup. |
| GET | `/api/clan/leaderboard` | Public clan-wide leaderboard from the bot's Railway PostgreSQL database. Returns `{ "members": [{ "discordId": "123456789", "name": "RSN", "avatarUrl": "/api/...", "currentRank": "Zealot", "clanRankOrder": 1, "pvmPoints": 123, "clanPoints": 456 }], "totalMembers": 1 }`. `name` is the member's first linked RSN, which is the primary account. `clanRankOrder` follows the backend's clan-rank progression, with higher values indicating higher ranks. The plugin sorts this roster locally by PvM points, clan points, or clan rank. |
| GET | `/api/competitions` | Current, upcoming and previous SOTW/BOTW/ROTM competitions. |
| GET | `/api/competitions/panels` | Compact dashboard panels and leading participants. |
| GET | `/api/profile-portrait?name=RSN` | Player portrait proxy through the Zweetnekken backend. |
| GET | `/api/clan/member-avatar?discordId=DiscordID` | Proxied Discord avatar for a roster member. Profile avatar paths must be prefixed with `ZweetnekkenApiClient.ZWEETNEKKEN_API_ORIGIN`; external avatar URLs are not returned by the profile API. |
| GET | `/zweetnekken-banner.jpg` | Public current clan banner used by the RuneLite side panel. |
| GET | `/botw.png` | Generated Boss of the Week banner based on the backend competition snapshot. |
| GET | `/sotw.png` | Generated Skill of the Week banner based on the backend competition snapshot. |
| GET | `/api/runelite/event-alerts` | Current event-alert state, bounded history of recent announcements, and backend time for clock-skew-safe delivery. |
| GET | `/api/pvm-diary/status?rsn=RSN&viewer=RSN` | Published PvM Diary progress for a rostered player. Authenticated. |
| GET | `/api/runelite/session` | Validates the permanent bearer code/session. Authenticated. |
| GET | `/api/runelite/rules` | Current server-side loot capture rules. |
| GET | `/api/runelite/bingo` | Active Bingo event and team mapping. Authenticated. |
| GET | `/api/runelite/bingo/rules` | Server-authoritative Bingo capture rules. Authenticated. |
| GET | `/api/runelite/pvm-diary/contract?viewer=RSN` | Versioned PvM Diary rollout and catalogue configuration. Authenticated. |

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
| POST | `/api/runelite/collection-log` | Sync the authenticated player's current Collection Log snapshot. The client captures item IDs from RuneLite script 4100 and sends totals, categories, and item quantities. |

PB, progress and collection-log snapshots are stored idempotently using sync ID and authenticated Discord identity. Combat Achievement and Collection Log snapshots are kept separately for each linked RuneScape name, so each alt has its own profile data. The backend validates that the submitted RSN is listed among that member's synced clan aliases. Do not use client-supplied account IDs as an authorization decision.

## Trusted Discord-to-backend configuration

These routes are **server-to-server only** and use `X-Discord-Bot-Token`; they must never be called with a plugin bearer code.

- `POST /v1/discord/runelite/config`: publish `event_alert`, `bingo_event`, `bingo_rules`, or `pvm_diary_contract`.
- `POST /v1/discord/runelite/pvm-diary-status`: publish a status snapshot for a rostered RSN.

The Discord integration remains the source of truth for staff-managed event configuration. If no trusted integration has published an event/configuration, the plugin should display the unavailable/inactive state instead of fabricating data.

## Local build

Run `./gradlew --no-daemon clean build` with JDK 17 or newer to run the Gradle 8.10 build. The source target remains Java 11. CI runs the same Gradle build on pushes and pull requests.


## PvM Diary rollout

Staff can manage access in **PvM Diaries → Uitrol** in the dashboard. Add testers by Discord ID and save the “Alleen geselecteerde testers” mode. Every eligible RuneScape account linked to that Discord ID can participate. Save “Alle pluginleden” to release broadly, or “Uit” to hide the feature. Adding or removing a tester leaves the rollout mode as saved.

The dashboard stores access separately from catalogue content. After the first dashboard change, its settings take precedence over the rollout fields in the published JSON. Existing name-based testers are resolved to their linked Discord IDs for that first change. If staff have yet to publish a full JSON catalogue, the dashboard uses the backend's built-in catalogue and any saved tier settings.


Publish [pvm-diary-config.json](pvm-diary-config.json) as the value of `pvm_diary_contract` through the trusted configuration route. The contract endpoint returns the active catalogue and rollout settings for the authenticated viewer. A tester receives only their own allowlist entry. The example starts disabled and includes the initial catalogue.

| Rollout | JSON settings |
| --- | --- |
| Off | `"enabled": false` |
| Selected testers | `"enabled": true, "audience": "testers", "testers": ["Example RSN"]` |
| All eligible plugin users | `"enabled": true, "audience": "all"` |

Testers are selected by their currently logged-in RuneScape name. Put each eligible alt that should participate on the list. Name matching ignores case and treats underscores as spaces. A missing contract, unsupported version, invalid catalogue, unknown audience, or empty test list keeps the feature hidden. The plugin checks the config when loading the player's roster profile, on the one-minute eligibility refresh, and when using the manual profile refresh.

Keep `version` at `1` for this JSON format. Update `catalogueVersion` whenever the catalogue changes. The backend builds progress from reviewed evidence and uses the current catalogue version. Categories and tiers render in JSON order, matching progress by activity ID and tier name. Preserve these identifiers for tiers that keep their meaning. Use milliseconds for `time`, a wave number for `wave`, and omit the target for `completion`. The config supplies names, categories, team sizes, targets, labels, and points. The backend remains authoritative for evidence validation and awards.

An activity can map to a client observation using `sourceActivity`, `teamSize`, and optional `sourceVariant` and `invocation`. Use the activity name emitted by the existing game or personal-best detector. For solo time and wave activities without an invocation, a `chatTemplate` or a list of up to eight `chatTemplates` can replace the mapped detector. The Doom example includes the level completion, normal delve, and deep delve messages. A matching wave activity uses `{wave}` as the result and can use `{time}` to verify the presence of a duration. Templates use literal text with `{time}` or `{wave}`, and can end with `{rest}` to accept an optional suffix. The client skips ambiguous matches. New game signals and changes to how RuneLite recognises raids or parties require client code.

Timed activities can set `minimumTimeMillis`. The plugin skips shorter captures and the backend rejects shorter uploads or submissions, including evidence from older clients. The floor also excludes invalid historical evidence from Diary progress. Colosseum starts with a five-minute floor and total-duration chat templates. Match the exact full-run message from a tester's game chat when tuning these templates. Keep wave splits and PB-improvement messages outside the templates. Use the staff review flow to reject incorrect pending Discord submissions.

The backend enforces the rollout for the contract and status reads and for Diary evidence uploads, updates, and submissions. Resolve the caller from the bearer code and verify that the viewer RSN is linked to that identity before checking the test list. For a member-profile lookup, apply the rollout to the viewer and resolve the requested target separately. The plugin implements the same display and capture gate locally. The staff dashboard uses the separate authenticated `/v1/dashboard/pvm-diary/status` route. Deploy the updated backend and staff dashboard before publishing a tester rollout.
