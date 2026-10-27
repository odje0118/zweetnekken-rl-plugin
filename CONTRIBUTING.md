# Contributing to Zweetnekken

## Local development

### Running locally

Run `gradlew.bat clean test build` to run the automated regression tests and package the plugin, or `gradlew.bat run` to launch RuneLite in developer mode. The development launcher lives in `src/dev`.

The production API origin is `https://api-production-81001.up.railway.app`, served by the Zweetnekken backend. The plugin's API origin is centralized in `ZweetnekkenApiClient.ZWEETNEKKEN_API_ORIGIN`; update that constant for local development.

The API routes and request/response responsibilities are documented in [docs/api-contract.md](docs/api-contract.md).

Evidence and caches are stored under RuneLite's plugin data directory (`.runelite/plugin-data/zweetnekken`). Existing data in the legacy `.runelite/zweetnekken` directory is migrated automatically by RuneLite. Authentication codes are stored only in RuneLite configuration and are never written into evidence metadata or logs.
