# Aura node testing

Use Java 21, Minecraft 1.21.1, and an isolated copy of an existing world or a disposable new world.

```powershell
.\gradlew.bat build
.\gradlew.bat runFieldObservationTests
.\gradlew.bat runClient
.\gradlew.bat runServer
```

The combined GameTest suite writes `build/field-observation-tests/report.xml`. The node cases cover worldgen registration and placement, deterministic generation, persistence, bounded recovery, transfer conservation, held-item charging, interrupted use, and two independent player records.

1. Explore fresh Overworld terrain without equipment, with a held Thaumometer, and with Goggles of Revealing. Compare the faint trace and enhanced world rendering, test a solid wall for occlusion, and check the transparent lens at GUI Scale 2 and Auto. For a controlled test at an isolated position, set a node with `/setblock ~2 ~ ~ thaumiclegacy:aura_node[variant=normal]`.
2. Use a Thaumometer on the node. Check its variant, six reserve lines, and condition. Repeat and verify the journal count stays at one. Use it on a different node and check the count grows.
3. Hold a Wand on the studied node. Check its inventory tooltip and the node readout. Each transferred point must occur in the same primal on both sides. Release or look away to interrupt use. Try a second player who has never studied a node and confirm their attempt moves no charge.
4. Drain the node with spare Wands. Reinspect it when empty and after one recovery interval. Verify that a full Wand reserve accepts no more than 25 and starts filling other available primals in turn.
5. Keep a partially drained node, leave its chunk, and return to the same position. Stop and restart the server. Compare the same UUID and remaining reserves, and reopen the player journal. Check that the node does not reappear in a new copy at the same location or lose its saved depletion on ordinary reload.
6. Verify the same flow in English and Russian and confirm that an adjacent Arcane Workbench still uses the independently measured chunk Vis. Test two matching real clients on a dedicated server before closing multiplayer QA.

## Previous milestone verification

- The Java 21 build and all 30 GameTest passed.
- Isolated Minecraft clients opened copies of an existing world in English and Russian. The player studied a node, then held use for five transferred Aer. The wand gained five, the node dropped from nine to four, releasing use stopped charging, and the local scalar aura reading remained unchanged. The Russian run also checked the journal and node readouts at GUI Scale Auto.
- The dedicated server preserved the test node's UUID, variant, reserve, and recovery clock between clean shutdowns. Four naturally generated node chunks were found in saved regions outside the manually placed fixture region after loading a fresh Overworld area with seed `380541`.
- The two-real-client dedicated-server scenario remains open. Server-side tests cover two separately tracked simulated players.
