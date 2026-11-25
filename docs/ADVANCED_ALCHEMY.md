# Advanced Alchemy

The existing Alchemical Furnace, Arcane Alembic, Warded Jar, phials, transportation blocks, and saved discoveries retain their IDs. The new milestones appear under Alchemy in the Thaumonomicon.

| Milestone | Requirement | Result |
| --- | --- | --- |
| Warded Jar Labels | Warded Jars and Vitreus | Four blank labels for 4 Vis. Put an ink sac below the top middle paper, between two papers. |
| Improved Distillation | Valves and Filtering | Journal guidance for stacking existing alembics. The mechanical upgrade also works for previously placed furnaces. |
| Alchemical Centrifuge | Improved Distillation and Motus | Centrifuge for 24 Vis. Copper above an alembic, a tube on either side of the alembic, and Resonant Copper below. |
| Automated Alchemy | Alchemical Centrifuge, Warded Jar Labels, and Potentia | Thaumatorium for 32 Vis. Thaumometer on top. Crucible between two Resonant Copper in the middle. Alchemical Furnace between two valves below. |

## Centrifuge

Use a filled phial or connect a tube to a horizontal input. A compound is identified by its exact registry ID. Mortuus and Venenum remain separate inputs even though both produce Aqua and Perditio in the bundled catalog. Primary aspects are rejected. Each operation takes 80 working ticks and converts one compound into one unit of each parent. The input holds 32 units and the output holds 64. Work pauses unless both outputs fit. A tube from the top carries parent aspects to a jar. An empty phial collects output. Sneaking with an empty phial recovers queued input.

Thaumcraft 4 and 6 fed the centrifuge from below and recovered just one parent per processed unit. This implementation feeds from the sides and guarantees both parents. No fuel, ambient Vis, or Flux is consumed by the current machine. [Azanor's TC6 centrifuge and Thaumatorium entries](https://raw.githubusercontent.com/Azanor/thaumcraft-beta/master/en_us.lang#L1418-L1434) · [TC4 centrifuge behavior](https://ftb.fandom.com/wiki/Alchemical_Centrifuge_(Thaumcraft_4))

## Thaumatorium

Use an empty hand to cycle the recipes you have unlocked. The action bar names the selected recipe's product. Insert a matching catalyst by using it on the machine. Supply the required aspects through horizontal tube inputs or filled phials. An empty phial recovers unused essentia. The machine accepts only the current recipe's outstanding amounts, up to 64 total. The player who selected the recipe must be online, alive, and hold the required research for incoming transfers and production.

Distillation does not feed automatically into the machine without a tube path. Once the complete catalyst, essentia, result space, and any remainder space are available, crafting advances for 120 working ticks. All recipe materials are debited together when the result commits. Collect the result and any container remainder with an empty hand. A recipe may reuse a one-item remainder that still matches its catalyst. Sneaking with an empty hand recovers the catalyst first and clears the recipe on a subsequent use. A recipe change or catalyst removal resets work progress. Essentia already accepted remains recoverable in the buffer. A full output, missing resources, or unavailable operator pauses production.

The device reads the existing `thaumiclegacy:alchemy` recipe type. A selected recipe ID, operator UUID, catalyst, result, remainder, work progress, and buffers persist in block-entity data. Normal block drops retain that data. The classic Thaumatorium was assembled as a multipart device and used the original suction system. This foundation is one craftable block with all-or-nothing recipe consumption. [Azanor's published TC6 journal](https://raw.githubusercontent.com/Azanor/thaumcraft-beta/master/en_us.lang#L1418-L1434)

## Jar labels and improved distillation

Use a blank label on a jar containing an aspect you know. To label an empty jar, hold a filled phial of that aspect in the other hand. The sample is preserved. The jar accepts only the chosen aspect from phials and tubes, even when empty. Sneak and use an empty hand to remove the label and get a blank label back. Thaumometer and Goggles of Revealing show the designation. A legacy jar with no recorded label behaves as it did before.

The furnace stores a completed profile in its internal buffer before distributing it to up to four consecutive alembics directly above it. Each alembic holds one aspect at a time. Gaps end the column, and stock with no compatible destination stays in the buffer. The furnace pauses before consuming input if the whole batch cannot fit in the buffer. Each alembic exposes its active stock to existing tubes and phials. [Azanor's TC6 labels and stacked alembics](https://raw.githubusercontent.com/Azanor/thaumcraft-beta/master/en_us.lang#L1395-L1409)

## Essentia suction design

Historical TC4 technical references describe compatible jars and demanding machines as suction sources. Suction propagates toward the supply and decreases by one through an ordinary tube. Essentia moves toward the strongest compatible sink. A labelled jar can outdraw a plain jar. Working machines can advertise stronger suction than jars, while full or inactive sinks can stop drawing. Incompatible aspects or competing suction can stall a route. The published TC6 journal confirms typed priority, directional/restricted tubes, valves, filtering, and visible interference. Its text does not specify numeric values or tie-breaks for every machine. [TC4 transport reference](https://thaumcraft-4.fandom.com/wiki/Essentia) · [Azanor's published TC6 tubing notes](https://raw.githubusercontent.com/Azanor/thaumcraft-beta/master/en_us.lang#L1436-L1446)

The current server uses typed, directional ports with separate availability checks and a destination preflight before source debit. Alembics can supply jars and machines. Jars can supply machines. Centrifuges output to jars. A route requires one or more loaded pipes. Jar-to-jar transfers are excluded to avoid oscillation. A future suction policy can rank eligible destinations and propagate typed demand over the same port graph. Keep transport bounded, keep conflict decisions deterministic, and preserve the existing all-or-nothing transfer contract before adding pipe buffers or vents.

## Verification scenario

1. Stack at least two alembics on the Alchemical Furnace. Distill a torch and confirm that Ignis and Lux go into separate alembics. Block the outputs and confirm that new stock accumulates in the furnace buffer. When it can no longer hold an entire batch, confirm that the next input stays in the slot.
2. Distill bone to obtain Mortuus. Put it in the centrifuge with a phial and confirm that Aqua and Perditio are produced together. A phial of either primary is rejected as input. Connect the top output to a suitable jar or extract it with an empty phial.
3. Label an empty jar using a filled matching phial in the other hand. Verify rejection of a different aspect. Break and place the jar, inspect it, then remove the label with sneaking empty-hand use.
4. Select Lumen Glass on the Thaumatorium, insert plain glass, and supply Lux 2 and Vitreus 2 through tubes. After six seconds of eligible work at 20 TPS, collect two Lumen Glass with an empty hand. Check again without adding resources. No second batch is produced.
5. Restart a disposable dedicated server with a partially filled machine and a labelled empty jar. Check the buffered contents and selected recipe. Production pauses until its operator is available and authorized.

## Local verification

The Java 21 build and 46 GameTest passed. In a client copy of an existing world, the Thaumatorium received essentia through tubes and crafted two Lumen Glass from one glass catalyst. The centrifuge converted Mortuus and Venenum into two Aqua and two Perditio in total. The client applied a label by right-click and displayed it in the Thaumometer readout.

The isolated dedicated server preserved an empty jar's Aqua label, a centrifuge blocked at tick 79, and the catalyst and essentia of a Thaumatorium with an offline operator. After freeing the centrifuge output, the original input produced exactly one Aqua and one Perditio. Two real clients on a dedicated server remain to be tested.
