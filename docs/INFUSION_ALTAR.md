# Infusion Altar

Thaumcraft 4 places a Runic Matrix above a central pedestal, surrounded by four pillars and ingredient pedestals. Opposing placements stabilize the ritual. Thaumic Legacy uses a symmetric layout with fixed pedestal positions, a two-block matrix offset, a small one-time Wand Vis cost, and non-destructive ritual failures. These dimensions, resource costs and risk values are Thaumic Legacy adaptations. See the [TC4 developer demonstration](https://www.youtube.com/watch?v=A4ycaVzypwk), [altar reference](https://thaumcraft-4.fandom.com/wiki/Infusion_Altar) and [community mechanics reference](https://thaumcraft-4.fandom.com/wiki/Infusion_mechanics).

## Building

At pedestal height, lay out the following positions relative to the central pedestal. Leave room below for the floor and around the ring for jars and candles.

```text
        x  -3 -2 -1  0 +1 +2 +3
z -3        .  .  .  P  .  .  .
z -2        .  O  .  .  .  O  .
z -1        .  .  B  .  B  .  .
z  0        P  .  .  C  .  .  P
z +1        .  .  B  .  B  .  .
z +2        .  O  .  .  .  O  .
z +3        .  .  .  P  .  .  .

C  Central Arcane Pedestal
P  Required Arcane Pedestal
O  Optional Arcane Pedestal
B  Arcane Stone Bricks, capped by one Arcane Stone on the next layer
.  Unspecified
```

Leave the block directly above C empty. Place the Runic Matrix two blocks above C. The layout is rotationally symmetric. The Wand changes the matrix into its formed, glowing state after checking the structure and the user's Infusion Foundations research. Formation consumes 5 Terra and 5 Ordo **Wand Vis**. Local chunk Vis does not power the ritual.

## First ritual

1. Follow the Warded Jars and Wand Charging research branches. Once both are unlocked, find Infusion Foundations under Infusion in the Thaumonomicon. Charge your Wand from aura nodes until it holds at least 5 Terra and 5 Ordo.
2. Craft the blocks, build the layout, and right-click the matrix with the Wand to form the altar.
3. Distill stone bricks through an Alchemical Furnace and Arcane Alembic. The assigned profile produces 3 Terra and 2 Ordo per stone bricks item. Collect at least 2 of each in separate Warded Jars. Leave the space immediately above each nearby jar empty. Nearby jars can be up to four blocks away on each horizontal axis and from pedestal height to matrix height.
4. Right-click the central pedestal with Chiseled Stone Bricks. Place an Iron Ingot and a Resonant Copper on opposing outer pedestals. One item is placed per click from the held stack.
5. Right-click the formed matrix with the Wand. Use the Thaumometer to inspect the matrix, or look at it with Goggles of Revealing equipped. The altar takes one unit every 10 ticks while supplied. Once all four units are absorbed, the ingredients are consumed together, and one Lodestone appears on the central pedestal. Right-click the pedestal with an empty hand to collect it. Any part of a result that does not fit in the inventory stays on the pedestal.

Pipe routes can also supply the matrix from any horizontal side. Filters and closed valves affect routed jars. An uncovered jar within direct range can still be drawn from independently of its pipe connections. To make a nearby jar available only through the network, put its extraction tube directly above it. The tube covers the jar for the direct-draw rule. The altar searches only loaded chunks. If a supply disappears mid-ritual, restore a valid route to resume.

| Recipe | Center | Peripheral components | Essentia | Base instability | Result |
| --- | --- | --- | --- | --- | --- |
| `thaumiclegacy:infused_lodestone` | Chiseled Stone Bricks | Iron Ingot, Resonant Copper | 2 Terra, 2 Ordo | 1 | Lodestone |
| `thaumiclegacy:infused_ender_chest` | Obsidian | 4 Obsidian, Ender Pearl, Lumen Glass | 2 Alienis, 2 Vacuos | 3 | Ender Chest |

For the second recipe, use three opposing pairs, including one of the optional diagonal pairs. A distilled Ender Pearl supplies 2 Alienis and each distilled Glass Bottle supplies 1 Vacuos.

## Recipe format

Use data pack files under `data/<namespace>/recipe/` with type `thaumiclegacy:infusion`. The path supplies the unique recipe ID. See the [Lodestone example](../src/main/resources/data/thaumiclegacy/recipe/infused_lodestone.json).

| Field | Meaning |
| --- | --- |
| `central` | One Minecraft ingredient. |
| `components` | An unordered list of 1–8 Minecraft ingredients. Repeated ingredients require individual pedestals. Extra occupied pedestals prevent a match. |
| `aspects` | Registered aspect IDs mapped to positive integers. Maximum 64 total units. |
| `research` | Required research ID, checked against the player who starts the ritual. |
| `instability` | Integer base instability from 0 to 12. |
| `result` | Minecraft item stack with `id` and optional `count` or `components`. |

## Instability and recovery

Each unmatched component position adds 2 instability points. Each occupied opposing pair subtracts 1. A pair of candles beyond opposing pedestal positions at pedestal height subtracts 1 more. Place candles at distance 4 on a cardinal axis or at offsets (±3, ±3) for diagonal pairs. Candle color and lit state do not affect the calculation. The score bottoms out at zero. Each absorbed unit rolls a mishap chance capped at 25%. The default multiplier is 2 percentage points per instability point. Adjust `infusionRiskPerPoint` and `infusionAccidentFlux` with integer values from 0 to 10. The default mishap releases 1 Flux into the matrix's chunk.

A mishap aborts the run and attempts to eject one peripheral component as a collectible item. Other inputs stay in place. The ritual owner can cancel with sneak-right-click and a Wand. Changing a ritual input cancels the run, and breaking a required block unforms the matrix. Breaking a pedestal drops its item. Spent essentia is never refunded in these cases. Remaining essentia stays in its original jars. No ingredients are consumed before successful completion, aside from the documented recoverable ejection.

The server persists the active operation and pedestal contents, including item revisions and absorbed amounts. An unloaded part of the altar pauses progress until it becomes available. Existing worlds require no migration. Clean saves preserve the operation; an abrupt process failure while Minecraft is saving different chunks is outside the transaction guarantee. The first version uses action-bar readings and item rendering. It leaves ritual-specific animation and expanded stabilization for later.
