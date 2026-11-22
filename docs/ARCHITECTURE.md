# Architecture

## Repository baseline

Thaumic Legacy starts from a Fabric scaffold targeting Minecraft 1.21.1 and Java 21. Loom separates the common `src/main` and client `src/client` source sets. `ThaumicLegacy` and `ThaumicLegacyClient` are the current common and client entrypoints. Gameplay systems and addon contracts are in design.

## Proposed boundaries

| Area | Ownership and dependencies |
| --- | --- |
| Common bootstrap | Initialize Fabric content and common services. Keep initialization phases explicit and all classes reachable on dedicated servers safe to load there. |
| Server gameplay | Own energy, research progress, recipe and ritual execution, and other authoritative state. Validate player requests and decide which state clients may observe. |
| Data and persistence | Use namespaced IDs for references across systems. Design data-driven content around explicit validation and reload semantics. Give persisted formats a migration policy before depending on them in released worlds. |
| Client presentation | Own screens, rendering, models, and visual effects. Present synchronized state and localized feedback. Maintain `en_us` and `ru_ru` assets. |
| Public addon contracts | Expose reviewed extension points with documented behavior and compatibility guarantees. Keep implementation details and client-only dependencies outside common contracts. |
| Optional integration addons | Depend on Thaumic Legacy and the relevant external mod. Own their adapters, target-specific dependencies, and compatibility tests. |

The proposed dependency direction is independent addons → public contracts → supported core behavior. The core owns validation and lookup. The server owns the authoritative state, and the client observes the portion it needs through explicit synchronization. Energy accounting and research progression should remain independently testable, with alchemy and rituals consuming their approved contracts.

## Verification implications

- Check client and dedicated-server startup when changing bootstrap, registration, networking, or code reachable from both environments.
- Exercise save/load, migration, and reconnect when changing persistent or synchronized state.
- Test the core by itself and with an independent addon when changing addon contracts. Exercise optional bridges in their own mod combinations.

See [Addon API design](ADDON_API.md) for the proposed registration flow and [Decision log](DECISIONS.md) for open architecture questions.
