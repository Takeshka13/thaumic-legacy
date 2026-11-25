# Essentia Transport Foundation

## Historical reference

The published Thaumcraft 6 journal describes jars drawing compatible essentia, and stronger suction taking priority where unlike types compete. Valves interrupt flow, filters accept a chosen type, directional tubes constrain how suction propagates, and interference can vent gas. TC4 technical accounts describe a gradient that drops by one per tube, with nominal unlabeled and labeled jar strengths of 32 and 64. Those numeric values are documented for TC4 here. The TC6 author text confirms relative priority and interference without specifying the full numerical scheduler or tie-break rules. [Azanor's TC6 journal](https://raw.githubusercontent.com/Azanor/thaumcraft-beta/master/en_us.lang#L1436-L1446) · [TC4 technical reference](https://thaumcraft-4.fandom.com/wiki/Essentia) · [Contemporary discussion](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/1292130-thaumcraft-6-1-beta26-no-longer-being-developed?page=538)

## Thaumic Legacy rules

- An Arcane Alembic can supply a tube on any horizontal side or above. A Warded Jar accepts a tube from above and remains the endpoint. Phials still let players collect essentia from jars manually.
- Plain tubes connect in six directions. Valves and filters connect only along their placement axis. Right-click the valve with an empty hand to toggle it. The block state retains the setting.
- Use an empty hand on a filter to cycle through the aspects you have discovered. Sneak and use an empty hand to clear the selection. An unconfigured filter is closed to flow. The server checks personal research and the known aspect list. The selection is saved with the block entity.
- Each active source can move one unit per ten game ticks. Starting with a rotating aspect order, the server tries up to four aspects per poll. Each search queues at most 128 positions and traverses up to 32 pipe blocks. Type checks, valve orientation, and destination capacity apply before committing. The same unit is removed from the alembic and added to the jar in one server operation. The pipes never store intermediate essentia.
- In this foundation routes replace pressure simulation. Nearby compatible destinations win by path length and fixed traversal order. No suction strength, redstone control, buffer pipes, suction interference, or Flux from collisions is simulated. Unloaded or unreachable endpoints leave the essentia at the source.

Aim a Thaumometer or wear Goggles of Revealing to read physical connections, the selected filter aspect, and local or recently observed routing conditions. A recent passage indicates a single unit that already reached its destination. Stored quantities appear on the alembic and jar readouts.

## Research and recipes

| Research | Requirement | Crafting unlock |
| --- | --- | --- |
| Essentia Distillation | Alchemy Foundations and a successful personal Lumen Glass brew | Existing furnace, alembic and jar recipes |
| Warded Jars | Essentia Distillation and a successful manual deposit from a phial | Storage practice required for transport |
| Essentia Transport | Warded Jars and discovered Vitreus | Four tubes for 8 Vis |
| Valves and Filtering | Essentia Transport and personal placement of a plain tube | A valve for 6 Vis and a filter for 10 Vis |

For tubes, put three glass panes across the middle and Resonant Copper above and below the center pane. For a valve, put a lever above a tube with copper ingots beside it. For a filter, place a Thaumometer between two amethyst shards with a tube beneath it. Existing Warded Jar recipes and saved discoveries remain valid.

## Verification scenario

1. Distill a torch to obtain Ignis 2 and Lux 2. Put tubes between the alembic and the top of a jar. The jar accepts one type at a time and the unmatched stock stays in the alembic.
2. Add a straight valve. Close it before the transfer and verify neither quantity changes. Open it and watch the route resume.
3. Insert a filter aligned with the path. Discover the chosen aspect before configuring the filter. Verify that matching essentia reaches an empty jar and nonmatching essentia stays in the alembic.
4. Fill the destination to capacity, disconnect an arm, or unload a chunk crossed by the line. Confirm that the source preserves any units that cannot reach a compatible jar. Load the chunk again and verify that a valid route resumes.
5. Restart the world and inspect endpoint quantities, valve state, and filter selection. A second player must earn their own milestones before crafting and configuring devices.

## Local results

The Java 21 build and all 40 GameTest passed. The client opened an existing world copy, reported a closed valve with its connected faces, opened it through ordinary use, and displayed the received Ignis in the goggles readout.

The dedicated-server fixture restored a closed valve and an Ignis filter after restart. With the valve opened and the alembic still loaded, transfer failed while the destination chunk was unavailable. Its stock remained Ignis 2 and Lux 3. Loading the destination again delivered exactly Ignis 2 and preserved Lux 3 at the source. The two-real-client scenario remains open. Player isolation and configuration gates are covered with simulated players.
