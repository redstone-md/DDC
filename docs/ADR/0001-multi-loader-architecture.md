# Architectural Decision Record

## Title: 0001-multi-loader-architecture

## Status
Approved

## Context
DDC is required to support multiple mod loaders, specifically Fabric as the primary target, but must also run on NeoForge to maximize popularity on Modrinth. Maintaining two independent codebases for Fabric and NeoForge is expensive, error-prone, and unsustainable for AI development agents. 

We need a framework or project structure that maximizes code reuse (sharing items, networking, D&D calculations, and GUI layouts) while allowing loader-specific bootstrapping (e.g. NeoForge event registrations, Fabric entrypoints, loader-specific mixins).

## Decision
We will use a **Multi-Project Gradle Structure** powered by **Architectury API** and Gradle Loom.
- All core game logic, D&D engines, models, interfaces, packet classes, and GUI overlays will live in the `:common` subproject.
- Loader-specific adapters and registry hooks will live in `:fabric` and `:neoforge` subprojects.
- We will target **Minecraft Java Edition 26.1.2** using **Java 21**.

## Consequences
- **Pros**:
  - Over 90% of the codebase will be shared between Fabric and NeoForge.
  - Changes to D&D logic only need to be written once in the `:common` subproject.
  - Simplifies testing since unit tests can run directly against the shared common project.
- **Cons**:
  - Requires maintaining Architectury API as a dependency, which could delay updates to future Minecraft versions if Architectury takes time to update.
  - Adding new registry objects requires wrapping them in Architectury's `RegistrarManager`.
