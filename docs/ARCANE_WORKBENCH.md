# Arcane Workbench

The Arcane Workbench uses the aura of the chunk containing the block. A recipe can require shaped item ingredients, Vis, and a research milestone. Build the station at an ordinary crafting table, then use it to turn materials into arcane components and a field instrument.

In Thaumcraft 6 the workbench draws scalar Vis from the local aura. The published recipe interface exposes a Vis cost, a research key, and separate crystal requirements. Thaumic Legacy's recipes use its own materials, costs, progression, and interface. This first station has a 3×3 ingredient grid. Dedicated aspect crystal inputs remain part of a later content phase. [Author's TC6 aura and crafting notes](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2523647), [TC6 Arcane Recipe interface](https://github.com/Azanor/thaumcraft-api/blob/master/crafting/IArcaneRecipe.java).

The original journal describes obtaining the workbench by applying Salis Mundus to a crafting table. Its research and crafting text covers prerequisite knowledge, Vis cost, and crystal inputs. The direct shaped recipe below gives Thaumic Legacy a route using currently available materials. [Thaumcraft beta journal and interface text](https://github.com/Azanor/thaumcraft-beta/blob/master/en_us.lang).

## Arcane Foundations

Research requires the first observation milestone and knowledge of Lux. Scan stone and a torch with the Field Lens to complete the first milestone. Scan a feather to learn Aer. Together with the torch's Ignis and Lux evidence, Aer unlocks Lux and Arcane Foundations. The research uses discoveries already saved for that player. It adds zero fields to the world save. The journal reports the same server-side milestone that gates recipes.

## Recipes

Dots represent empty slots. Patterns can be positioned in the grid like other shaped recipes.

### Arcane Workbench

Craft this recipe in a vanilla crafting table. Obtaining a crafting table reveals the recipe in the vanilla recipe book.

```text
.C.
GTG
.S.
```

`C` is a copper ingot, `G` is a glass pane, `T` is a crafting table, and `S` is stone.

### Resonant Copper

Costs 8 Vis. Available immediately.

```text
R
C
```

`R` is redstone dust and `C` is a copper ingot.

### Attuned Lens

Costs 16 Vis. Requires Arcane Foundations.

```text
GAG
.R.
```

`G` is a glass pane, `A` is an amethyst shard, and `R` is Resonant Copper.

### Aura Surveyor

Costs 60 Vis. Requires Arcane Foundations.

```text
.L.
RTP
```

`L` is an Attuned Lens, `R` is Resonant Copper, `T` is a Thaumometer, and `P` is a copper ingot. Crafting the Surveyor and both arcane components from scratch spends 92 Vis. Right-click with the Surveyor to print the Vis, capacity, and Flux of the current chunk and the four cardinal neighbors. The server reads the aura ledger without force-loading chunks. A reading costs zero Vis.

## Recipe data

Compatible packs can add recipes in `data/<namespace>/recipe/` with the type `thaumiclegacy:arcane_workbench`. The file path supplies the unique recipe ID. Shaped recipes use a vanilla `pattern` and `key`, a `result` item stack, and integer `vis_cost` from 1 to 100. The optional `research` field takes a namespaced research ID. The shipped gate is `thaumiclegacy:arcane_foundations`. Bundled examples are under [recipe data](../src/main/resources/data/thaumiclegacy/recipe).

## Crafting and persistence

The menu shows a read-only preview, the Vis cost and balance, and a localizable status. Press Craft to place one recipe result in the player's inventory. The server ties each request to the open menu, live block entity, and current preview. It rechecks research and Vis, plans the output and any ingredient remainders, then spends Vis and applies the inventory plan on the server thread. Stale attempts and replays cannot repeat the purchase. Shared-grid crafting and dimension-local AuraSavedData both use the station's position. Local Flux affects the rate at which spent Vis returns. See the [aura rules](AURA_SYSTEM.md) for recovery rates.

The workbench stores its input stacks in the block entity. Closing a menu preserves the grid. Replacing the block drops the stored stacks. Clean server shutdown saves chunk inventories, player inventory, aura, and research through Minecraft's normal persistence. An abrupt process or power failure can interrupt these separate saves.

Future work includes recipe discovery in the journal, dedicated aspect ingredients, visual and audio feedback, and balance tuning with a larger set of recipes. Use matching client and server builds for the updated research and workbench screens.
