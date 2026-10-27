# Zweetnekken

RuneLite Plugin Hub project for Zweetnekken clan.

## Features

- Home panel with member ranks, points, next-rank progress, and collapsible BOTW/SOTW competition cards.
- Clan banner loaded from the Zweetnekken backend, so the image can be replaced without rebuilding the plugin.
- Event alerts delivered to game chat with an optional in-game overlay.
- Evidence screenshots always include the player's RSN, even when chat is closed. Enable **Bingo Overlay → Screenshot only** to include active Bingo details only in captured evidence; leave it off to keep the normal in-game Bingo overlay.
- Automatic full-client evidence capture for eligible loot, collection-log unlocks, pets, Combat Achievement tiers, and PBs.
- Durable local outbox and editable loot-submission drafts.
- Raid scoreboard or observed boss-interaction party estimates, including same-clan participant RSNs while excluding guest names.
- Live and bulk personal-best sync, including Adventure Log team-size records.
- Automatic first-connection and 30-minute sync for every Combat Achievement task and all skill levels/XP.
- Permanent Discord `/connect` code authentication using an `Authorization: Bearer` header.

## Data and privacy

The plugin communicates with the external Zweetnekken backend hosted on Railway. After you connect with a permanent link code, it sends the RuneScape display name and account identifier, eligible loot/event details, observed raid party/clan information, personal bests, skill levels/experience, and screenshots captured for eligible events. Screenshots may contain visible game UI and chat; the Hide private messages option is enabled by default. Event evidence may be forwarded to the Zweetnekken clan Discord for review. Only connect if you trust the Zweetnekken service and its operators.

## Local development

### Running locally

Run `gradlew.bat clean test build` to run the automated regression tests and package the plugin, or `gradlew.bat run` to launch RuneLite in developer mode. The development launcher lives in `src/dev`.

The production API origin is `https://api-production-81001.up.railway.app`, served by the Zweetnekken backend. The plugin's API origin is centralized in `ZweetnekkenApiClient.ZWEETNEKKEN_API_ORIGIN`; update that constant for local development.

The API routes and request/response responsibilities are documented in [docs/api-contract.md](docs/api-contract.md).

Evidence and caches are stored under `.runelite/zweetnekken`. Authentication codes are stored only in RuneLite configuration and are never written into evidence metadata or logs.
