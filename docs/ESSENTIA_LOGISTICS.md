# Essentia Logistics and Thaumonomicon

## Progression and equipment

Open Alchemy Foundations and brew Lumen Glass yourself in a Crucible. The server records the successful brew for the player who threw the catalyst. This unlocks Essentia Distillation. Older saves retain their existing research. Any player whose past brew predates this milestone can record it by brewing again.

| Equipment | Crafting pattern, top to bottom | Cost |
| --- | --- | ---: |
| Alchemical Furnace | Resonant Copper in the top center. Furnace between two Resonant Copper in the middle. Bricks in the bottom center. | 18 Vis |
| Arcane Alembic | Copper ingots in the top corners. Lumen Glass in the center. Resonant Copper in the bottom center. | 16 Vis |
| Warded Jar | Oak slab in the top center. Glass panes in the middle outer slots. Lumen Glass between two panes in the bottom row. | 10 Vis |
| Essentia Phial | Glass bottle and copper ingot in any positions at a normal crafting table. | No Vis |

Open the Alchemical Furnace with right-click. Place a profiled item in the upper slot and vanilla fuel in the lower slot. Shift-click loads fuel or feed from the player inventory and retrieves stacks from the machine. The work and fuel gauges track server state. The left panel lists buffered essentia with glyphs and amounts, with scrolling for longer mixtures. Unlearned aspects are masked. The two return slots collect containers left by recipes or fuel.

A batch distills in 100 active ticks into a shared internal buffer of 256 units. If the entire batch cannot fit, the input stays in its slot. The furnace distributes the buffer across up to four stacked Arcane Alembics. Each alembic accepts a single aspect at a time and can change aspect when drained. Blocked output remains in the furnace buffer. One Warded Jar holds one aspect. Alembics and jars each hold up to 64 units.

An empty Essentia Phial withdraws up to eight units from the visible alembic or jar stock. A filled phial deposits into compatible alembics or jars. A partly accepted deposit leaves the remainder in the phial. The Thaumometer and Goggles of Revealing show the visible stock. Older mixed alembics keep their remaining aspects in a persistent reserve that counts toward capacity. Draining the active aspect exposes the next reserved aspect. Furnace slots, fuel, progress, and buffered essentia persist on clean shutdown and normal retained block drops.

### Historical basis

TC4 distillation combines a fuel-fired furnace and stacked single-aspect alembics. Thaumic Legacy uses server-side exact profile quantities and a bounded, inspectable furnace buffer. TC6 also describes a fuel-fired Essentia Smelter, an oven-top alembic, phials, and Warded Jars. [TC4 distillation reference](https://thaumcraft-4.fandom.com/wiki/Essentia_Distillation) · [Azanor's published Thaumcraft beta journal](https://raw.githubusercontent.com/Azanor/thaumcraft-beta/master/en_us.lang#L1395-L1409)

## Research data

The existing Research Journal item opens the Thaumonomicon. Its category views sit alongside a personal aspect index. The observation records remain in the save and supply the known-source lists in the index. The server validates and sorts a prerequisite graph, computes each player's unlocked entries, and excludes hidden entries and undisclosed prerequisite names from the client payload. Empty categories reserve navigation space for future content.

Place data bundles under `data/<namespace>/thaumiclegacy/research/`. Categories have `id`, `title` (translation key), and `order`. The optional `.short` translation gives a compact tab label. Entries have `id`, `category`, `translation` (prefix for `.locked.title`, `.locked.body`, `.unlocked.title`, and `.unlocked.body`), `gate` (an implemented server milestone or `always`), optional `parents`, and optional `hidden`. A hidden entry appears once all parents are unlocked. Missing or duplicate IDs, missing parents or categories, unknown gates, and cycles reject the reload. Existing research unlock rules and saved discovery IDs remain valid.

## Essentia transport

Tubes, valves, and filters move individual aspects from alembics into jars over bounded loaded routes. Manual transfer remains useful for setup, sorting, and recovery. Pressure simulation and buffer devices remain future work. [Essentia Transport Foundation](ESSENTIA_TRANSPORT.md)

## Verification

- Run the Java 21 build and GameTest with "gradlew.bat build runFieldObservationTests".
- In a disposable world, put a torch and coal in the furnace GUI and place two alembics above it. Verify that one gets Ignis 2 and the other Lux 2. Remove the second alembic and distill another torch. Check that incompatible stock stays in the furnace buffer.
- Restart a disposable world with queued feed, fuel, and buffered essentia. Check the machine after restart, then restore its outputs. An old mixed alembic should expose each reserved aspect in turn as the active one is drained.
- Scan exposed water and lava with the Thaumometer. Check Aqua and Ignis in the lens preview after scanning. In the Thaumonomicon, hover an aspect and scroll its known-source list. A second player should have their own list.
- For interaction follow-up, phial transfer still uses direct use feedback, filter configuration uses in-world selection, and Thaumatorium controls are separate from the furnace menu.
