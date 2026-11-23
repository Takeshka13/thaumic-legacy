# Alchemy testing

Use Java 21, Minecraft 1.21.1, and the configured Fabric versions. Test upgrades in a disposable copy of an existing world.

```powershell
.\gradlew.bat build
.\gradlew.bat runFieldObservationTests
.\gradlew.bat runClient
```

The GameTest command runs the combined suite. The XML report is in `build/field-observation-tests/report.xml`.

## Survival walkthrough

1. Use the Field Lens to observe stone, a torch, and a feather. Observe a water bucket in the offhand while sneaking with the Lens in the main hand to learn Aqua. Open the journal and confirm that Alchemy Foundations is available. A known Aqua source such as a clay block also works.
2. In the Arcane Workbench, place Resonant Copper above a vanilla cauldron and craft the Crucible for 12 Vis. For testing only, the item ID is `thaumiclegacy:crucible`.
3. Place the Crucible and use a water bucket on it. Confirm the filled basin is visible and an empty bucket remains. Use charcoal before adding heat and confirm the charcoal stays in hand. Place a magma block directly underneath. Wait about five seconds at 20 TPS and check that the basin shows it is ready.
4. Use a charcoal item on the basin twice without sneaking. Pause briefly between interactions. Empty-hand right-click inspects the mixture. Confirm that it contains 4 Ignis and 2 Perditio. Sneak and use redstone dust on the basin. One Ember Salt should enter your inventory, the catalyst and listed Essentia should be spent, and the water should remain. Use the salt as a furnace fuel.
5. Dissolve a torch and a glass bottle. Sneak and use a glass block on the basin. The result is two Lumen Glass blocks. Inspect the surplus of 2 Ignis and 1 Vacuos. Place a Lumen Glass block to check light and appearance.
6. Measure the current chunk with the Thaumometer, then sneak and use an empty bucket on the Crucible. The bucket collects the water and the three units of remaining Essentia add one Flux in an unsaturated chunk. Refill, reheat and dissolve two ink sacs and a quartz. Sneak-use sugar to make Cleansing Salt. With the salt held in a polluted chunk, right-click into the air to remove up to eight Flux.
7. Test a wrong catalyst, a cold basin, insufficient Essentia, a full inventory and a full basin. Each refused interaction preserves the held item and the mixture. Stop and reopen the world while the Crucible is wet and contains several aspects. Inspect it again after reloading its chunk.
8. Compare the Crucible states, readout, error messages, and journal in English and Russian. Check fixture blocks with a cold basin, a heating basin and a ready basin.

## Dedicated multiplayer and restart

1. Use a test world and two matching modded clients. Open Alchemy Foundations only on one player. Prepare enough mixture for one recipe and use the same catalyst from both players. The unqualified player must retain their catalyst. With both players qualified and one recipe's worth of Essentia, only one result can be produced.
2. Try rapid repeat use, then remove the heat source before another attempt. Confirm the resulting cold refusal preserves resources. Break the basin with remaining mixture and check that the Crucible item drops and contamination is applied once.
3. Leave another loaded basin filled with a mixture, cleanly stop the dedicated server, and restart it. Reload the basin's chunk and inspect water, mixture and heating state. Confirm the previous pollution is still tracked by the local aura.

## Local verification

- Java 21 build and JAR checks passed. All 24 Fabric GameTests passed, including four alchemy cases. They cover the arcane recipe, all three products, heat loss and warmup, full and refused attempts, per-player research, the shared mutation delay, NBT roundtrip, fuel and light values, and residue and cleanup in the aura ledger.
- Actual item use in a client succeeded in English at GUI Scale 2 and in Russian at Auto (effective scale 3). In both runs the player filled the Crucible, kept the charcoal during a cold refusal, heated it, dissolved a torch and a glass bottle, and received two Lumen Glass blocks. The final Russian run showed the raised surface and verified the empty-hand readout of Ignis 2 and Vacuos 1. The three residual units added one Flux when drained.
- Two dedicated-server runs loaded the test chunk and shut down cleanly. The second recovered the ready Crucible, 100 heat ticks, Terra 12, and Ordo 2. Breaking it changed local Flux from zero to two. The shared Crucible tests use two simulated server players. The two-real-client scenario remains open.
