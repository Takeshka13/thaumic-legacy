# Gameplay Systems

Thaumic Legacy is an independent project for Minecraft 1.21.1 Fabric. The player loop is to **observe → form a hypothesis → test it in a workshop → apply the result → manage the consequences**. The project will author its own mechanics, progression, recipes, names, interface composition, writing, art, models, sounds, and effects. Historical terminology in the [Feature Matrix](FEATURE_MATRIX.md) identifies the reference game. The designs below use working names for Thaumic Legacy.

Use public release notes and player-facing observations for historical research. Exclude copied source, decompiled implementations, and original Thaumcraft text or assets from project deliverables. Maintain provenance for newly authored materials. Marketing and release notes describe the project as independent.

The matrix gives the role, prerequisites, milestone, effort, and acceptance checks for each feature. This document describes proposed behavior and 1.21.1 design work. Values and named content remain provisional until playtested.

## First playable slice

Craft a **survey lens** with a new vanilla-ingredient recipe. Observe stone, a torch, and an oak log to record three distinct material profiles. Open a **field journal** and complete one milestone by observing any two different profiles. The milestone reveals a short original entry explaining how the third specimen can broaden the player's findings. Include one discovery prompt and a clear explanation of an already recorded or invalid target. Use original icons and text for both locales.

Scope the first build to three explicit block profiles, one observation action, one UUID-owned server journal, one milestone, and one client view. Use integrated and dedicated servers. Validate reach, line of sight, held item, and rate limits. Verify duplicate requests, two players observing the same block, a failed reload, reconnect, and server restart. The player should understand the loop without a wiki. Aura, essence quantities, world generation, and additional progression branches can follow independently.

## Discovery and progression

### F01 Aspects

Define an original set of material traits, starting with a few contrasting profiles for rigidity, warmth, and growth. A profile maps a specimen to one or more small trait weights. Later recipes can query these profiles to choose useful feedstock, with traits that work across exploration and alchemy. Treat a trait profile, a discovery record, and an inventory of refined essence as distinct quantities.

For 1.21.1, use registry IDs and data tags with explicit precedence for exact definitions over grouped definitions. Report conflicting equally specific rules during validation. Start with a small reloadable catalog of explicit block profiles. Add grouped definitions when another feature needs them. Avoid discovering an item's profile by recursively traversing its crafting recipes.

### F04 Scanning

A lens sends an observation intent, and the server rechecks what the player can actually see and reach. The first observation records the canonical target ID and its traits for that player. Repeats still show known information and provide no additional progress. Later define separate policies for loose items, live creatures, and anomalous sites. Do not disclose another player's container contents through scanning.

Use normal item interaction and server raycasting for the initial block path. Use a short client overlay for aiming and a server response for success or rejection. Address delayed packets, moved targets, and switching items. Block state variants share a profile unless explicitly distinguished.

### F05 Research

Model a milestone as a set of visible evidence requirements and prerequisite milestones. A later workshop experiment tests a player choice, such as comparing two feedstocks by yield and waste, and records the result. Let multiple evidence paths satisfy broad requirements so an unusual world or missing creature cannot deadlock progression. Gate fabrication at the server-side station.

Persist unlocked milestones by namespaced ID. Validate the graph for missing IDs and cycles, and decide how to migrate renamed milestones or remove retired entries. Put canonical player knowledge behind one persistence boundary so dimension changes, death, and journal replacement preserve it. Give experiments an explicit start, outcome, and retry state to survive reconnect.

### F06 Magic book

Use a field journal with a specimen index and a research notebook with list-based navigation. Each entry can show known material traits, currently available actions, and the hint or reason for a locked requirement. Provide original diagrams for later workshop steps. Journal ownership and progress are independent.

Use an original Fabric client screen with keyboard navigation, scalable text areas, and a layout tested with lengthy Russian labels. Synchronize only the player's known details and visible goals. Use language keys for headings and feedback. Keep undiscovered content and server rules out of client-side decisions.

### F19 Onboarding and field expeditions

Use the vanilla recipe book and a journal prompt to introduce the lens and its first targets. Later optional expeditions can ask players to compare two biomes or weather conditions and return with a short observation set. Give timed opportunities a different obtainable clue so limited play sessions do not halt progress.

Use built-in recipe and advancement facilities when they fit. Keep milestones in the mod's knowledge model, and give existing worlds the same entrance to progression. Write new localized prompts and test a clean player profile.

## Regional magic and workshops

### F02 Aura and Vis

Implement **ambient charge** as an integer balance with a baseline and an upper bound for each dimension-local cell. Regenerate at a bounded cadence. A workshop can draw charge from its own cell so cost and recovery are easy to read. Prototype one cell per chunk, then choose the scale through playtesting. Neighbor exchange is a later candidate and must conserve total charge apart from explicit regeneration and spending.

Track dirty loaded cells, cap work per tick, and use a deterministic default for untouched cells. A recipe consumes charge only when confirmed. Unloaded periods receive either no simulation or one explicitly bounded recovery calculation. Choose and document the rule before balancing recipes.

### F03 Flux and remediation

Use **residue** as a separate pollution balance. High residue reduces recovery, and an alchemical waste outlet adds residue that the player can measure. A treatment device consumes a prepared filter and collects a stable waste product for disposal. Make cleanup a local trade-off and provide a slow passive recovery floor. Record each source and sink for test diagnostics.

Keep this ledger beside charge and keep spread disabled in the first version. Add a localized warning and display a tier with text or shape as well as color. Check that repeated loading, block breakage, or cleanup cannot duplicate the waste product or bypass input costs.

### F07 Alchemy and distillation

Design a batch station that accepts a set of tagged inputs, a reusable or consumed catalyst as declared by the recipe, and a selected heat mode. An initial recipe converts common inputs into one useful workshop material. Distillation can separate a known specimen into one refined essence, with bounded yield and optional residue. Later combinations trade speed against waste and purity.

Use 1.21.1 recipe or reload facilities after choosing the schema. Match a deterministic recipe, verify the output and waste sinks, and commit costs once. Persist the station's batch phase and input reservation for multi-tick work. Treat full outputs as backpressure and make the player-visible state reason explicit.

### F08 Magical crafting

Use a specialized bench with an ingredient panel and a preview of the local charge cost. Design one first recipe that applies field knowledge to craft a useful workshop tool. Later recipes can choose between multiple input bundles with different efficiency. Knowledge records remain prerequisites instead of consumable materials.

Use a normal server-backed menu with its own rules for manual output pickup and shift-clicking. Validate recipe, remaining capacity, ownership, inputs, and charge in one operation. Prevent an old preview from committing after recharge conditions or content definitions change.

### F09 Essences and storage

Refined essences are bounded integer amounts keyed by material trait. Use small single-type vials and larger labeled vessels, with manual transfer before introducing pipes. Give spent or empty storage a clear state. Treat mixed-type storage as a later upgrade with a clear capacity rule.

Store per-item data in 1.21.1 components where appropriate and placed storage in a block entity. Decide whether filled portable items can stack. A placed vessel and its dropped item are alternate representations of the same contents. Validate its type and quantity during load and transfer.

### F10 Essence transport

Model connected ports as a graph using explicit direction, filters, and priority. Keep a first network push-based with a bounded transfer per step, predictable ties, and no simulated packets in the pipes. Introduce a buffer for decoupling producers from consumers. Closed valves, blocked outputs, and unavailable endpoints appear in a diagnostic view.

Recompute local topology on block or chunk changes, use a budget for active networks, and require both endpoints to be loaded. Use a plan and commit operation for each move. Calendar tick time or neighbor update spam cannot create extra throughput. Track the inventory total before and after a graph fixture.

### F15 Automation

Let a station export simple work requests, such as delivering a tagged material to a configured buffer. Design redstone start/stop and an internal job queue before autonomous helpers. Reserve a bounded quantity and attach a reason when the station is waiting. Automation uses the same rules as a player action.

Run dispatch on the server with a bounded search area and shared resource operations. A claim belongs to one job ID, has one current owner, and can expire. Recovery after restart reconciles source, worker cargo, and destination without silently discarding material.

### F23 Advanced workshop upgrades

Plan a choice between larger buffers, cleaner conversion, or higher throughput. Give each machine one explicit trade-off. Workshop diagnostics should let players compare the result of two configurations. Reuse residue remediation and cap the total rate at which a network can waste or recover resources.

Persist upgrades through station save data and clamp old values deliberately during migrations. Ensure replacing an upgrade preserves or visibly handles excess contents. Avoid a path where passive improvements remove all energy or material costs.

## Rituals, tools, spells, and constructs

### F11 Altars and instability

Design a **harmonic frame** around an anchor and a set of ordered functional stations. The layout depends on role coverage and phase timing. A ritual takes measurements, binds prepared materials, then resolves. Local residue, mismatched components, and weak supports raise a readable strain tier. A player can stabilize or abort before a bounded setback. Early failures can stall progress, discharge a warning pulse, or produce waste.

Validate a bounded structure near the controller and invalidate the cached layout on relevant changes. Show the affected station and the direction of strain change using a label and a symbol. Support reduced flashes. Persist input escrow and ritual phase in one owner for the first version. Finish once, or return unspent escrow under the interruption policy. Test controller breakage, unloaded stations, failed restarts, and blocked output. Avoid scanning an entire region each tick.

### F12 Magical tools

Start with the survey lens. Later add a regional gauge and a caster implement. The gauge trades an equipment or hand slot for a readable account of charge, residue, and direction to an anomaly. The caster's configuration gives it an explicit role and explains cost and handling.

Use standard hand interactions and existing equipment slots or held-item overlays. Store configuration in item components as needed. Keep range and state server-authoritative and send only compact display data. Use independent icons, geometry, animations, and sound.

### F13 Spells

Design an editor with three fields for delivery, effect, and one optional modifier. Offer a directional pulse and a placed glyph as delivery forms, and a light marker and a controlled push as early effects. Each combination has a cost budget, a range limit, and compatibility rules. A saved configuration can become unusable after a definition reload with a visible reason.

Use validated spell data and a short server execution path. Check target, ownership, friendly fire, and whether the action can affect a block or entity before consuming charge. Keep the first effects reversible or non-destructive. Register mere visual effect feedback on the client and rate-limit requests on the server.

### F14 Golems

Build an original construct from a chassis and a replaceable behavior module. Start with hauling between two explicitly marked stations and tending a player-marked plot. Give owners a compact assignment screen, a stop command, and a clear reason for idle workers. The worker appears physically in the world and can carry the job's cargo.

Use server entity data and normal path navigation with bounded repaths, search radius, and active job count. Check ownership at job assignment and again at interaction. Workers do not force-load chunks. Persist carried material, and reconcile interrupted jobs after unload, death, or restart.

### F21 Artifice and utility

Use simple upgrades to expand safe workshop work and utility play. Candidates are a protective mantle that reduces one specific hazard and a workshop lamp that reacts to regional state. Define distinct uses and costs before adding variants. An advanced ritual may improve a tool without becoming a prerequisite for basic exploration.

Use native equipment slots, item components, and block state or block entity data as appropriate. Reward original sound, texture, model, and tooltip work. Verify repair, drop, placement, and save persistence.

## World, creatures, and consequences

### F16 Magical creatures

Design a neutral survey creature that hints at local material traits and a hostile anomaly native that pushes players out of a threatened area. Each creature should have its own encounter role, visual silhouette, and bounded spawn conditions. Tie one optional journal record to each and avoid progression requirements that depend on finding a rare mob.

Use separate renderer registration, server-side AI, data-driven loot, and capped local populations. Schedule expensive behavior checks at a fixed cadence. Verify target loss, despawn boundaries, and dedicated startup.

### F17 World generation

Add original mineral growths, unusual flora, and small ruined monitoring stations in stages. Each site supports a distinct discovery activity. Use alternate vanilla or workshop sources for critical inputs and keep rare finds as shortcuts or specializations.

Use the 1.21.1 configured and placed feature pipeline, biome tags, and original structures as appropriate. Keep deterministic seed fixtures and biome allowlists. Generate in new chunks and give preexisting saves a documented route to materials. [Fabric biome API for 1.21.1](https://maven.fabricmc.net/docs/fabric-api-0.116.17%2B1.21.1/net/fabricmc/fabric/api/biome/v1/BiomeModification.html)

### F18 Anomalies and contamination

An **aura fracture** begins after sustained local residue crosses a tuned band. It has warning, active, contained, and resolved states. A survey tool identifies it and a player can stabilize it by treating the surrounding area. The first fracture applies reversible effects within a bounded radius. Later terrain changes require a separate decision and server control.

Persist the owner cell, cooldown, and lifecycle stage. Set per-dimension and per-tick budgets. Avoid changes in unloaded chunks and give operators control over damage. Verify resolution stops further effects and a restart cannot spawn duplicate fractures.

### F20 Research strain

Explore a separate personal **research strain** meter for optional risky experiments. Prefer short, telegraphed effects with visible recovery and an accessibility setting. Keep it distinct from regional residue and avoid a mandatory penalty that prevents progress through standard research.

Persist one small server-side record per player. Synchronize only that player's relevant feedback. Test death, reconnect, and operator settings. Avoid visual warnings that depend solely on color, flashes, or audio.

### F22 Forbidden sites and encounters

Design an optional expedition to an original unstable observatory after the regional and creature systems are tested. Players decipher site clues and choose to recover an artifact or secure the site, with distinct rewards. Keep the encounter outside required progression. Treat any extra dimensions, final bosses, or large decorative sets as separate scope decisions.

Use a bounded structure encounter with server-owned progress, explicit reset conditions, and per-player reward claims. Confirm that existing worlds can reach an equivalent site and that multiplayer parties can retry without duplicating rewards.

## Shared verification

- For every milestone, record a player-facing walkthrough on an integrated server and a dedicated server with two clients. Cover reconnect, restart, and both locales where screens or feedback change. Check translation key and placeholder parity.
- For resource operations, assert conservation across rejection, contention, reload, breakage, and full outputs. Test recovery at boundaries before reservation, after reservation, and after delivery before allowing a single action to mutate ownership across save records.
- For the regional simulation, transport, and constructs, record the number of active cells, networks, and entities in the performance fixture. Agree on time, packet, and save-size budgets before raising simulation limits.
- For exploration content, verify fresh and existing worlds. For releases, record the provenance of original art, text, models, and audio, and document the independent project identity.

## Later cross-system playtest candidate

After the initial workshop loop, test a small irrigation branch. Observe two water conditions and unlock a lesson about how materials hold water. An experiment produces a lining for an interchangeable nozzle. A spell waters one plot, and a later ritual applies the effect along directional markers. Rushing the sequence creates temporary strain and a measurable trace. A player can let it dissipate or spend a prepared catalyst. Include an indoor observation route so weather is optional. Costs, timing, and the branch's place in progression remain open.
