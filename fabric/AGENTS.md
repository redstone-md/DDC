# DDC Fabric Module - Local AGENTS.md

This local `AGENTS.md` governs development in the `fabric/` subproject. This module contains Fabric-specific bootstrap classes, Mixins, entrypoint declarations, and Fabric API integrations.

---

## 1. Architectural Boundaries
- **Fabric Entrypoints**: Bootstrap class must implement `ModInitializer` (main), `ClientModInitializer` (client), and/or `PreLaunchEntrypoint` if needed.
- **Mixins**: none exist yet — the mod has needed none. If one becomes necessary, place Fabric-specific
  mixins in `fabric/src/main/resources/ddc.mixins.json` and shared ones in
  `common/src/main/resources/ddc-common.mixins.json`, and register the config in `fabric.mod.json`.
- **No access widener**: there is no `ddc.accesswidener`. If you add one, note that Minecraft 26.x is
  unobfuscated, so its header namespace is `official`, not `named`, and it must be declared in
  `fabric.mod.json` **and** present in the jar or the game refuses to start.
- **Fabric API**: Utilize Fabric API services (e.g. `ServerPlayNetworking`, `ClientPlayNetworking`, `CommandRegistrationCallback`) to map Architectury abstractions where required.

---

## 2. Coding Conventions

### Mixin Integrity
- Always supply a description comment for Mixins detailing *why* the injection is necessary.
- Use `@Inject` with `@At("HEAD")`, `@At("RETURN")`, or `@At("TAIL")`. Avoid `@Redirect` as it conflicts with other mods (e.g. optimization mods like Sodium/Lithium).
- Use `remap = true` unless injecting into a library class that is not remapped by Loom.

### Mod Metadata
- The Fabric configuration is defined in `src/main/resources/fabric.mod.json`.
- Placeholders (`${mod_id}`, `${mod_version}`, ...) are expanded by `processResources` in the root
  `build.gradle` from `gradle.properties`. Never hardcode a version here.
- Maintain accurate dependencies: `fabricloader`, `minecraft` (`26.1.2`), `java` (>=25), `fabric-api`,
  and `architectury`.
- Entrypoints: `main` -> `com.ddc.fabric.DDCFabric`, `client` -> `com.ddc.fabric.DDCFabricClient`.
  Both do nothing but call the shared bootstrap; keep it that way.
