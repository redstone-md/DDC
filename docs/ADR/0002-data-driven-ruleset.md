# Architectural Decision Record

## Title: 0002-data-driven-ruleset

## Status
Approved

## Context
Dungeons & Dragons is a trademark of Wizards of the Coast (WotC), and specific lore, characters, and proprietary monster descriptions (e.g. Beholder) represent WotC's "Product Identity" which must not be shipped inside our mod binaries. However:
- General RPG tropes, classes, stats, and dice systems are not proprietary.
- The official D&D 5e System Reference Document (SRD 5.1) is licensed under Creative Commons (CC-BY-4.0), making the core rule structures completely safe to utilize.
- We want to allow users and other mod authors to build modular **Addons** for our mod (e.g., adding custom homebrew classes or custom spellbooks) without modifying our source code or recompiling the mod.

Hardcoding attributes and rules directly in Java classes would create severe limitations for licensing compliance, house rules, and addon compatibility.

## Decision
We will build DDC as a **Generic RPG rules engine** that dynamically loads classes, races, spells, and XP levels via **Minecraft Data Packs** across all active namespaces.
- The mod will ship with a built-in datapack that contains standard 5e CC-BY SRD rules under the `ddc` namespace.
- Third-party creators can publish independent DDC addons simply by placing their custom JSON files under their own mod's namespaces (e.g., `data/myaddon/ddc_classes/paladin.json`).
- Our code will scan all active namespaces on datapack load/reload, automatically parsing and registering addon-provided classes and spells.

## Consequences
- **Pros**:
  - **Addon Ecosystem**: Third-party developers can create fully-fledged DDC additions without writing any Java/Kotlin code or declaring compile-time Java dependencies.
  - **IP Compliance**: Keeps the core mod JAR generic and fully compliant with trademark and copyright boundaries by loading rules from external assets.
  - **Runtime Reloading**: Server hosts can tune attributes, spell coefficients, or leveling speed instantly via `/reload`.
- **Cons**:
  - Datapack scanning across all namespaces increases the serialization workload during server startup, requiring efficient deserialization codecs.
  - Addon authors must ensure their JSON format strictly conforms to DDC's schema definitions; we must write a validator to log explicit errors for malformed JSONs.
