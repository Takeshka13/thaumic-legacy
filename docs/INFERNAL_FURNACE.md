# Infernal Furnace

## Build and operate

Open **Infernal Furnace** under **Magical Artifice** in the Thaumonomicon after completing **Arcane Foundations** and **Wand Charging**. The furnace is built from ordinary blocks. Its front can face any horizontal direction.

Each layer is viewed from above with the front at the bottom.

| Layer | Back | Middle | Front |
| --- | --- | --- | --- |
| Top | N N N | N . N | N N N |
| Middle | O O O | O L O | O B O |
| Base | N N N | N N N | N N N |

**N** is a nether brick block, **O** is obsidian, **L** is a lava source, **B** is iron bars, and **.** is air.

1. Charge the wand at aura nodes and right-click the outward face of the bars with at least **12 Ignis and 12 Terra**. The basic wand holds 25 of each. A failed activation consumes no charge.
2. Right-click the controller holding a smeltable item. The furnace accepts one input stack type at a time and uses the regular Minecraft smelting recipe manager. Right-click with empty hands to collect the finished stacks. Sneak and right-click with empty hands to recover unprocessed input.
3. Every fourth raw iron smelt gives an extra iron nugget. Raw gold has an independent counter and gives a gold nugget every fourth smelt. Normal output and bonus each have a stack buffer. Clear the buffers if a product does not fit.
4. Break any part to extinguish the furnace. The surviving shell reverts to its original materials. The controller ejects pending input, finished items, and byproducts once. Broken brick and obsidian parts use their respective drops. The broken sealed lava cell drops nothing. Reloading a partially unloaded shell resumes the same batch once the shell is intact and loaded.

## Bellows and Ignis

Discover **Arcane Bellows** after **Infernal Furnace**. Place them against the outside of the middle layer on its left, right, and rear center blocks, and below the center of the base. These four positions are two blocks from the lava cell. The separate Ignis inlet is on the outward face of the top-layer block directly above the front grate. Lead a tube from the top of an Ignis jar to this face. Rotate the locations with the front. All four bellows and the inlet can operate together.

The furnace is fuel-free. For a 200-tick vanilla recipe, these are the calculated processing times with the default settings. The Ignis column assumes a continuous supply when needed. Delivery intervals can add ticks in practice.

| Bellows | Without Ignis | With Ignis |
| --- | ---: | ---: |
| 0 | 134 ticks | 100 ticks |
| 1 | 100 ticks | 80 ticks |
| 2 | 89 ticks | 73 ticks |
| 3 | 85 ticks | 70 ticks |
| 4 | 83 ticks | 69 ticks |

The furnace starts at 150 work units per tick, with 100 units required for each tick in the underlying recipe. The four bellows add 50, 25, 12, and 6 units respectively. Ignis adds 50 while beneficial. One Ignis supplies 40 accelerated processing ticks. The inlet accepts Ignis from the transport network during valid smelting and keeps one reserve unit. Stored boost pauses when the furnace is blocked or idle. Inspect the grate with an empty hand to check its status, bellows count, Ignis reserve, and boost time. The chunk aura is unchanged.

Configure the rate with Java system properties under `thaumiclegacy.infernalFurnace.`: `baseRate`, `bellowsRate`, `ignisRate`, `ignisTicks`, and `maxRate`. The default maximum is 500 work units per tick. Values are clamped server-side.

The controller saves its facing, buffers, recipe, work, and Ignis state. The shell uses linked parts with their original material encoded in block states. Existing controllers migrate on their next loaded tick with a valid shell, retaining their saved buffers and proportional progress. The controller checks the shell without forcing any chunks to load.

## Historical reference and design choices

| Version | Historical mechanism | Sources |
| --- | --- | --- |
| Thaumcraft 4 | Community references describe a constructed nether brick and obsidian furnace ignited with Ignis and Terra from a wand. They describe fuel-free smelting, extra products and speed aids including Ignis centivis. The author's changelog rules out placing bellows on the output side. | [TC4 furnace reference](https://thaumcraft-4.fandom.com/wiki/Infernal_Furnace), [TC4 release notes](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2216015) |
| Thaumcraft 5 | The version-specific community reference describes wand activation at 50 Ignis and 50 Terra and fuel-free smelting. That charge exceeds the capacity of the basic Legacy wand. | [TC5 furnace reference](https://thaumcraft-5.fandom.com/wiki/Infernal_Furnace) |
| Thaumcraft 6 | The community reference describes Salis Mundus activation and local Vis assistance. The author's beta notes describe bellows and how input waits when the furnace is full. | [TC6 furnace reference](https://ftb.fandom.com/wiki/Infernal_Furnace_(Thaumcraft_6)), [TC6 beta release notes](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2558310/dependencies) |

Thaumic Legacy uses wand ignition, optional Ignis essentia, and a defined multiblock teardown rule. Input and output use manual interaction with the front controller. Hopper automation and smelting XP remain open.
