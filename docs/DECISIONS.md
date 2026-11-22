# Decision Log

## Accepted project constraints

| ID | Decision | Consequence |
| --- | --- | --- |
| D-001 | Target Minecraft 1.21.1, Fabric, and Java 21. | Dependency version changes require a dedicated task. Client and dedicated-server startup are release checks. |
| D-002 | Retain the option of a future versioned public API. | Supersedes the earlier requirement to develop the addon API first. Use explicit internal boundaries and stable content IDs. Public contracts and separate addons are deferred. |
| D-003 | Keep the core independent of AE2, Create, and other gameplay mods. | Supersedes the plan to schedule separate bridges. All external integrations are deferred indefinitely. |
| D-004 | Produce original code and assets. Maintain English and Russian in-game localization. | Review asset provenance and translation key coverage when adding content. |
| D-005 | Use JDK 21 in CI and for local build and runtime verification. | The CI workflow selects JDK 21, local builds run with JDK 21, and Java compilation uses release 21. |
| D-006 | Prioritize interconnected magic gameplay. | Study Thaumcraft 6 for historical context. Design original mechanics, recipes, progression, text, artwork, models, audio, and presentation. Describe Thaumic Legacy publicly as an independent project. |
| D-007 | Start with one complete field observation loop. | Use existing vanilla targets to test data, persistent player knowledge, synchronization, and journal UX before building resource simulation. |

## Project Foundation verification

The local Windows build passed on JDK 21 with Gradle 9.8.2. The Minecraft 1.21.1 client loaded the mod and closed cleanly. The dedicated-server log confirms Minecraft 1.21.1, Fabric Loader 0.20.1, Java 21, and Thaumic Legacy 1.0.0. The server reached `Done (4.928s)!`, shut down cleanly, and saved the Overworld, Nether, and End.

The workflow specifies JDK 21. Its execution on GitHub remains unverified.

## Decisions pending

| ID | Question | Needed before |
| --- | --- | --- |
| P-006 | Which field observation definitions are stable enough to persist across reload, and how are retired IDs migrated? | Field Observation |
| P-007 | What cell scale, tick budget, and charge/residue recovery behavior produce useful choices without persistent depletion traps? | Aura and Magical Crafting |
| P-008 | What ownership rules and crash-recovery checkpoints apply to workshop inputs shared across chunks? | Workshop Logistics and Rituals |
| P-009 | What world-damage and accessibility defaults should apply on multiplayer servers? | Exploration and Ecology |

Record accepted decisions with their rationale and consequences. When replacing a decision, record which entry it supersedes.
