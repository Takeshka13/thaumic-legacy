# Aspect discovery

Thaumic Legacy registers 48 original aspects. Grain, Ember, Current, Vapor, Pulse, and Lattice are primary. The other 42 each name two parents. The bundled profiles describe 44 blocks, 33 items, and 21 creature types. All amounts and parent relationships in these files are Thaumic Legacy game data. See [historical terminology and version notes](ASPECT_HISTORY.md) for Thaumcraft 4, 5, and 6.

## Field Lens

- Observe an assigned block or creature by aiming at it and using the lens. A dropped item uses the same profile as its held item. To examine a held specimen, place the lens in the main hand and the specimen in the offhand, then crouch and use the lens.
- The server identifies the target and verifies the reach and sight line. The journal records each target kind and registry ID once per player. Standing and wall torches share a block record.
- An examined profile provides evidence for each of its aspects. A primary is learned when there is evidence for it. A compound is learned when there is evidence and both parents are known. New specimens can complete earlier evidence, so the scan order does not change the final set of learned aspects. Duplicate scans never add records or discoveries.
- Open the Research Journal to see studied specimens, profile quantities, and known aspects. The aspect index shows compound formulas when both parents are known. Unknown aspects have a placeholder glyph and label. The first research entry opens with two distinct observations.

## Data and compatibility

- Aspect files live under `data/<namespace>/thaumiclegacy/aspects/`. Their `aspects` arrays define `id`, `parents`, `color` as `#RRGGBB`, and `glyph` as a style index from 0 to 47.
- Assignment files live under `data/<namespace>/thaumiclegacy/assignments/`. The `assignments` arrays contain `kind` (`block`, `item`, or `entity`), a registry ID in `target`, and positive integer quantities for 1 to 16 aspects per target.
- The highest-priority resource at a given file path supplies that file. Entries in different effective files must have distinct aspect IDs or target pairs. The server checks both tables and the compound graph before applying a catalog on data reload.
- Discovery saves use a separate Overworld data file indexed by player UUID. The existing observation file keeps its original format. Old block observations are imported into the aspect journal when it is opened or the player scans. The process is safe to repeat. The current catalog defines quantities shown for each specimen.
- The journal sends a bounded server snapshot on use. Legacy block scanning and journal use stay in place. The extended snapshot uses a new packet identifier, so use the same mod build on the client and server for aspect discovery.

## Checking in Minecraft

1. Run `gradlew.bat runClient --args="--username AspectTester"` and create a disposable test world. Use the same username and world after restarting.
2. With commands enabled, run `/give @s thaumiclegacy:field_lens` and `/give @s thaumiclegacy:research_journal`. Open the journal and confirm it starts empty for a fresh player.
3. Scan stone and a torch. Check Grain and Ember and verify that the first journal entry opens. Observe an oak log and confirm the journal lists its aspect quantities, including unknown values. Scan the same target again and confirm the counts stay unchanged.
4. Observe a sheep or cow. Drop a feather, aim directly at it from outside pickup distance, and use the lens. Put a feather in the offhand and scan again while crouching. Both item actions should yield one shared item record.
5. With stone already recorded, scan stone bricks to learn Lattice. Reopen the stone entry and check that Flint is now identified. Switch between English and Russian, scroll the journal at two GUI scales, then restart and recheck discoveries.
6. For dedicated multiplayer, connect two clients with different UUIDs. Scan different specimens, inspect both journals, reconnect each player, and restart the server. Compare both sets of observations and discoveries after reconnecting.

The complete bundled catalog and source assignments are available in [aspect definitions](../src/main/resources/data/thaumiclegacy/thaumiclegacy/aspects/base.json) and [specimen profiles](../src/main/resources/data/thaumiclegacy/thaumiclegacy/assignments/base.json).

## Future aura nodes

Nodes require a persisted regional aura model, dimension ownership, and an energy transfer policy. A later phase can define node profiles by size, capacity, stability, and aspect composition. Scanning also needs a stable specimen key. Movement of a node requires an ownership transfer that preserves stored charge and prevents duplication. Discovery currently supports the three registry-backed target kinds.
