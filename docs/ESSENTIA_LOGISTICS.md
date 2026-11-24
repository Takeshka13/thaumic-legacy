# Essentia Logistics and Thaumonomicon

## Progression and equipment

Open Alchemy Foundations and brew Lumen Glass yourself in a Crucible. The server records the successful brew for the player who threw the catalyst. This unlocks Essentia Distillation. Older saves retain their existing research. Any player whose past brew predates this milestone can record it by brewing again.

| Equipment | Crafting pattern, top to bottom | Cost |
| --- | --- | ---: |
| Alchemical Furnace | Resonant Copper in the top center. Furnace between two Resonant Copper in the middle. Bricks in the bottom center. | 18 Vis |
| Arcane Alembic | Copper ingots in the top corners. Lumen Glass in the center. Resonant Copper in the bottom center. | 16 Vis |
| Warded Jar | Oak slab in the top center. Glass panes in the middle outer slots. Lumen Glass between two panes in the bottom row. | 10 Vis |
| Essentia Phial | Glass bottle and copper ingot in any positions at a normal crafting table. | No Vis |

Place one Arcane Alembic directly above the Alchemical Furnace. Use a profiled item on the furnace to supply the charge. Sneak and use a vanilla fuel item to supply fuel. The furnace obtains aspect amounts from the active item profile or the block profile for a block item. A batch distills in 100 active ticks and transfers its complete aspect mixture into the alembic if it fits. The furnace waits when the output cannot accept the mixture. Coal and charcoal work as fuel. Sneak and use the furnace with an empty hand to recover pending items or their containers. Ordinary machine drops preserve the inventory, storage, and processing state when placed again.

Use an empty Essentia Phial on the alembic to extract up to eight units of one aspect. Use a filled phial on a Warded Jar to deposit its contents. A jar holds one aspect at a time, so use separate jars to sort a mixed batch. Alembics and jars each hold up to 64 units. Empty phials can also draw from jars. A partly accepted deposit leaves the remainder in the phial. Thaumometer use and worn Goggles of Revealing show the state of these machines. The amounts use the standard aspect IDs and colors.

### Historical reference and adaptations

Thaumcraft 6 used a fuel-fired Essentia Smelter, an oven-top Arcane Alembic, portable phials, and Warded Jars. Thaumic Legacy starts with one multi-aspect alembic, a reusable phial, and exact conversion of the bundled profiles. Stacked alembics, purification losses, labels, and automatic transport are deferred. [Azanor's published Thaumcraft beta journal](https://raw.githubusercontent.com/Azanor/thaumcraft-beta/master/en_us.lang#L1395-L1409)

## Research data

The existing Research Journal item opens the Thaumonomicon. Its category views sit alongside the original observations and aspect index. The server validates and sorts a prerequisite graph, computes each player's unlocked entries, and excludes hidden entries and undisclosed prerequisite names from the client payload. Empty categories reserve navigation space for future content.

Place data bundles under `data/<namespace>/thaumiclegacy/research/`. Categories have `id`, `title` (translation key), and `order`. The optional `.short` translation gives a compact tab label. Entries have `id`, `category`, `translation` (prefix for `.locked.title`, `.locked.body`, `.unlocked.title`, and `.unlocked.body`), `gate` (an implemented server milestone or `always`), optional `parents`, and optional `hidden`. A hidden entry appears once all parents are unlocked. Missing or duplicate IDs, missing parents or categories, unknown gates, and cycles reject the reload. Existing research unlock rules and saved discovery IDs remain valid.

## Essentia transport

Tubes, valves, and filters move individual aspects from alembics into jars over bounded loaded routes. Manual transfer remains useful for setup, sorting, and recovery. Pressure simulation and buffer devices remain future work. [Essentia Transport Foundation](ESSENTIA_TRANSPORT.md)

## Verification

- Build on Java 21 with `gradlew.bat build`, then run `gradlew.bat runFieldObservationTests`. The local combined suite passed all 35 tests, including failed transfers, legacy discoveries, and retained block contents.
- In a client copy of an existing world, distill one torch. The alembic holds Ignis 2 and Lux 2. Transfer the Ignis to a jar and check both blocks with the Thaumometer and goggles. Open the Thaumonomicon and check the category, observation, and aspect views.
- On a disposable dedicated server, leave a torch and coal queued without an alembic and a separate jar holding Ignis 7. After a clean restart both queued items and the jar's amount persist. Adding the alembic then processes the torch into Ignis 2 and Lux 2. Two real clients on the same dedicated server remain to be tested.
