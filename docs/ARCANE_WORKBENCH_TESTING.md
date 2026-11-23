# Arcane Workbench testing

Run with Minecraft 1.21.1, Fabric, and Java 21. Use a disposable world or a copy of an existing save.

```powershell
.\gradlew.bat build
.\gradlew.bat runFieldObservationTests
.\gradlew.bat runClient
```

The GameTest command runs the combined suite. The XML report is under `build/field-observation-tests/report.xml`.

## Single-player progression

1. Craft a Field Lens and Research Journal. If commands are enabled, use `/give @s thaumiclegacy:field_lens` and `/give @s thaumiclegacy:research_journal`.
2. Scan stone and a torch. Comparing Specimens opens, while Arcane Foundations still needs Lux. Scan a feather by holding it in the offhand and crouching with the lens in the main hand. The torch evidence and newly discovered Aer reveal Lux. Reopen the journal to read the Arcane Foundations entry.
3. Craft an Arcane Workbench at a regular crafting table. Put copper at top center, glass panes left and right of a regular crafting table in the middle row, and stone at bottom center. For troubleshooting use `/give @s thaumiclegacy:arcane_workbench`. Place the block and open it with right-click.
4. Place redstone dust above copper in the workbench. Check that the result shows Resonant Copper and costs 8 Vis. Craft two units using two separate actions. Use two glass panes flanking an amethyst shard with Resonant Copper below the shard to craft an Attuned Lens for 16 Vis.
5. Place the Attuned Lens above a Thaumometer, with Resonant Copper on the Thaumometer's left and a copper ingot on its right. Craft the Aura Surveyor for 60 Vis. Use it to survey the local chunk and the four adjacent chunks. Check the Vis reduction at the bench or with a second Thaumometer.
6. Put only part of a recipe in the grid and check the explanation. Try a complete recipe with less Vis than its cost. Pollute the chunk with `/thaumiclegacy aura flux 80` to freeze recovery during this check. Verify that the attempt preserves the ingredients. Run `/thaumiclegacy aura clean 100` and wait for Vis to return, or restore it with the operator command.
7. Fill all player inventory and hotbar slots without a compatible partial output stack. Verify that crafting is blocked and that neither Vis nor ingredients are consumed. Free a slot, craft once, and verify that exactly one recipe result arrives.
8. Close the interface with ingredients still in the grid. Save and restart the world, then check the grid, local Vis and research. Break the block and check that its stored ingredients drop once. Compare both languages at GUI Scale 2 and Auto, including a locked recipe and the insufficient Vis state.

## Dedicated multiplayer

1. Join a disposable dedicated server with two matching modded clients. Keep one player new and let the other complete Arcane Foundations. Open the same bench from both clients and arrange a gated recipe. The player's own research must control the action, even when both see the same shared inventory.
2. Place enough ingredients for two operations and set the chunk's Vis so only one can afford its recipe. Both players should have Arcane Foundations for this check. Press Craft as close together as possible. Exactly one result is produced and the cost is charged once.
3. Have either player change an ingredient while the other clicks a stale preview. Check that the stale request cannot craft a different recipe. Repeat while one client closes the interface, disconnects, or breaks the bench. Verify that inputs either stay in the bench or drop once on break.
4. Stop the server cleanly with a partially filled grid and spent Vis, then restart it. Confirm the bench contents, chunk balance, and independent journal progress. A forced process termination is a separate failure mode because Minecraft persists chunk, player, and SavedData files independently.

## Local verification

- Java 21 build and JAR checks passed. All 20 Fabric GameTests passed, including six workbench cases. They cover recipe lookup and costs, the sixty-Vis craft, per-player unlocks, replay and shared-grid contention, full inventory, broken-bench drops, and saved grid and Vis data.
- English at GUI Scale 2 and Russian at Auto (effective scale 3) passed in the client. Both clicked the craft button and sent a duplicate of the same request. Each received one Resonant Copper, spent exactly 8 Vis, and retained the rest of the stacked ingredients. The journal displayed the new entry using progress from a copied world. The Russian test also displayed the low-Vis status and updated automatically when Vis became available.
- A fresh player opened the workbench through the block's right-click action. With 100 Vis, the Attuned Lens preview still showed the research requirement and disabled Craft. The full balance label fit the Russian Auto layout.
- The dedicated server loaded a test world twice. Its workbench held two redstone dust and two copper ingots after restart, and the chunk still had 60 Vis and 80 Flux. Both runs shut down cleanly. The GameTest contention scenario uses two simulated server players. The two-real-client connection and disconnect scenario remains open.
