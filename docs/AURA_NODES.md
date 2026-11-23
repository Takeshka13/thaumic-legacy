# Aura nodes and wand charging

## Historical reference

Thaumcraft 4 made naturally generated nodes a local source of wand Vis. Players could see them more clearly with a Thaumometer or Goggles of Revealing and siphon primal Vis by holding use with a wand. Nodes could contain compound aspects too. Historical descriptions distinguish node types such as Pure and Hungry from modifiers such as Bright and Pale, which affected recovery. Completely draining an aspect could permanently damage its reserve. The Node Preserver research left a reserve on supported wands. The official release identifies the version. The player references document the mechanics. [Azanor's 4.2.3.5 release](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2227552) · [Contemporary TC4 guide](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/mods-discussion/1406639-thaumcraft-4-guide-azanor-approved-first-update) · [Feed The Beast TC4 node reference](https://ftb.fandom.com/wiki/Aura_Node_(Thaumcraft_4)) · [TC4 node variants](https://thaumcraft-4.fandom.com/wiki/Aura_Node)

## Thaumic Legacy rules

A node is a stationary block entity. Its chunk identifies the dimension and position. Its UUID, capacities, available Vis, and six recovery clocks persist in chunk NBT. The block state preserves the variant. All nodes have independently generated capacities for Aer, Aqua, Ignis, Terra, Ordo, and Perditio. The following ranges and rates are Thaumic Legacy balance values.

| Variant | Default generation weight | Capacity per primal | Ticks per point per primal |
| --- | ---: | ---: | ---: |
| Normal | 75 | 6–12 | 1,200 (60 seconds at 20 TPS) |
| Bright | 15 | 12–20 | 600 (30 seconds at 20 TPS) |
| Unstable | 10 | 4–12 | 1,800 (90 seconds at 20 TPS) |

Recovery is calculated on demand from elapsed game ticks and caps at each primal's capacity. Unloaded chunks recover while the world runs. Stopping the server pauses the game clock. A depleted primal cannot supply the wand until it recovers. Depletion leaves node capacity intact. Unstable nodes recover slowly and share the same safe drain rules.

## Generation and configuration

The placed feature tries one candidate per 48 newly generated Overworld chunks on average. Placement selects a position inside the chunk at the motion-blocking surface, skips occupied positions, and requires solid dry support. Seed and position determine generated identity and reserves. Existing generated chunks stay intact, and reloading a chunk loads its saved node. Already generated worlds gain nodes as players explore fresh chunks.

Override `data/thaumiclegacy/worldgen/placed_feature/aura_node.json` in a data pack to change the rarity. The configured feature at `data/thaumiclegacy/worldgen/configured_feature/aura_node.json` controls the variant weights. Apply worldgen settings at world load and explore fresh terrain. Generation is restricted to the Overworld. Stored nodes and their local regeneration already operate per dimension.

## Discovery and use

Aim the Thaumometer at a prism to see its type, condition, and current/maximum Vis for all six primals. Use the instrument to study the node. The server records each node UUID once per player. The journal tracks Magical Nodes, Node Study, and Wand Charging. Comparing Specimens or a first node scan unlocks the introductory entry. The first scan completes Node Study and unlocks charging. A successful transfer records the Wand Charging milestone. Existing observation and classic-aspect saves keep their format.

Worn Goggles of Revealing list up to five loaded nodes within 24 blocks, including distance and coordinates. Aimed readouts show the six reserves. Observations are recorded when the player uses a Thaumometer. The faint world model is always present. The server looks up nodes in a loaded chunk index and never loads distant chunks for this display.

After studying a node, aim with the starter Wand and hold the use button. The server transfers one Vis every five use ticks, checks line of sight and reach, and deducts exactly what the wand can accept. Each wand reserve holds up to 25. Release, a lost target, or absence of usable charge stops transfer. The local chunk Vis and Flux ledger continues to power the Arcane Workbench independently. Node charging, depletion, and recovery affect only the six primal reserves.

Compound node aspects, destructive variants, jarring, stabilization, automated recharge, and energy conversion are outside this foundation.
