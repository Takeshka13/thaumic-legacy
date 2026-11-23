# Alchemy testing

Use Java 21, Minecraft 1.21.1, and the configured Fabric versions. Test upgrades in a disposable copy of an existing world.

```powershell
.\gradlew.bat build
.\gradlew.bat runFieldObservationTests
.\gradlew.bat runClient
```

The GameTest command runs the combined suite. The XML report is in `build/field-observation-tests/report.xml`.

## Survival walkthrough

1. Use the Thaumometer to observe stone, a torch, and a feather. Observe a water bucket in the offhand while sneaking with the Thaumometer in the main hand to learn Aqua. Open the journal and confirm that Alchemy Foundations is available. A known Aqua source such as a clay block also works.
2. In the Arcane Workbench, place Resonant Copper above a vanilla cauldron and craft the Crucible for 12 Vis. For testing only, the item ID is `thaumiclegacy:crucible`.
3. Place the Crucible and use a water bucket on it. Confirm the filled basin is visible and an empty bucket remains. Check that a dropped charcoal stays undissolved in a cold vessel, and collect it before heating. Place a magma block directly underneath. Wait about five seconds at 20 TPS.
4. Stand close and aim down at the interior. Drop two charcoal into the opening with Q. Inspect with an empty hand or the Thaumometer. Confirm 4 Ignis and 2 Perditio. Drop redstone dust in, pick up the Ember Salt that emerges above the rim, and use it as furnace fuel.
5. Drop in a torch and an empty glass bottle. Verify Lux 2 and Vitreus 2, then drop a plain glass block as the catalyst. Pick up two Lumen Glass blocks. Inspect the remaining Ignis 2 and Vacuos 1. Place a Lumen Glass block to check its light and appearance.
6. Measure the current chunk with the Thaumometer, then crouch and use an empty hand on the Crucible. The basin empties and the three units of remaining Essentia add one Flux in an unsaturated chunk. Refill, reheat, and dissolve two ink sacs and a quartz. Drop sugar to make Cleansing Salt. With the salt held in a polluted chunk, right-click into the air to remove up to eight Flux.
7. Try a glass catalyst with only Lux in the mixture. If there is capacity, the glass dissolves into Vitreus and produces no result. Try a locked recipe, a cold basin, and an over-capacity drop. The blocked item stays recoverable. Restart with a wet Crucible holding multiple aspects and inspect it after reloading its chunk.
8. Compare the Crucible states, readout, error messages, and journal in English and Russian. Check fixture blocks with a cold basin, a heating basin and a ready basin.

## Dedicated multiplayer and restart

1. Use a test world and two matching modded clients. Open Alchemy Foundations only on one player. Prepare enough mixture for one recipe and drop stacks of the same catalyst from both players. The unqualified player's catalyst must remain recoverable. With both players qualified and one recipe's worth of Essentia, only one result can be produced.
2. Try rapid repeat use, then remove the heat source before another attempt. Confirm the resulting cold refusal preserves resources. Break the basin with remaining mixture and check that the Crucible item drops and contamination is applied once.
3. Leave another loaded basin filled with a mixture, cleanly stop the dedicated server, and restart it. Reload the basin's chunk and inspect water, mixture and heating state. Confirm the previous pollution is still tracked by the local aura.

## Diagnosis on the previous interaction model

The previous client produced two Lumen Glass when the player crouched and right-clicked with plain glass. A control run on the same version held the correct catalyst and had unlocked research, a ready Crucible, Lux 2, and Vitreus 2. Ordinary right-click chose dissolution, consumed the glass, and left Lux 2 and Vitreus 5 with no result. Dropped catalysts now go through recipe selection first.

## Local runtime verification

- The Minecraft client loaded a copy of a world with existing research. The Thaumometer learned Aqua from a water bucket, ordinary client drop packets added the torch, bottle, and glass, and two Lumen Glass blocks appeared above the basin. Purging the remaining Ignis 2 and Vacuos 1 added one Flux.
- The Crucible reading was visually checked with the Thaumometer and worn goggles in Russian at Auto and in English at GUI Scale 2.
- The dedicated server reloaded a ready Crucible with Terra 12 and Ordo 2 after a clean restart. Breaking it raised local Flux from 0 to 2. The two-real-client scenario remains open.
