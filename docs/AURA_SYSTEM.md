# Aura, Vis, and Flux

## Historical reference

| Generation | Aura model |
| --- | --- |
| Thaumcraft 4 | Aura nodes held aspect-specific energy. Players drew Vis into wands. The TC5 author notes describe their move away from direct node-to-wand charging. [TC5 5.0.0 release notes](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2264542) |
| Thaumcraft 5 | Nodes became entities that fed the surrounding aura, and held wands drew Vis from the field. Arcane crafting still used aspect-specific Vis costs. Unused material in a crucible and inefficient essentia processing could pollute the field. [TC5 5.0.0 release notes](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2264542), [TC5 crafting API](https://github.com/Azanor/thaumcraft-5-api/blob/master/ThaumcraftApi.java) |
| Thaumcraft 6 | The TC6 beta notes describe one raw Vis balance per chunk, natural replenishment, Flux competing for aura capacity, removal of nodes, and arcane crafting drawing directly from the aura. The public API exposes Vis, Flux, and a baseline at a world position, as well as simulated drains and pollution. Arcane recipes expose a Vis cost and separate crystal requirements. [TC6 6.0.BETA2 release notes](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2523647), [Aura API](https://github.com/Azanor/thaumcraft-api/blob/master/aura/AuraHelper.java), [Arcane crafting API](https://github.com/Azanor/thaumcraft-api/blob/master/crafting/IArcaneRecipe.java) |

The author's beta localization repository describes a device for reading the local aura and a separate device for extending the workbench's reach. The 6.1.BETA26 release notes identify the 1.12.2 reference build. The public contracts and release notes establish the gameplay direction. Exact BETA26 initialization, regeneration, redistribution, and pollution rates remain unverified. [TC6 beta reference text](https://github.com/Azanor/thaumcraft-beta/blob/master/nl_NL.lang), [TC6 6.1.BETA26 release notes](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2629023)

## Thaumic Legacy foundation

The aura belongs to a chunk in a specific dimension. Players in the same chunk share its Vis and Flux. Adjacent chunks and other dimensions have independent balances. Vis is a scalar energy resource. The Aspect Catalog still describes the properties of specimens and their prerequisites. Flux is local pollution that slows or stops Vis regeneration.

An untouched chunk starts at 100 of 100 Vis and zero Flux. Both balances remain between zero and 100. Thaumic Legacy uses the following recovery rates.

| Flux | Vis recovered |
| --- | --- |
| 0–24 | 1 per 100 ticks |
| 25–49 | 1 per 200 ticks |
| 50–74 | 1 per 400 ticks |
| 75–100 | Recovery blocked |

Flux naturally drops by one per 1,200 game ticks while positive. At 20 TPS that takes one minute. These are original balance values for the foundation. Existing Vis can still be spent in a polluted chunk. Pollution affects recovery and does not immediately remove stored Vis. This first model uses a fixed capacity and local, tick-based recovery. Biome or lunar effects, inter-chunk transfer, nodes, alchemy, taint, and rifts require later gameplay systems.

### State and persistence

- The server owns reads, spending, restoration, and pollution. A failed or zero-cost mutation leaves the balances unchanged. Vis spending is all-or-nothing and checks availability at the time of the action.
- A versioned SavedData record per dimension uses the ID `thaumiclegacy_aura`. The Overworld file is `<world>/data/thaumiclegacy_aura.dat`. The Nether and End use their own dimension data directories. The saved rows retain the packed chunk coordinates, balances, game time, and recovery remainders. State is kept sparsely for changed chunks. Existing chunks with no aura record begin at the same full, clean defaults as new chunks.
- Recovery uses elapsed world game ticks. Unloaded chunks catch up on access without loading their neighbors. Time while the server is stopped does not advance the aura. Recovery applies the rate for each crossed Flux band, preserving fractional recovery progress while the balance is below its cap.

### Player measurement

Craft the Thaumometer from two copper ingots, two glass panes, and one amethyst shard. Put copper above and below the center slot, amethyst in the center, and glass on each side of it. Use the instrument to read the current chunk's Vis, capacity, Flux, and recovery state. Measurement costs zero Vis.

Operators can inspect and exercise the balances through the `/thaumiclegacy aura` commands. This phase establishes the measurement loop and the server contract for future crafting or spell costs. An ordinary crafting recipe produces the instrument.

## Future TC4-inspired nodes

Model a node as a world object with its own position, size, stability, and capacity for individual aspects. Define node-to-chunk transfer as an explicit server operation before introducing a node that can charge the scalar Vis ledger. Require loaded participants for transfers and keep node energy and regional Vis separately inspectable. Portable nodes need persistent identity and a transfer rule that prevents a node from being duplicated or credited both as a placed object and as an item. The TC6-style area ledger remains usable without nodes.

## Future interface and balance

- Keep snapshots readable without color and add optional sound or world feedback when measurements change.
- Give later instruments more depth through comparative readings, an optional held-item gauge, or research on local conditions.
- Rebalance capacity and recovery after a workshop can spend Vis. Explore biome or lunar variation and neighbor exchange through explicit sources and sinks. Use multiplayer measurements before introducing active chunk processing.
- Add gameplay sources of Flux and a player-facing cleanup loop before introducing rifts or ecological consequences.
