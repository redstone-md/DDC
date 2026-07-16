# DDC NeoForge Module - Local AGENTS.md

This local `AGENTS.md` governs development in the `neoforge/` subproject. This module contains NeoForge-specific bootstrap, events registration, event bus setups, and NeoForge-specific capabilities.

---

## 1. Architectural Boundaries
- **NeoForge Bootstrap**: `@Mod("ddc")` on `com.ddc.neoforge.DDCNeoForge`, whose constructor takes
  `(IEventBus modBus, Dist dist)`. It calls the shared bootstrap and, only when `dist.isClient()`,
  wires the client bootstrap through `FMLClientSetupEvent`. Client-only classes must never be
  referenced on a dedicated server: NeoForge 26.x no longer strips `@OnlyIn` members, so the guard is
  the call site, not the annotation.
- **`loom.platform=neoforge`** lives in `neoforge/gradle.properties` and is what tells Loom this is
  the NeoForge platform. Removing it breaks configuration.
- **Deferred Registers**: Register NeoForge-specific handlers or objects using NeoForge's `DeferredRegister` framework if Architectury's registries are not used.
- **Event Listeners**: Prefer `@SubscribeEvent` on an event handler class. Keep client-only listeners registered strictly to the Client Event Bus to prevent dedicated server crashes.

---

## 2. Coding Conventions

### NeoForge Events & Thread Safety
- NeoForge is highly multithreaded. Respect the event lifecycle stages.
- Registries must be updated *only* during the registry events.
- Client rendering code (gui overlays, entity renderers) must be registered via `RegisterGuiLayersEvent` and `EntityRenderersEvent.RegisterRenderers` on the mod event bus.

### Character data is not a capability here
Character sheets are **not** stored with NeoForge capabilities or attachments. They live in the
world's vanilla `SavedData` (`com.ddc.character.CharacterSheetStore`), which is one implementation
shared with Fabric instead of two, and which survives death and dimension changes because it was
never tied to an entity. Do not add a capability for sheet data; extend the store instead.

Syncing is handled in common by `CharacterService` on join, respawn, and every write.
