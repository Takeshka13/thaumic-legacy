# Addon API Design

Status: proposal for review. Public contracts require design approval before implementation. The entrypoint identifier, Java package, types, and published artifact layout are open.

## Registration by independent Fabric mods

1. An addon ships as a separate Fabric mod with its own mod ID, namespace, and metadata. It declares the supported Thaumic Legacy mod versions using Fabric dependency metadata.
2. The proposed discovery mechanism is a dedicated Fabric entrypoint declared by the addon. During an explicitly documented common initialization phase, Thaumic Legacy discovers addon entrypoints and gives each contributor access to the approved registration surface. Minecraft-owned registries follow Fabric and Minecraft lifecycle rules.
3. Where a data format is approved, addons can contribute content such as research definitions or alchemy recipes through namespaced data resources. Code registration is reserved for extension points that need behavior. Each extension point documents its phase, ownership, and whether it participates in data reload.
4. Validate duplicate IDs, unknown references, dependency cycles where relevant, and invalid definitions before publishing contributions. Diagnostics should identify the addon and content ID. Define a deterministic policy for conflicts and failed reloads before exposing a stable contract.
5. Server-side systems resolve and execute registered gameplay content. Any client-only contribution uses a separately documented client lifecycle. Addon authors test the declared version range and both environments their addon supports.

## Versioning and compatibility

- Publish a supported addon API version and document its relationship to the core mod versions accepted by Fabric. Decide release numbering, version discovery, and artifact layout before stabilizing the first contract.
- Treat an explicitly documented surface as the compatibility boundary. Track source, binary, and behavioral compatibility, including stable IDs, data formats, and lifecycle ordering.
- Prefer additive evolution. Deprecate older behavior with a documented migration path and release window before any planned breaking change. Provide data migration where changes affect existing saves.
- Verify compatibility with an independent addon that compiles against the published contract and runs against the supported core versions. Include startup and reload failures in the test plan where registration participates in those lifecycles.

## AE2 and Create integration

- Ship separate integration addons. Each bridge declares its dependency on Thaumic Legacy and its target mod, and owns that target's API references and external version matrix. The core mod and its public contracts stay free of AE2 and Create dependencies and types.
- Prefer approved data contributions for recipe compatibility. Use a bridge adapter when an integration requires behavior such as AE2 storage or automation, or Create processing. Design conversion, ownership, and error handling with each bridge so repeated or failed operations preserve gameplay state.
- Verify the core without either external mod. For each bridge, verify its supported dependency combination, client and dedicated-server behavior where applicable, and Fabric's dependency error when a required target mod is absent.
