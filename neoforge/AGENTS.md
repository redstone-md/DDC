# DDC NeoForge Module - Local AGENTS.md

This local `AGENTS.md` governs development in the `neoforge/` subproject. This module contains NeoForge-specific bootstrap, events registration, event bus setups, and NeoForge-specific capabilities.

---

## 1. Architectural Boundaries
- **NeoForge Bootstrap**: The main class must be annotated with `@Mod("ddc")` and listen to the `IEventBus` for lifecycle events (`FMLCommonSetupEvent`, `FMLClientSetupEvent`).
- **Deferred Registers**: Register NeoForge-specific handlers or objects using NeoForge's `DeferredRegister` framework if Architectury's registries are not used.
- **Event Listeners**: Prefer `@SubscribeEvent` on an event handler class. Keep client-only listeners registered strictly to the Client Event Bus to prevent dedicated server crashes.

---

## 2. Coding Conventions

### NeoForge Events & Thread Safety
- NeoForge is highly multithreaded. Respect the event lifecycle stages.
- Registries must be updated *only* during the registry events.
- Client rendering code (gui overlays, entity renderers) must be registered via `RegisterGuiLayersEvent` and `EntityRenderersEvent.RegisterRenderers` on the mod event bus.

### Capabilities System
- D&D Character data is attached to the player using NeoForge's Capability system (if not using shared data structures like Architectury's attachment APIs).
- Sync capability data to client when player joins, changes dimensions, or respawns.
- Verify capability attachment is thread-safe.
