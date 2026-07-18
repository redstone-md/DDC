# DDC (Dungeons, Dragons & Crafting) - Root AGENTS.md

Welcome, AI Agent. This is the root configuration and instruction file for the DDC (Dungeons, Dragons & Crafting) Minecraft mod. DDC is a multi-loader, multi-version mod. `main` targets **Minecraft 1.21.1** (the version where Iron's Spells 'n Spellbooks and L_Ender's Cataclysm live, so DDC installs alongside them); the **`mc-26.1.2`** branch keeps the 26.1.2 build. Both have native support for Fabric and NeoForge.

You must follow the instructions, architecture rules, and validation gates defined here.

---

## 1. Project Overview & Tech Stack
DDC integrates D&D tabletop rules and roleplay directly into the Minecraft voxel engine. `main`
supports:
- **Minecraft Version**: `1.21.1`
- **Language**: Java 21. Minecraft 1.21.1's Java. The version lives in `gradle.properties` as
  `java_version`.
- **Build System**: Gradle 9 (multi-project: `common`, `fabric`, `neoforge`). The wrapper is committed; use `./gradlew`.
- **Target Mod Loaders**:
  - Fabric (utilising Fabric API)
  - NeoForge (utilising the NeoForge mod bus)
- **Core Library Dependencies**:
  - Architectury API (cross-loader abstractions for registries, networking, events, reload listeners)

### 1.21.1 is obfuscated — the build remaps
1.21.1 maps against **official Mojang mappings** layered with **Parchment** parameter names, using the
**`dev.architectury.loom`** plugin. Both loader jars go through `remapJar`. Mod dependencies must be
declared with `modApi`/`modImplementation` (not `api`/`implementation`) or loom leaves intermediary
names (`class_3222`, `class_1937`) leaking into the jar.

> The `mc-26.1.2` branch is the opposite: 26.x ships **unobfuscated**, so it declares **no mappings**,
> uses **`loom-no-remap`**, and the shadow jar ships as-is. Do not port that branch's build back here.

### Version matrix (verified against upstream maven metadata; do not guess these)
| Component | Version | Note |
|---|---|---|
| Minecraft | `1.21.1` | Java 21, Mojang mappings |
| Fabric Loader | `0.16.10` | |
| Fabric API | `0.116.14+1.21.1` | must carry the `+1.21.1` suffix |
| NeoForge | `21.1.238` | |
| Parchment | `2024.11.17` | layered over official mappings |
| Architectury API | `13.0.8` | the 1.21.1 line |
| Architectury Loom | `1.17.491` | requires Gradle 9 |
| architectury-plugin | `3.5.169` | |

### API notes for 1.21.1 (the 26.x → 1.21.1 port already handled these)
The codebase was ported from 26.x; these are the shapes it now uses (the *reverse* of the 26.x
renames, kept here so nobody reintroduces a 26.x name):
- Use **`net.minecraft.resources.ResourceLocation`** (not `Identifier`) and **`net.minecraft.Util`**.
- Use **`GuiGraphics`** with `drawString`/`fill`, and the old
  `EntityRenderer.render(entity, yaw, partialTick, PoseStack, MultiBufferSource, int)` — there is no
  render-state / `SubmitNodeCollector` system.
- The GM gate is **op level 2**: `player.hasPermissions(2)` / `source.hasPermission(2)`. There is no
  named permission system.
- `pack.mcmeta` uses **`pack_format`** (48 for 1.21.1), not `min_format`/`max_format`. Recipe
  ingredients are **`{"item": "..."}`** objects, not bare strings.

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
1. **Compilation**: `./gradlew build` must pass clean under Java 21.
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
- GM commands (possessing mobs, editing sheets, narrating) must check server-side that the sender is a GM. The single gate is `com.ddc.gm.GameMasters`, which requires operator level 2 (`hasPermissions(2)`).
- A Brigadier `requires` is **not** a security boundary; it only shapes the command tree. Always re-check in the handler.

---

## 6. Maintainability Limits
- **Max Class Size**: 500 lines (except registry bootstrap classes). Split complex systems into specialised services.
- **Max Method Size**: 60 lines. Extract complex arithmetic into named helper methods.
- **No Raw Threading**: use `MinecraftServer#execute` / `MinecraftClient#execute`, or Architectury's `context.queue(...)` when handling a payload off the netty thread.
