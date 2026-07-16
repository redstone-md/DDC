# DDC Fabric Module - Local AGENTS.md

This local `AGENTS.md` governs development in the `fabric/` subproject. This module contains Fabric-specific bootstrap classes, Mixins, entrypoint declarations, and Fabric API integrations.

---

## 1. Architectural Boundaries
- **Fabric Entrypoints**: Bootstrap class must implement `ModInitializer` (main), `ClientModInitializer` (client), and/or `PreLaunchEntrypoint` if needed.
- **Mixins**: Place all Fabric-specific Mixins in `fabric/src/main/resources/ddc.mixins.json`. Shared mixins should live in `common/src/main/resources/ddc-common.mixins.json` and refactored properly.
- **Fabric API**: Utilize Fabric API services (e.g. `ServerPlayNetworking`, `ClientPlayNetworking`, `CommandRegistrationCallback`) to map Architectury abstractions where required.

---

## 2. Coding Conventions

### Mixin Integrity
- Always supply a description comment for Mixins detailing *why* the injection is necessary.
- Use `@Inject` with `@At("HEAD")`, `@At("RETURN")`, or `@At("TAIL")`. Avoid `@Redirect` as it conflicts with other mods (e.g. optimization mods like Sodium/Lithium).
- Use `remap = true` unless injecting into a library class that is not remapped by Loom.

### Mod Metadata
- The Fabric configuration is defined in `src/main/resources/fabric.mod.json`.
- Ensure version placeholders match the Gradle properties during build.
- Maintain accurate dependencies: `fabricloader`, `minecraft` (version `26.1.2`), `fabric-api`, and `architectury`.
