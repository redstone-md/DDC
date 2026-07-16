# DDC Common Module - Local AGENTS.md

This local `AGENTS.md` governs development in the `common/` subproject. All shared code (items, blocks, entity properties, UI screens, network payload interfaces) lives here.

---

## 1. Architectural Boundaries
- **No Loader Imports**: never import `net.fabricmc` or `net.neoforged` classes here, with one
  exception: Fabric's `@Environment`/`EnvType` annotations mark client-only classes, and Architectury
  translates them for NeoForge. Nothing else from the loader may be used.
- **`com.ddc.core` imports no Minecraft at all.** That is what lets the rules engine be unit tested
  without the game jar, and it is the rule most easily broken by accident. Anything that needs an
  `Identifier`, a `Codec`, or a payload belongs in `com.ddc.rules`, `com.ddc.character`, or
  `com.ddc.network` — not in `core`. Codecs for core types live in `com.ddc.rules.DDCCodecs`.
- **Architectury Registry Abstractions**: Register items, blocks, and entity attributes using Architectury's `RegistrarManager` or deferred registers wrapper to ensure registration maps cleanly to both loaders.
- **D&D Engine Isolation**: Keep the core D&D mechanics (dice rolls, modifier calculations, character leveling) isolated from Minecraft code as much as possible to allow pure JUnit testing without loading the Minecraft server jar.

---

## 2. Coding Conventions

### Data-Driven Registries
- Character classes are read from `data/<namespace>/ddc_classes/` across **every** loaded namespace.
  `ddc_spells` and `ddc_races` are specified but not implemented yet.
- Parse with Mojang `Codec`s (`RecordCodecBuilder`), not hand-written Gson. A codec failure is
  reported against the offending file by Minecraft itself, which is the addon validator ADR-0002
  asks for; hand-rolled parsing loses that.
- `CharacterClassRegistry` extends `SimpleJsonResourceReloadListener` and is registered through
  Architectury's `ReloadListenerRegistry`, so `/reload` picks up changes.

### Network Payloads
- One `record` per payload, implementing `CustomPacketPayload` with a `StreamCodec`.
- Architectury splits registration by physical side: the **server** calls `registerS2CPayloadType`,
  the **client** registers a receiver (which registers the type for it). Doing both on one side
  registers the type twice. See `DDCNetwork` and `DDCClient`.
- Keep payloads small, and prefer sending a seed over sending a result: `DiceResultPayload` sends the
  seed and the client replays the roll, which stays a few bytes no matter how many dice were thrown.
- Cap every incoming string (`ByteBufCodecs.stringUtf8(max)`); never trust a length from the wire.
- Payloads arrive on the netty thread. Use `context.queue(...)` before touching game state.
