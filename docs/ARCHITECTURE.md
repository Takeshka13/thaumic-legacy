# Architecture

## Repository baseline

Thaumic Legacy starts from a Fabric scaffold targeting Minecraft 1.21.1 and Java 21. Loom separates the common `src/main` and client `src/client` source sets. `ThaumicLegacy` and `ThaumicLegacyClient` are the current common and client entrypoints. Gameplay systems and addon contracts are in design.

## Proposed boundaries

| Area | Ownership and dependencies |
| --- | --- |
| Common bootstrap | Register Fabric content and wire gameplay services. Keep all common execution paths safe on dedicated servers. |
| Definitions | Load aspect profiles, observation targets, research requirements, and later recipes by namespaced ID. Validate schema versions, missing references, duplicate IDs, bounded values, and prerequisite cycles before activating the next snapshot. |
| Discovery | Own server-side observation validation and per-player research. The journal presents a filtered view of the player's discoveries and requirements. Items used to open it carry presentation metadata where needed. |
| Regional simulation | Own charge, residue, and anomaly state per dimension and cell. Tick loaded cells on a fixed budget. Neighbor operations require both cells to be active and never force-load chunks. |
| Workshop | Stations, vessels, and construct jobs call shared inventory, charge, and essence operations. Plan transfers against visible state and commit on the server thread. A rejected transfer leaves both endpoints intact. |
| Client presentation | Own screens, overlays, models, audio, and effects. Show localized and accessible feedback derived from server snapshots and deltas. Keep client classes in `src/client`. |
| Future extension boundary | Keep internal system boundaries and format versions explicit. Publication of API types, addon entrypoints, separate addons, and external integration bridges is deferred. |

The dependency order is definitions → discovery and regional simulation → workshop rules → rituals, spells, ecology, and automation. Client views depend on synchronized state. Gameplay decisions have no dependency on rendering or external mods. See the [Gameplay Systems](GAMEPLAY_SYSTEMS.md) dependencies and the [Decision Log](DECISIONS.md).

## Persistence and synchronization

- Store knowledge by player UUID using a versioned server save. Keep discovery separate from journal item ownership and dimension changes. Candidate storage uses vanilla saved data with Mojang mappings. Preserve unknown schema versions without overwriting their data, and test migrations for safe retries.
- Persist regional fields separately by dimension and cell. Prefer sparse storage for modified cells and deterministic defaults for untouched areas. Store station inventories and active work in their owning block entities. Use persistent entity data for construct ownership and recoverable job state. Item-specific spell or vessel configuration can use 1.21.1 item data components.
- Validate incoming actions against the active definition snapshot, player reach, permissions, item state, and current server state. Bound payload sizes and action rates. Send the affected player a snapshot on join and coalesced deltas thereafter. Tag snapshots with revisions so the client can reject stale updates. Clear stale state on disconnect or dimension change.
- Validate data reloads before activation. Finish active work against a recoverable recipe revision or cancel using its persisted reservation and progress. Keep a single owner for reserved material. Document checkpoint behavior across files before adding shared reservations.

Fabric API and Minecraft data facilities are implementation candidates. The initial design avoids optional libraries and public interfaces. [Fabric networking API for 1.21.1](https://maven.fabricmc.net/docs/fabric-api-0.115.6%2B1.21.1/net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking.html) · [Fabric item data component guide](https://docs.fabricmc.net/develop/items/custom-data-components)

## Verification implications

- Check client and dedicated-server startup when changing bootstrap, registration, networking, or code reachable from both environments.
- Exercise save/load, migration, and reconnect when changing persistent or synchronized state.
- Exercise contested inputs with two players. Cover full outputs, broken stations, unavailable chunks, definition reloads, and abrupt restart before declaring a resource-consuming feature complete.
- Profile work against the empty scaffold on the same world and hardware. Record p95 and p99 tick time, active cells, pathfinding requests, save size and duration, and bytes sent per player. Record the fixture and agree on limits before increasing load.
