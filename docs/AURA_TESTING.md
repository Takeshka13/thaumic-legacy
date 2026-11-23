# Aura and Flux testing

Use Java 21, Minecraft 1.21.1, and the Fabric versions configured by this repository. Upgrade a disposable copy when using an older world.

## Local build and GameTest

```powershell
.\gradlew.bat build
.\gradlew.bat runFieldObservationTests
```

The second command runs the combined Fabric GameTest suite. Its XML report is in `build/field-observation-tests/report.xml`.

## In-game scenario

1. Start `.\gradlew.bat runClient`, open a test world, and enable commands. Make a Thaumometer at a crafting table. Put a copper ingot at top center and bottom center, an amethyst shard at the center, and a glass pane on either side of it. For a quick test, use `/give @s thaumiclegacy:thaumometer`.
2. In an untouched chunk, hold the Thaumometer and right-click. The reading should show 100/100 Vis and 0 Flux. The reading is a snapshot. Right-click again to refresh it.
3. Without leaving the chunk, run `/thaumiclegacy aura spend 40`. Measure again. The reading should start at 60 Vis and recover by one unit approximately every five seconds at 20 ticks per second. A request to spend 101 Vis always fails and consumes nothing.
4. Run `/thaumiclegacy aura flux 80` and measure again. The instrument should indicate that the environment is polluted and natural Vis recovery is blocked. Right-click after several seconds and verify that Vis stays at the same value. Flux itself decays by one unit every 1,200 world ticks.
5. Run `/thaumiclegacy aura clean 40`. At approximately 40 Flux, recovery resumes at one Vis per 200 world ticks. Use `/thaumiclegacy aura restore 100` to test that Vis stops at capacity. All four mutation commands require operator permission.
6. Walk across a chunk boundary and measure the other chunk. Return and confirm the original balance is still independent. For the same block coordinates in different dimensions, compare `/execute in minecraft:overworld positioned 0 80 0 run thaumiclegacy aura status` with the same command for `minecraft:the_nether`.
7. Add 80 Flux and spend Vis, then save and quit. Reopen the same world and measure the same chunk. The stored deficit and remaining Flux persist. Repeat on a dedicated server using `stop` before restarting. Minecraft must be running for the world clock to advance.
8. Measure again at GUI Scale 2 and Auto, in English (US) and Russian. Check that values and recovery text fit, the reading is visible only briefly, and leaving the world clears it. Pause, resume, and repeat with an inventory overlay.

## Commands

| Command suffix, after `/thaumiclegacy aura` | Effect |
| --- | --- |
| `status` | Prints the current chunk's Vis, capacity, and Flux. |
| `spend <amount>` | Spends the full positive amount or fails with no deduction. |
| `flux <amount>` | Adds Flux up to capacity and reports the amount applied. |
| `restore <amount>` | Restores Vis up to capacity and reports the amount applied. |
| `clean <amount>` | Removes existing Flux and reports the amount removed. |

These commands act at the command source position. They can be used with vanilla `execute in … positioned …` on a dedicated server without joining the world.

## Local verification

- The Java 21 build and JAR resource checks passed. All 14 Fabric GameTests passed, including five aura tests covering balance limits, decay and threshold changes, independent dimensions, reads without chunk loading, on-disk reload, and the Thaumometer recipe.
- The client opened copies of a pre-aura world. Real item use produced the server readings in English at GUI Scale 2 and Russian at Auto (effective scale 3). The captures show full, blocked, and recovering states, plus the overlay absent after expiry. The copies retained their prior research data.
- In a dedicated test world, the same chunk coordinates held 59 Vis and 80 Flux in the Overworld and 82 Vis and 90 Flux in the Nether. The server loaded the Overworld test chunk again after restart and returned both sets of balances intact. An adjacent chunk retained 100 Vis and zero Flux. Both server runs stopped cleanly.
- In a local Java 21 microbenchmark, one million in-memory cell projections took a median of 14.03 ms over six warmed trials, with a range of 13.80–14.38 ms. Inputs varied the saved balance, Flux, recovery remainders, and elapsed time. This measurement excludes world access, network traffic, saves, and server tick profiling. Projection visits at most four recovery bands and does no work on idle ticks.
