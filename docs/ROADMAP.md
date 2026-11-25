# Development Roadmap

Thaumic Legacy is an independent magic mod for Minecraft 1.21.1, Fabric, and Java 21. Thaumcraft 6 on Minecraft 1.12.2 is a historical reference for the depth of discovery, experimentation, environmental consequences, and workshop automation. Thaumic Legacy defines its own rules, progression, content, interface, and assets. Release material identifies it as an independent project.

## Milestones

| Stage | Status | Depends on | Completion gate |
| --- | --- | --- | --- |
| 1. Project Foundation | Complete locally | Existing Fabric scaffold | Build and launch both environments on Java 21. Verify mod loading and a clean dedicated-server shutdown. |
| 2. Field Observation | Complete locally. Two-client verification pending. | 1 | Craft a Thaumometer and Research Journal, observe three vanilla blocks, unlock the first research entry, and retain progress after restart. Verify that two real clients on a dedicated server see independent progress before closing multiplayer QA. |
| 3. Aura and Magical Crafting | Aura & Flux Foundation and Arcane Workbench complete and verified locally. Two-client QA and balance tuning remain open. | 2 | Three shaped recipes spend local Vis. Arcane Foundations gates advanced recipes. Save and reload preserve the workbench grid, research, and aura ledger. |
| 3a. Aura Nodes and Wand Charging | Foundation implemented. Two-client QA and balance tuning remain open. | 2, 3 | Find a naturally generated node, study it, charge a wand without moving chunk Vis, and verify node and journal state across reload. |
| 4. Experiments and Alchemy | Alchemy Foundation complete and verified locally. | 2, 3 | Players unlock Alchemy Foundations, craft a Crucible, extract Essentia, and use catalysts. The mixture survives reloads and discarded residue affects local Flux. |
| 5a. Essentia Logistics and Thaumonomicon | Manual distillation and categorized research foundation implemented. Two-client QA remains open. | 4 | Brew Lumen Glass to unlock an Alchemical Furnace, Arcane Alembic, and Warded Jar. Distill and transfer by phial. The Thaumonomicon preserves existing discoveries and displays available research by category. |
| 5b. Essentia Transport | Bounded routing foundation implemented. Pressure simulation and multiplayer QA remain open. | 5a | Tubes, valves, and filters route typed essentia from alembics to jars. Blocked or unloaded paths preserve stored material. |
| 5c. Advanced Alchemy | Centrifuge, Thaumatorium, jar labels, and improved distillation implemented. | 5a, 5b | Split compound essentia, retain labels, complete gated recipes through typed ports, and distribute whole batches over stacked alembics. [Advanced Alchemy](ADVANCED_ALCHEMY.md) |
| 6. Rituals and Spellcraft | Planned | 3, 4. 5 supplies optional automation. | An original multiblock completes a ritual or fails in a readable and recoverable way. One implement casts two mechanically distinct effects. |
| 7. Exploration and Ecology | Planned | 3, 4. Content design can start earlier. | New exploration sites, creatures, and local anomaly recovery form a repeatable loop. Existing saves retain an attainable progression route. |
| 7a. Ancient Knowledge and the Crimson Cult | Planned | 2, 7. Dimension and boss design also needs encounter and progression rules. | Rare sites yield Thaumometer observations and unlock Ancient Knowledge. Later encounters, dimensions, and bosses follow explicit access and recovery rules. |
| 8. Golems and Advanced Workshop | Planned | 5, 6. Optional roles can use 7. | Two construct jobs run within owner and navigation budgets, stop safely when blocked, and resume after restart. Workshops gain useful late-game upgrades. |
| 9. Testing and Public Release | Planned | Completed features in the agreed release scope | Verify dedicated multiplayer, save upgrades, reloads, performance, access to progression, original assets, and both locales. Report verified environments and known limitations. |

Testing starts at each milestone. Agree on measurable tick, network, and entity budgets before scaling up simulations. The first playable slice uses vanilla blocks so world generation, aura simulation, and custom recipes can proceed independently.

## Deferred work

Public API compatibility remains an architectural option. A public API release, separate addons, and bridges for AE2, Create, and other mods have no scheduled milestone. The [Addon API proposal](ADDON_API.md) remains a reference and confers no implementation commitment. See the [Feature Matrix](FEATURE_MATRIX.md) and [Gameplay Systems](GAMEPLAY_SYSTEMS.md) for the active scopes.

## Planned multiblock structures

- [**Infernal Furnace**](INFERNAL_FURNACE.md) forms a transformed shell with wand activation, fuel-free smelting, bellows, optional Ignis transport, and recoverable teardown. Automation and balance remain future work.
- **Infusion Altar** centers on a **Runic Matrix** above an **Arcane Pedestal**, with surrounding **Arcane Pedestals** for ingredients. It depends on reliable stored essentia. Its design needs typed essentia consumption during a ritual, visible stability and instability, and recoverable failure consequences such as displaced ingredients or local Flux.
- **Golem Press** follows infusion and component crafting. Assembly consumes its inputs once, with a clear recovery path if the structure or power supply is interrupted.

## Planned ancient sites and encounters

- Generate rare **Eldritch Obelisks**, **Eldritch Altars**, and **Eldritch Rings** in new terrain. Use seed-based placement and spacing, and preserve existing terrain. Give each site a distinct Thaumometer observation.
- Use those observations to unlock relevant entries in the Thaumonomicon's **Ancient Knowledge** branch. Gate forbidden research by prerequisites and make its personal **Warp** consequences visible to the player.
- Add **Crimson Cultists**, **Crimson Clerics**, **Crimson Knights**, and **Eldritch Guardians** as distinct encounters with clear roles and controlled spawn rates. Plan ancient dimensions and bosses after the site and research loop is playable.

Thaumcraft 4, 5, and 6 are historical references for this milestone. Check each feature against its original version during design. [Historical references and acceptance criteria](FEATURE_MATRIX.md#planned-ancient-knowledge)

## Project Foundation verification

- Local build passed on JDK 21 with `gradlew.bat build`.
- The Minecraft 1.21.1 client loaded Thaumic Legacy and closed cleanly.
- The dedicated Fabric server loaded Thaumic Legacy, reported `Done (4.928s)!`, shut down cleanly, and saved all dimensions.

The GitHub Actions workflow selects JDK 21. Verification on a hosted GitHub runner is pending.

## Field Observation verification

- The user confirmed that all nine manual gameplay checks passed.
- Local build, client restart, dedicated-server startup and shutdown, and server GameTests passed. The GameTests verify separate progress for two simulated players.
- The two-real-client dedicated-server scenario remains open, including reconnect and checking each player's journal after a server restart. [QA procedure](FIELD_OBSERVATION_TESTING.md#dedicated-server-with-two-players)

### Aspect Discovery extension

The Thaumometer and Research Journal use 52 classic aspects across 98 bundled specimen profiles. The Thaumcraft 6 API supplies the baseline, with compatible historical extensions from Thaumcraft 4. Discovery uses evidence and compound prerequisites. Versioned per-player storage preserves earlier discoveries and observations. See [Aspect Discovery](ASPECTS.md) and [Classic Magical Tools](CLASSIC_TOOLS.md).

### Aura and Flux foundation

The foundation is functional and locally verified. The server stores one independent aura cell per dimension and chunk. Vis recovers as world ticks pass, Flux slows recovery, and the craftable Thaumometer reports a point-in-time reading. The Arcane Workbench spends Vis from this ledger. Tune capacity, recovery, Flux, and recipe costs against the crafting loop during playtesting. See [Aura System](AURA_SYSTEM.md) and [Aura Testing](AURA_TESTING.md).

### Arcane Workbench

The first magical crafting loop connects Thaumometer observations, Lux discovery, the Research Journal, and local Vis. A persistent shared grid supports recipes for components and researched equipment. The Thaumometer handles aura comparison directly. Local verification covers exact Vis charges, rejection of replayed requests, and preservation of the grid and aura after a dedicated-server restart. The two-real-client scenario remains open. See [Arcane Workbench](ARCANE_WORKBENCH.md) and [Arcane Workbench Testing](ARCANE_WORKBENCH_TESTING.md).

### Alchemy Foundation

Alchemy Foundations connects the existing research graph and Aqua discovery to Crucible construction through the Arcane Workbench. The heated Crucible accepts dropped items and checks each catalyst against the mixture before dissolving it. It ejects the crafted result into the world. Draining or breaking it with residue adds local Flux. See [Alchemy](ALCHEMY.md) and [Alchemy Testing](ALCHEMY_TESTING.md).

### Classic Gameplay Alignment

Implemented and verified locally with a Java 21 build and all 26 GameTest passing at that milestone. The Thaumometer replaces the Field Lens in new progression and uses the same saved discoveries. The heated Crucible processes dropped reagents and catalysts. Worn Goggles of Revealing show the Crucible mixture and reveal nearby heat and light sources in view. The starter Wand persists six independent primal Vis reserves. The two-real-client scenario remains open. [Classic Magical Tools](CLASSIC_TOOLS.md) · [Alchemy Testing](ALCHEMY_TESTING.md)

### Aura Nodes and Wand Charging Foundation

Normal, Bright, and Unstable nodes appear in fresh Overworld chunks. The Thaumometer records individual nodes in the Research Journal. Worn Goggles of Revealing list nearby loaded nodes. The Wand siphons primal Vis from studied nodes, whose reserves recover independently of the local chunk ledger. [Aura Nodes](AURA_NODES.md) · [Aura Node Testing](AURA_NODES_TESTING.md)

### Essentia Logistics and Thaumonomicon

The Alchemical Furnace processes a profiled input into an Arcane Alembic. Reusable phials transfer individual aspects into Warded Jars. The Thaumonomicon keeps existing observations and the aspect index and adds eight data-driven research categories. [Progression, recipes, and transport design](ESSENTIA_LOGISTICS.md)

### Essentia Transport Foundation

Tube networks find compatible storage without buffering essentia in transit. Valves block routes and filters select a known aspect. Players study storage, discover Vitreus, and place a tube to unlock flow controls. [Transport rules, history, and verification](ESSENTIA_TRANSPORT.md)
