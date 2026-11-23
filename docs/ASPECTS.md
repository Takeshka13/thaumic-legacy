# Aspect discovery

Thaumic Legacy registers 52 classic aspects. The six primals are Aer, Aqua, Ignis, Terra, Ordo, and Perditio. The 46 compounds each have two parents. The base graph uses the Thaumcraft 6 formulas for all 37 aspects declared in its public API. It adds 15 identities with their Thaumcraft 4 formulas. See the [version matrix](ASPECT_VERSION_MATRIX.md) for all IDs, formulas, and historical sources.

## Assignment provenance

The bundled data describes 44 blocks, 33 items, and 21 entity types. The [assignment file](../src/main/resources/data/thaumiclegacy/thaumiclegacy/assignments/base.json) lists the complete specimen inventory and the current amount for each aspect.

- **Historically verified.** The identity and formula rows in the version matrix come from the original public API declarations. Exact historical assignments and amounts for this collection of specimens remain unverified in a version-pinned standalone reference.
- **Adapted profiles.** The classic concepts are applied to modern registry entries for familiar materials and roles. Stone carries Terra and Ordo, torches carry Ignis and Lux, logs carry Herba and Arbor, iron carries Metallum, and books carry Cognitio. Timing, crafting, harvesting, protection, decay, and other familiar concepts follow specimen function. All quantities were tuned for this mod. Vanilla copper also belongs to this group because the modern registry entries represent a material familiar from classic Thaumcraft.
- **New profiles.** Deepslate, mud, moss block, lantern, campfire, the bell, lodestone, amethyst blocks and shards, tinted glass, glow ink sacs, honey bottles, recovery compasses, bees, foxes, glow squids, salmon, turtles, and phantoms received profiles based on their modern behavior. The item data gives honey bottles Alkimia for their restorative use, for example, and the creature data gives bees Venenum for their sting. Names in these profiles are historical. The combinations of specimen, aspect, and quantity are Thaumic Legacy design.

Observation evidence is available in the bundled profiles for all 52 aspects. Common materials cover the primal foundation before rarer specimens contribute compounds. Stone, torch, feather, charcoal, and a water bucket provide a short route to all six primals, and the same group can reveal Lux.

## Thaumometer

- Observe an assigned block or creature by aiming at it and using the lens. A dropped item uses the same profile as its held item. To examine a held specimen, place the lens in the main hand and the specimen in the offhand, then crouch and use the lens.
- The server identifies the target and verifies reach and sight. The journal records each target kind and registry ID once per player. Standing and wall torches share a block record.
- An examined profile provides evidence for each of its aspects. Evidence reveals a primary immediately and reveals a compound when both parents are known. A new specimen can complete earlier evidence. Scan order leaves the final set of known aspects unchanged, and duplicate scans add zero records or discoveries.
- Open the Research Journal to see studied specimens and quantities. The index shows known compounds and their formulas. An unknown glyph and label conceal the identities of unrevealed aspects. Two distinct observations unlock the first research.

## Data and compatibility

- Aspect files live under `data/<namespace>/thaumiclegacy/aspects/`. Their `aspects` arrays define fully qualified `id` and `parents` entries, `color` in `#RRGGBB` format, and a `glyph` style index from 0 to 51. The parent count determines whether an aspect is primal or compound.
- Assignment files live under `data/<namespace>/thaumiclegacy/assignments/`. The `assignments` arrays contain `kind` (`block`, `item`, or `entity`), a registry ID in `target`, and positive integer quantities for 1 to 16 aspect IDs per target.
- The highest-priority resource at a given file path supplies that file. Entries in different effective files must have distinct aspect IDs or target pairs. The server validates both tables and checks that every aspect's ancestry terminates at primals before applying a catalog on reload.
- Discovery records live in a separate Overworld data file indexed by player UUID. Version 1 retains the existing specimen IDs and copies unversioned knowledge to the per-player `archived_known` set. The old Ember aspect represented fire and maps directly to Ignis. The remaining old builtin IDs have differing meanings, parent formulas, or specimen roles and stay in the archive. The server restores classic knowledge silently by replaying retained specimen evidence. This retains the first research and gives each observation credit once.
- The initial observation file keeps its existing format. When upgrading an earlier world, retain both files. The journal uses current material profiles to show quantities for preserved observations.
- The journal sends a bounded server snapshot when used. Use the same mod build on the client and server for aspect discovery.

## Checking in Minecraft

1. Run `gradlew.bat runClient --args="--username AspectTester"` and create a disposable world. Use the same username and world after restarting.
2. With commands enabled, run `/give @s thaumiclegacy:thaumometer` and `/give @s thaumiclegacy:research_journal`. Open the journal and confirm that it starts empty on a fresh player.
3. Scan stone and a torch. Check Terra and Ordo for stone, Ignis and an unknown entry for the torch, and the first research unlock. Scan the same torch again and check that the history and counts stay unchanged.
4. Scan a dropped feather. It exposes Aer and unlocks the earlier torch's Lux evidence. Scan a feather again in the offhand while crouching. Both item actions must share one record.
5. Observe an oak log and a sheep. Examine charcoal and a water bucket in the offhand to complete the six primals. Observe an oak sapling to supply Victus evidence. Reopen the oak-log entry to check Herba and Arbor with their quantities.
6. Switch between English and Russian and scroll the journal at two GUI scales. Restart Minecraft and verify that the discoveries, records, and first research are still present. On a dedicated server, repeat with two separate player UUIDs and compare each player's journal after reconnecting and restarting the server.
7. Test an upgrade with a disposable copy of a world saved on the preceding mod build. Record its first research unlock and specimen history before opening the copy. On the upgraded build, verify both are retained, open the journal again after a clean restart, and check that no records or rewards duplicate.

## Verification

Gradle build, package validation, and all nine Fabric GameTests passed on Java 21. The tests discovered all 52 aspects in both scan orders, distinguished Mortuus and Venenum through their separate specimen evidence, and covered legacy save migration and repeated loading.

The client reopened a migrated world copy with eight observations, eight discoveries, and the first research unlocked. Both journal tabs and scrolling were checked in English at GUI Scale 2 and in Russian at Auto, which selected scale 3 for the test window. The dedicated-server startup and clean shutdown passed. The [two-real-client check](FIELD_OBSERVATION_TESTING.md#dedicated-server-with-two-players) remains open.

## Future aura nodes

Nodes require a persisted regional aura model, dimension ownership, and an energy transfer policy. A later phase can define node profiles by size, capacity, stability, and aspect composition. Scanning also needs a stable specimen key. Movement of a node requires an ownership transfer that preserves stored charge and prevents duplication. Discovery currently supports the three registry-backed target kinds.
