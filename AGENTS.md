# DDC (Dungeons, Dragons & Crafting) - Root AGENTS.md

Welcome, AI Agent. This is the root configuration and instruction file for the DDC (Dungeons, Dragons & Crafting) Minecraft mod. DDC is a multi-loader, multi-version mod targeting Minecraft 26.1.2 as the primary version, with native support for Fabric and NeoForge.

You must follow the instructions, architecture rules, and validation gates defined here.

---

## 1. Project Overview & Tech Stack
DDC integrates D&D tabletop rules and roleplay directly into the Minecraft voxel engine. It supports:
- **Minecraft Version**: `26.1.2`
- **Language**: Java 21 (record patterns, pattern matching for switch, modern APIs)
- **Build System**: Gradle (Multi-project setup with `common`, `fabric`, and `neoforge` modules)
- **Target Mod Loaders**:
  - Fabric (utilizing Fabric API)
  - NeoForge (utilizing modern NeoForge deferred registers and event bus)
- **Core Library Dependencies**:
  - Architectury API (for cross-loader abstractions of items, blocks, entities, networking, and registries)

---

## 2. Workspace Layout
```
DDC/
├── AGENTS.md                   # This file (Root AI rules)
├── build.gradle                # Root gradle build file
├── settings.gradle             # Project structure settings
├── common/                     # Common logic, entities, rules, data-driven engine
│   ├── AGENTS.md               # Common subproject rules
│   └── src/main/...            # Shared java code & resources
├── fabric/                     # Fabric-specific implementation
│   ├── AGENTS.md               # Fabric local rules
│   └── src/main/...            # Fabric entrypoints & mixins
├── neoforge/                   # NeoForge-specific implementation
│   ├── AGENTS.md               # NeoForge local rules
│   └── src/main/...            # NeoForge entrypoints & handlers
└── docs/                       # Comprehensive documentation
    ├── PRD.md                  # Product Requirement Document
    ├── ARCHITECTURE.md         # Technical architecture details
    └── ADR/                    # Architectural Decision Records
        ├── 0001-multi-loader-architecture.md
        ├── 0002-data-driven-ruleset.md
        └── 0003-gm-networking-sync.md
```

---

## 3. Developer Commands
When modifying the codebase, use these commands for setup, verification, and building:

| Command | Action |
|---|---|
| `./gradlew build` | Build all jars, run all unit tests and static analysis. |
| `./gradlew test` | Execute unit tests across common and loader-specific subprojects. |
| `./gradlew :fabric:runClient` | Launch Minecraft 26.1.2 client under Fabric development environment. |
| `./gradlew :neoforge:runClient` | Launch Minecraft 26.1.2 client under NeoForge development environment. |
| `./gradlew checkstyleMain` | Verify code style standards. |

---

## 4. Verification & Quality Gates
You must verify your changes prior to proposing them to the user:
1. **Compilation**: Code must compile clean with Java 21 without deprecation warnings (unless wrapping legacy Minecraft APIs).
2. **Testing**: All unit tests must pass. When adding new features (like spell mechanics or combat calculations), write corresponding unit tests in `common/src/test/java`.
3. **Mixin Safety**: Ensure mixin injections are narrow and highly targeted. Avoid overwrite mixins unless absolutely necessary.
4. **Data Syncing Validation**: Custom network payloads must be validated on the server. Never trust player client payloads (e.g. validating roll results or GM tool commands).

---

## 5. Architectural Standards & Coding Style

### Common Logic vs. Loader Logic
- **90% of the code must live in `common`**. This includes character attributes, spell registry definitions, D&D math calculators, data pack deserializers, and UI screens (using shared rendering interfaces).
- **No direct references to Fabric or NeoForge classes** inside `common`. Use Architectury API abstractions or custom interfaces where Architectury is insufficient.
- Use Gradle's `compileOnly` dependencies on the loader APIs in `common` only if absolutely necessary, but prefer abstraction interfaces.

### Modern Java 21 Usage
- Utilize `record` classes for immutable data structures (e.g. `DiceRollResult`, `SpellSlotUpdatePacket`).
- Use pattern matching for `instanceof` and modern `switch` expressions to clean up entity capability checks.

### Network Protocol Security
- GM commands (possessing mobs, editing player sheets, triggering global narrative) must contain server-side checks verifying that the sending player is indeed marked as a GM (Operator level >= 2 or specified by permission plugins).

---

## 6. Maintainability Limits
- **Max Class Size**: 500 lines (except registry bootstrap classes). Split complex systems into specialized services.
- **Max Method Size**: 60 lines. Extract complex arithmetic (like D&D combat AC/saving throw resolution) into clean, named helper methods.
- **No Raw Threading**: Use Minecraft's scheduled executor service (`MinecraftServer#execute` or `MinecraftClient#execute`) for thread-safe operations on the main loop.
