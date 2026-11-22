# Decision Log

## Accepted project constraints

| ID | Decision | Consequence |
| --- | --- | --- |
| D-001 | Target Minecraft 1.21.1, Fabric, and Java 21. | Dependency version changes require a dedicated task. Client and dedicated-server startup are release checks. |
| D-002 | Support independent addons through a versioned public API. | Public contracts require architecture approval before implementation. Compatibility includes behavior, published content IDs, and persisted data where applicable. |
| D-003 | Keep AE2 and Create dependencies outside the core mod. | Each integration is planned as a separate addon and owns its dependency and compatibility matrix. |
| D-004 | Produce original code and assets. Maintain English and Russian in-game localization. | Review asset provenance and translation key coverage when adding content. |
| D-005 | Use JDK 21 in CI and for local build and runtime verification. | The CI workflow selects JDK 21, local builds run with JDK 21, and Java compilation uses release 21. |

## Project Foundation verification

The local Windows build passed on JDK 21 with Gradle 9.8.2. The Minecraft 1.21.1 client loaded the mod and closed cleanly. The dedicated-server log confirms Minecraft 1.21.1, Fabric Loader 0.20.1, Java 21, and Thaumic Legacy 1.0.0. The server reached `Done (4.928s)!`, shut down cleanly, and saved the Overworld, Nether, and End.

The workflow specifies JDK 21. Its execution on GitHub remains unverified.

## Decisions pending

| ID | Question | Needed before |
| --- | --- | --- |
| P-002 | Which contract should the first independent addon exercise, and when are contributions discovered, validated, and made available? | Public registration implementation |
| P-003 | How are addon API versions published and mapped to core mod versions, and what is the deprecation window? | First supported public API release |
| P-004 | Which gameplay definition formats, reload rules, and save migration guarantees should be public? | Stabilizing data-driven addon contracts |
| P-005 | Which external Fabric releases and bridge features will AE2 and Create integrations support? | Integration implementation |

Record accepted decisions with their rationale and consequences. When replacing a decision, record which entry it supersedes.
