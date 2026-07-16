# DDC (Dungeons, Dragons & Crafting) - Root AGENTS.md

Welcome, AI Agent. This is the root configuration and instruction file for the DDC (Dungeons, Dragons & Crafting) Minecraft mod. DDC is a multi-loader, multi-version mod targeting Minecraft 26.1.2, with native support for Fabric and NeoForge.

You must follow the instructions, architecture rules, and validation gates defined here.

---

## 1. Project Overview & Tech Stack
DDC integrates D&D tabletop rules and roleplay directly into the Minecraft voxel engine. It supports:
- **Minecraft Version**: `26.1.2`
- **Language**: Java 25. Minecraft 26.1.2 requires it (`piston-meta` reports `javaVersion.majorVersion=25`); an earlier draft of this file said Java 21, which the game will not run on. The version lives in `gradle.properties` as `java_version`.
- **Build System**: Gradle 9 (multi-project: `common`, `fabric`, `neoforge`). The wrapper is committed; use `./gradlew`.
- **Target Mod Loaders**:
  - Fabric (utilising Fabric API)
  - NeoForge (utilising the NeoForge mod bus)
- **Core Library Dependencies**:
  - Architectury API (cross-loader abstractions for registries, networking, events, reload listeners)

### Minecraft 26.x is unobfuscated — this changes the build
Do not "fix" the build by adding mappings. For 26.x:
- Mojang publishes **no mappings** (the version manifest has only `client`/`server`), and Fabric's intermediary for 26.1.2 resolves to the no-op `0.0.0`.
- The build therefore declares **no `mappings` dependency** and uses the **`dev.architectury.loom-no-remap`** plugin rather than `dev.architectury.loom`. There is no `remapJar`; the shadow jar is what ships.
- `loom.silentMojangMappingsWarning()` no longer exists.

### Version matrix (verified against upstream maven metadata; do not guess these)
| Component | Version | Note |
|---|---|---|
| Minecraft | `26.1.2` | Java 25, unobfuscated |
| Fabric Loader | `0.19.3` | |
| Fabric API | `0.155.0+26.1.2` | must carry the `+26.1.2` suffix |
| NeoForge | `26.1.2.81` | NeoForge now tracks the Minecraft version |
| Architectury API | `20.0.9` | **21.x targets MC 26.2 and will not work here** |
| Architectury Loom | `1.17.491` | requires Gradle 9 |
| architectury-plugin | `3.5.169` | |

### API renames in 26.x that catch out training data
Verify against the jar rather than memory; several familiar names moved:
- `ResourceLocation` → **`net.minecraft.resources.Identifier`**
- `net.minecraft.Util` → **`net.minecraft.util.Util`**
- `GuiGraphics` → **`net.minecraft.client.gui.GuiGraphicsExtractor`** (`text(...)`, `fill(...)`)
- Operator levels → a **named permission system**: `net.minecraft.server.permissions.Permissions`, `PermissionSet`, `PermissionCheck.Require`. `CommandSourceStack.hasPermission(int)` is gone.
- `ServerPlayer.getServer()` is gone; use `player.level().getServer()`.
- `pack.mcmeta` uses `min_format`/`max_format`, not `pack_format`. For 26.1.2 the data pack format is `101`.

---

## 2. Workspace Layout
```
DDC/
├── AGENTS.md                   # This file (Root AI rules)
├── LICENSE                     # MIT; the built-in data pack is SRD 5.1 CC-BY-4.0
├── build.gradle                # Root gradle build file
├── settings.gradle             # Project structure settings
├── gradle.properties           # Single source of truth for every version
├── common/                     # Common logic, entities, rules, data-driven engine
│   ├── AGENTS.md               # Common subproject rules
│   └── src/main/...            # Shared java code & resources
├── fabric/                     # Fabric-specific implementation
├── neoforge/                   # NeoForge-specific implementation
└── docs/                       # PRD, ARCHITECTURE, ADRs
```

---

## 3. Developer Commands

| Command | Action |
|---|---|
| `./gradlew build` | Build both jars and run all unit tests. |
| `./gradlew test` | Execute unit tests (they live in `common`). |
| `./gradlew :fabric:runClient` | Launch the Fabric development client. |
| `./gradlew :neoforge:runClient` | Launch the NeoForge development client. |
| `./gradlew :fabric:runServer` | Launch a Fabric dedicated server. |
| `./gradlew :neoforge:runServer` | Launch a NeoForge dedicated server. |

There is no `checkstyleMain` task; an earlier draft of this file claimed one. Style is enforced by review and by `-Xlint:all` on every compile.

**Known dev-environment quirk**: the dev server's console cannot execute commands — even vanilla `list`/`help`/`stop` fail with "An unexpected error occurred trying to execute that command". This is not caused by the mod (it reproduces with the mod's commands unregistered). Verify command behaviour with the tests in `common/src/test/java/com/ddc/command/` or a real client, not the dev console.

---

## 4. Verification & Quality Gates
You must verify your changes before proposing them:
1. **Compilation**: `./gradlew build` must pass clean under Java 25.
2. **Testing**: all unit tests must pass. New rules (spells, combat maths) need tests in `common/src/test/java`. The rules engine under `com.ddc.core` is deliberately Minecraft-free and tests without any bootstrap; tests that do touch Minecraft call `MinecraftBootstrapExtension.bootstrap()`.
3. **Boot both loaders**: a change to registration, networking, or data packs must be booted on both `:fabric:runServer` and `:neoforge:runServer`. The log line `Loaded N character class(es)` proves the data pack pipeline still works.
4. **Mixin Safety**: keep injections narrow. Avoid overwrite mixins. (There are no mixins yet; the mod has needed none.)
5. **Data Syncing Validation**: custom payloads are validated server-side. Never trust a client payload.

---

## 5. Architectural Standards & Coding Style

### Common Logic vs. Loader Logic
- **90% of the code must live in `common`.** Today the loader modules hold only a bootstrap class each.
- **No direct references to Fabric or NeoForge classes** inside `common`, other than Fabric's `@Environment` annotation, which Architectury translates for NeoForge.
- The rules engine (`com.ddc.core`) imports **no Minecraft at all**, so it can be tested without the game jar. Keep it that way: codecs and payloads for its types live in `com.ddc.rules` and `com.ddc.network`.

### Modern Java Usage
- Use `record` classes for immutable data (`DiceRollResult`, payloads, `CharacterSheet`).
- Use pattern matching and modern `switch` expressions.

### Network Protocol Security
- GM commands (possessing mobs, editing sheets, narrating) must check server-side that the sender is a GM. The single gate is `com.ddc.gm.GameMasters`, which requires `Permissions.COMMANDS_GAMEMASTER` (the 26.x equivalent of operator level 2).
- A Brigadier `requires` is **not** a security boundary; it only shapes the command tree. Always re-check in the handler.

---

## 6. Maintainability Limits
- **Max Class Size**: 500 lines (except registry bootstrap classes). Split complex systems into specialised services.
- **Max Method Size**: 60 lines. Extract complex arithmetic into named helper methods.
- **No Raw Threading**: use `MinecraftServer#execute` / `MinecraftClient#execute`, or Architectury's `context.queue(...)` when handling a payload off the netty thread.
