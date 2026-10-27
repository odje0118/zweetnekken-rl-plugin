# Zweetnekken Plugin Maintenance Plan

1. Keep Java packages, RuneLite configuration identifiers, plugin metadata, and UI branding consistent with Zweetnekken.
2. Keep the plugin integrated with the Zweetnekken backend for linking, clan membership, points, competitions, loot submissions, and personal-best data.
3. Preserve applicable third-party license texts and source notices when reusing or adapting open-source components.
4. Run the Gradle build and a RuneLite smoke test before publishing changes.

## Security

- Never embed a Discord bot token in the plugin.
- Treat plugin-supplied RSN, evidence, and event payloads as untrusted.
- Authorize every player and staff operation server-side.
- Preserve BSD and third-party copyright/license notices.
