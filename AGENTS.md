# Thaumic Legacy

## Team

Ender Dragon is the primary Codex agent and the user's point of contact. It owns task scope, file assignments, integration, verification, and the final report.

| Agent | Codex name | Profile | Responsibilities |
| --- | --- | --- | --- |
| Iron Golem | `iron_golem` | `.codex/agents/iron-golem.toml` | Fabric, architecture, public API, and addon compatibility |
| Witch | `witch` | `.codex/agents/witch.toml` | Magical systems, alchemy, research, recipes, and game balance |
| Allay | `allay` | `.codex/agents/allay.toml` | Client interfaces, textures, models, localization, and visual effects |
| Warden | `warden` | `.codex/agents/warden.toml` | Testing, code review, performance, and compatibility |

## Project rules

- Target Minecraft 1.21.1, Fabric, and Java 21. Change dependency versions only in a dedicated task.
- Keep the client and dedicated server runnable. Keep client-only classes and initialization out of common execution paths.
- Write code, comments, and technical documentation in English. Communicate with the user in Russian. Maintain English (`en_us`) and Russian (`ru_ru`) in-game localization.
- Create original code, textures, and other assets. Never copy Thaumcraft source code, textures, or protected resources.
- Support independent Fabric addons through an explicitly documented, versioned public API. Consider backward compatibility for API behavior, content IDs, and persisted data. Obtain agreement on the API architecture before creating public interfaces or registration stubs.
- Keep the core mod free of Applied Energistics 2 (AE2) and Create dependencies. Plan external integrations as separate addons.
- Preserve existing user changes. Inspect Git status and the relevant diff before editing. Get the user's permission before any Git push.

## Delegation and verification

- Give each delegated task a purpose, file scope, deliverable, and read-only or write assignment. Agents may edit only the files explicitly assigned for writing. Assign one writer per file at a time. Ender Dragon coordinates any handoff before another agent edits that file.
- Use the named profiles for specialized delegation when the client exposes them. Queue a second wave when available slots are full. If a profile cannot be launched, report it as unavailable.
- Run heavy Gradle jobs serially across the team. For runtime changes, choose relevant checks and cover client and dedicated-server startup when the changed path can affect either.
- Verify actual command exit codes and relevant output. Review the resulting Git diff, including new files, and report checks that failed, were blocked, or were skipped.

## Project references

- [Roadmap](docs/ROADMAP.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Addon API design](docs/ADDON_API.md)
- [Decision log](docs/DECISIONS.md)
