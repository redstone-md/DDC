# DDC Common Module - Local AGENTS.md

This local `AGENTS.md` governs development in the `common/` subproject. All shared code (items, blocks, entity properties, UI screens, network payload interfaces) lives here.

---

## 1. Architectural Boundaries
- **No Loader Imports**: You must never import any classes containing `net.fabricmc` or `net.neoforged` in this directory.
- **Architectury Registry Abstractions**: Register items, blocks, and entity attributes using Architectury's `RegistrarManager` or deferred registers wrapper to ensure registration maps cleanly to both loaders.
- **D&D Engine Isolation**: Keep the core D&D mechanics (dice rolls, modifier calculations, character leveling) isolated from Minecraft code as much as possible to allow pure JUnit testing without loading the Minecraft server jar.

---

## 2. Coding Conventions

### Data-Driven Registries
- Spell definitions, D&D character classes, and custom race bonuses are read from JSON files inside `data/ddc/ddc_classes/`, `data/ddc/ddc_spells/`, etc.
- Use Gson or Jackson (integrated into Minecraft's data pack deserialization framework via `Codec`) to parse these JSON files.
- Code should react dynamically when a data pack changes (e.g. reload listeners).

### Network Payloads
- Custom packet payloads must implement the standard Architectury networking interfaces.
- Define a separate record for each payload type.
- Keep packet sizes small. Compress arrays and strings if sending large GM narration buffers.
