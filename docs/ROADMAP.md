# Development Roadmap

Thaumic Legacy is an independent magic mod for Minecraft 1.21.1, Fabric, and Java 21. Thaumcraft 6 on Minecraft 1.12.2 is a historical reference for the depth of discovery, experimentation, environmental consequences, and workshop automation. Thaumic Legacy defines its own rules, progression, content, interface, and assets. Release material identifies it as an independent project.

## Milestones

| Stage | Status | Depends on | Completion gate |
| --- | --- | --- | --- |
| 1. Project Foundation | Complete locally | Existing Fabric scaffold | Build and launch both environments on Java 21. Verify mod loading and a clean dedicated-server shutdown. |
| 2. Field Observation | Complete locally. Two-client verification pending. | 1 | Craft a Field Lens and Research Journal, observe three vanilla blocks, unlock the first research entry, and retain progress after restart. Verify that two real clients on a dedicated server see independent progress before closing multiplayer QA. |
| 3. Aura and Magical Crafting | Planned | 2 | A workshop consumes finite local charge for one useful recipe. Players can read regional charge and residue. Save/reload and two simultaneous consumers preserve the resource ledger. |
| 4. Experiments and Alchemy | Planned | 2, 3 | Field evidence and a small lab experiment unlock a useful alchemical product. Distillation, capacity limits, manual transfer, and waste are observable. |
| 5. Workshop Logistics | Planned | 4 | Filters, valves, and buffers supply two consumers on one bounded network. Backpressure, unavailable endpoints, and reload preserve stored material. |
| 6. Rituals and Spellcraft | Planned | 3, 4. 5 supplies optional automation. | An original multiblock completes a ritual or fails in a readable and recoverable way. One implement casts two mechanically distinct effects. |
| 7. Exploration and Ecology | Planned | 3, 4. Content design can start earlier. | New exploration sites, creatures, and local anomaly recovery form a repeatable loop. Existing saves retain an attainable progression route. |
| 8. Golems and Advanced Workshop | Planned | 5, 6. Optional roles can use 7. | Two construct jobs run within owner and navigation budgets, stop safely when blocked, and resume after restart. Workshops gain useful late-game upgrades. |
| 9. Testing and Public Release | Planned | Completed features in the agreed release scope | Verify dedicated multiplayer, save upgrades, reloads, performance, access to progression, original assets, and both locales. Report verified environments and known limitations. |

Testing starts at each milestone. Agree on measurable tick, network, and entity budgets before scaling up simulations. The first playable slice uses vanilla blocks so world generation, aura simulation, and custom recipes can proceed independently.

## Deferred work

Public API compatibility remains an architectural option. A public API release, separate addons, and bridges for AE2, Create, and other mods have no scheduled milestone. The [Addon API proposal](ADDON_API.md) remains a reference and confers no implementation commitment. See the [Feature Matrix](FEATURE_MATRIX.md) and [Gameplay Systems](GAMEPLAY_SYSTEMS.md) for the active scopes.

## Project Foundation verification

- Local build passed on JDK 21 with `gradlew.bat build`.
- The Minecraft 1.21.1 client loaded Thaumic Legacy and closed cleanly.
- The dedicated Fabric server loaded Thaumic Legacy, reported `Done (4.928s)!`, shut down cleanly, and saved all dimensions.

The GitHub Actions workflow selects JDK 21. Verification on a hosted GitHub runner is pending.

## Field Observation verification

- The user confirmed that all nine manual gameplay checks passed.
- Local build, client restart, dedicated-server startup and shutdown, and server GameTests passed. The GameTests verify separate progress for two simulated players.
- The two-real-client dedicated-server scenario remains open, including reconnect and checking each player's journal after a server restart. [QA procedure](FIELD_OBSERVATION_TESTING.md#dedicated-server-with-two-players)
