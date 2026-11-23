# Crucible alchemy

The crucible converts item profiles into a local reservoir of aspect units. A valid catalyst and the required units produce an item. The stored aspects belong to the block. Each player's research governs their own ability to craft at a shared crucible.

## Historical reference

The Thaumcraft 6 journal describes treating a cauldron with Salis Mundus, filling it with water, heating it, and throwing in ingredients followed by a catalyst. It also describes consuming some water during crafting and Essentia becoming Flux when released to the aura. The public recipe contract specifies the catalyst, aspect quantities, result, and research key. Thaumic Legacy uses an Arcane Workbench recipe, explicit held-item interactions, and persistent water and leftovers. Its materials, values, and residue disposal rules are original adaptations. [TC6 Crucible Recipe API](https://github.com/Azanor/thaumcraft-api/blob/master/crafting/CrucibleRecipe.java), [Azanor's beta journal text](https://raw.githubusercontent.com/Azanor/thaumcraft-beta/master/en_us.lang).

## Alchemy Foundations

Unlock [Arcane Foundations](ARCANE_WORKBENCH.md#arcane-foundations) by observing stone, a torch, and a feather. Scan a clay block or scan a water bucket held in the offhand to learn Aqua. This unlocks Alchemy Foundations, which appears in the Research Journal. The milestone derives from saved observations and aspect knowledge.

At the Arcane Workbench, place a Resonant Copper above a vanilla cauldron. Craft the Crucible for 12 Vis. Making the Resonant Copper costs another 8 Vis. The Crucible recipe and all three bundled alchemy recipes require Alchemy Foundations.

## Operation

1. Place the crucible directly above fire, soul fire, lava, a magma block, or a lit campfire. Use a water bucket on the empty crucible. It takes 100 consecutive game ticks of heat to become ready with the default settings.
2. Check the basin with an empty hand, the Thaumometer, or worn Goggles of Revealing. Heat loss resets the warmup, while the water and stock stay in the crucible.
3. Aim at the basin opening and drop ingredients with Q. Items must physically land inside the mouth. The hot basin takes one unit from a stack at a time. The item aspect profile takes priority, with the block profile as a fallback for placeable items.
4. Drop the catalyst into the mixture. The server checks known recipes and the thrower's research before dissolving the item. A match with enough Essentia spends the listed amounts and spits out the result above the rim. An authorized catalyst with insufficient Essentia can dissolve using its normal profile. Locked catalysts stay as items. Water and unused Essentia remain in the basin.
5. Crouch and right-click with an empty hand to drain the basin. A crouching empty-bucket interaction can recover the water. Disposing of residue through either control or by breaking the Crucible adds one Flux per eight units, rounding up and respecting the aura cap. Inspect local Flux with the Thaumometer and use Cleansing Salt to remove up to eight Flux.

The default reservoir holds 64 aspect units. An addition that would exceed capacity preserves the whole incoming unit. The server bounds intake and shares its cooldown across players. Crafted output entities stay outside the intake logic until picked up. This keeps products from dissolving in the same vessel while they are still bouncing over it.

## Products

| Product | Ingredients to dissolve | Catalyst | Required aspects | Use |
| --- | --- | --- | --- | --- |
| Ember Salt ×1 | Two charcoal | Redstone dust | Ignis 4, Perditio 2 | Burns for 4,800 ticks when used as furnace fuel. |
| Lumen Glass ×2 | One torch and one empty glass bottle | Glass block | Lux 2, Vitreus 2 | Placeable luminous glass. The recipe leaves Ignis 2 and Vacuos 1 available for later use. |
| Cleansing Salt ×1 | One clay block and one Nether quartz, or two ink sacs and one Nether quartz | Sugar | Aqua 2, Ordo 1 | Use to remove up to eight Flux from your current chunk. In survival, each successful use consumes one salt. Using it where no Flux can be removed preserves the item. |

The recipes use [assigned aspect quantities](../src/main/resources/data/thaumiclegacy/thaumiclegacy/assignments/base.json). Dissolving ingredients uses the full profile, including any extra aspects listed in the assignments.

## Recipe data

Recipe JSON files live under `data/<namespace>/recipe/` and use the type `thaumiclegacy:alchemy`. Each recipe specifies a vanilla ingredient in `catalyst`, a nonempty map of namespaced aspect IDs to positive integer amounts in `aspects`, and a `result` item stack. The optional `research` accepts a known namespaced research ID. Each bundled recipe uses `thaumiclegacy:alchemy_foundations`. See the [bundled recipes](../src/main/resources/data/thaumiclegacy/recipe).

The bundled `data/thaumiclegacy/thaumiclegacy/alchemy/config.json` supplies `capacity`, `heat_ticks`, `heat_sources`, and `flux_units_per_point`. Lower capacity settings preserve previously saved contents and prevent more additions until the amount falls below the limit.

The Crucible block state records water and the visible phase. Its block entity records heat progress and the amount for each aspect ID. Unloaded chunks retain their last state and resume heating when ticking again. Clean shutdown uses normal Minecraft persistence. Research uses the existing save format, and the journal requires matching client and server builds. Separate storage and automation can build on these rules after defining which player or device owns a craft and how outputs are accepted.

## Known limitations

- Inputs enter as dropped items. Research-gated catalysts need an attributable, live player thrower. Automated transfer and passive crafting without an actor remain future work.
- A Crucible holds one bucket of water and one mixed reservoir. Successful crafts keep the water. Residue stays in the vessel until it is used, drained, or released when the block is broken. Distillation, portable Essentia storage, and pipes are future work.
- Clean dedicated-server restart and simulated shared use were verified. Testing two real clients on the same Crucible and recovery after an abrupt shutdown remain open.
