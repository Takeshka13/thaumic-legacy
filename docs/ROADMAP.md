# Development Roadmap

This roadmap is provisional. Dependencies in the table indicate the contracts or behavior a stage needs. Testing begins in Project Foundation and continues throughout development.

| Stage | Depends on | Completion target |
| --- | --- | --- |
| 1. Project Foundation (complete) | Existing Fabric scaffold | Establish a reproducible build and verify Minecraft 1.21.1 client and dedicated-server startup. Confirm the build JDK and Java 21 runtime baseline. Establish conventions for original assets and English and Russian localization. |
| 2. Public API Foundation | 1 | Agree on API scope, registration lifecycle, namespaced IDs, and compatibility policy. Validate the proposed registration flow with a minimal independent Fabric addon after design approval. |
| 3. Magical Energy System | 1, 2 for any public extension points | Define server-authoritative energy units, limits, persistence, and synchronization. Verify generation, consumption, and save/load behavior with controlled sources. |
| 4. Research & Knowledge | 1, 2 for addon contributions, 3 for energy-related unlocks | Define knowledge ownership, persistent progress, prerequisite validation, and synchronized views. Research graph design can proceed while the energy implementation is in progress. |
| 5. Alchemy | 2, 3, 4 for progression-gated recipes | Add data-driven recipes with validated inputs, energy costs, and outputs. Verify recipe reload and failure handling. |
| 6. Rituals & Crafting | 2, 3, 4, 5 where alchemical components are required | Add crafting and ritual rules with server-side validation, documented costs, and consistent consumption when an operation succeeds or fails. |
| 7. World Generation | 1, 2 for addon contributions, 3 and 4 for the role of generated resources in progression | Introduce world resources and structures. Verify server generation, existing-world behavior, and availability of progression-critical materials. |
| 8. Entities & Equipment | 1, 3 and 4 for gameplay requirements, 5–7 where acquisition or behavior uses their content | Introduce entities and equipment with dedicated-server-safe behavior, client assets, and localized player feedback. |
| 9. Addon Ecosystem | 2 and the relevant features from 3–8 | Publish API usage and versioning guidance. Test an independent addon and evaluate AE2 and Create bridges separately against compatible Fabric releases. |
| 10. Testing & Public Release | 1–9 for the chosen release scope | Run client, server, save migration, addon compatibility, and performance checks. Publish supported versions, known issues, and release notes. |

## Project Foundation verification

- Local build passed on JDK 21 with `gradlew.bat build`.
- The Minecraft 1.21.1 client loaded Thaumic Legacy and closed cleanly.
- The dedicated Fabric server loaded Thaumic Legacy, reported `Done (4.928s)!`, shut down cleanly, and saved all dimensions.

The GitHub Actions workflow selects JDK 21. Verification on a hosted GitHub runner is pending.
