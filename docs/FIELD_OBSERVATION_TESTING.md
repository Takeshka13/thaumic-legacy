# Field observation QA

## Verification status

The user confirmed that all nine manual gameplay checks passed. Local automation exercised two simulated player UUIDs. The [scenario with two real clients](#dedicated-server-with-two-players) remains open, including reconnect and journal verification after a dedicated-server restart.

Use Minecraft 1.21.1, the Fabric Loader and Fabric API versions in `gradle.properties`, and Java 21. Use disposable survival worlds and keep a backup before opening a save from an earlier Thaumic Legacy build. Record the build, Java version, game side, language, GUI scale, and first relevant log message for any failure.

## Build and package checks

When preparing a build for QA, run these commands from the repository root. The PowerShell execution policy override applies to the verifier process only.

```powershell
.\gradlew.bat build
powershell -NoProfile -ExecutionPolicy RemoteSigned -File .\scripts\verify-field-observation.ps1 -JarPath .\build\libs\thaumiclegacy-1.0.0.jar
```

Use the actual artifact path if the configured version differs. The script exits with an error on missing or duplicate expected entries, invalid JSON, incorrect model references, invalid texture format or dimensions, incorrect recipe directory or result format, or missing translations. It also checks entrypoint and payload class presence. Game startup verifies that Minecraft accepts the recipe and resource files and can initialize each side.

## Automated server checks

Run Fabric GameTest with a separate test configuration.

```powershell
.\gradlew.bat runFieldObservationTests
```

The runner uses a test world under `build/field-observation-tests/server` and writes `build/field-observation-tests/report.xml`. It tests both recipes with Minecraft's recipe manager, all three observations, duplicate and invalid attempts, the unlock threshold, two mock player UUIDs, and reloading their records from disk.

## Single-player survival

For a quick check with commands enabled, use `/give @s thaumiclegacy:thaumometer` and `/give @s thaumiclegacy:research_journal`. Use the crafting steps below to verify survival acquisition. When testing a full restart in the development client, use the same name on both launches, for example `.\gradlew.bat runClient --args="--username FieldTester"`. The journal follows the player UUID.

1. Start the Fabric client and enter a fresh survival world. Confirm the main menu and world load without new Thaumic Legacy registration, mixin, or resource errors in `logs/latest.log`.
2. Gather resources and craft both items without commands. Make a Thaumometer in a crafting table with copper ingots at top and bottom center, an amethyst shard at center, and a glass pane on each side of the shard. Craft a Research Journal from a book and one copper ingot in any arrangement. Each recipe yields one item. Check both inventory icons, both held textures, and both displayed names. With recipe book filtering disabled, confirm that acquiring a book reveals the journal recipe.
3. Right-click with the Research Journal in hand. Verify that the observation list is empty and the first research is locked. Close the screen with Escape. Open the journal while aiming at a plain stone block as well as while aiming into air.
4. Right-click stone within normal survival interaction distance with the Thaumometer. Expect a localized observation message containing the block name. Open the journal and check that it lists stone once and remains locked. Repeat on a different stone block and check the duplicate message and unchanged journal.
5. Use the lens on dirt and into empty air. Expect localized feedback for an unsupported or missing target and no progress. Put an oak log beyond normal block interaction distance and use the lens while aiming at it. Then move the log within reach, put a dirt block between yourself and the log, and use the lens while aiming toward the log. Reopen the journal after both attempts and verify oak log is absent.
6. Observe a standing torch. Expect a second observation and one unlock notification. Open the journal and verify that stone and torch each appear once and the first research is unlocked. Observe a wall torch and check for a duplicate with no second unlock. Observe an oak log, including a horizontally placed one if available. Check that oak log becomes a third entry and that logging another oak log preserves three entries. On a fresh profile, observe a wall torch before a standing torch and verify that either order gives the same torch entry.
7. Reopen the journal in English (US) and Russian. In each language check item names, successful, duplicate, missing-target, and unlock feedback, specimen names, and locked and unlocked state text. Use a fresh player or save for the locked state and one-time messages. Test GUI Scale 1, 2, and Auto, including a small window with all three entries and the Russian text. Verify that text stays within the journal, scrolling reveals any text below the visible area, and both Escape and Done return to the game.
8. Save and quit, then reopen the same world. Use the journal and verify that observations and the unlock persist. With keep inventory disabled, die, respawn, and recover or craft a replacement journal. The replacement should show the same progress.

## Dedicated server with two players

1. Start a disposable Fabric dedicated server with the remapped mod JAR and Fabric API installed. Confirm that the server reaches `Done` and check its log for missing client classes, initializer errors, and recipe load failures. Start two modded clients with distinct player UUIDs and join the server. Give both testers access to survival resources and a crafting table.
2. Players A and B craft the lens and journal. Both open an empty, locked journal. A records stone, while B records an oak log. Reopen both journals and verify that A sees only stone and B sees only oak log. When A uses the journal, B should receive no screen or feedback from that action.
3. A records a wall torch, sees the unlock, and reopens a journal with stone and torch. B's journal still has oak log and remains locked. B then records the standing torch and unlocks independently. Use the journals in close succession and verify that each client sees its own observations and unlock state.
4. A travels to the Nether and opens the journal before making a new observation. It should show A's Overworld progress. A places and observes an oak log in the Nether and checks that oak log remains visible after returning to the Overworld. B stays in the Overworld and checks that B's journal still contains only oak log and torch.
5. A disconnects and rejoins with the same identity while B stays online. A immediately opens the journal and verifies all three observations and the unlocked state. B verifies unchanged progress. Then stop the server cleanly, start it again using the same world directory, and repeat the journal checks for both players.
6. A dies and respawns with keep inventory disabled. After recovering or crafting a journal, A sees the same three observations and unlocked state. B loans A a journal and checks it again after A gives it back. Each player sees their own progress regardless of who crafted or previously used the item.

## Earlier save

1. Start a copy of an existing survival world saved by a build without field observation. Join as an existing player and verify the saved position, inventory, and local chunks still load.
2. Craft both items. A fresh journal for this player starts empty and locked. Record two different supported specimens and verify that the first research unlocks.
3. Save and close the client or stop the dedicated server cleanly. Reopen the same copy and verify that the earlier world state and the new observations are present.

For each scenario, report the observed outcome and mark unrun checks as skipped. Capture the game log and the exact steps for any failure.
