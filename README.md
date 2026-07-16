# DDC (Dungeons, Dragons & Crafting)

[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-blue.svg)](https://minecraft.net)
[![Loader](https://img.shields.io/badge/Loader-Fabric%20%7C%20NeoForge-purple.svg)](https://modrinth.com)
[![Java](https://img.shields.io/badge/Java-21-red.svg)](https://oracle.com/java)
[![Framework](https://img.shields.io/badge/Framework-MCAF%20v1.2-success.svg)](https://mcaf.managed-code.com)

**DDC** is a Minecraft Java Edition mod that merges tabletop roleplaying rules and storytelling (like Dungeons & Dragons) into Minecraft's 3D voxel world. It targets version **26.1.2** with native support for both **Fabric** and **NeoForge**.

DDC introduces an **asymmetric gameplay model**. Players explore the world as heroic characters with D&D classes, spell slots, and dice checks, while a **Game Master (GM)** controls the environment, possesses monsters, triggers sounds, and narrates the adventure in real-time.

🇷🇺 **[Нажмите здесь, чтобы прочитать README на русском языке.](file:///d:/projects/DDC/README.RU.md)**

---

## 🗺️ Repository Navigation & Documentation Map

- 📑 **Root Instructions**: 
  - [AGENTS.md](file:///d:/projects/DDC/AGENTS.md) — Root AI development rules, workspace layout, and compiler properties.
- 📁 **Module Instructions**:
  - [common/AGENTS.md](file:///d:/projects/DDC/common/AGENTS.md) — Shared gameplay, mathematical calculations, and loader isolation.
  - [fabric/AGENTS.md](file:///d:/projects/DDC/fabric/AGENTS.md) — Fabric API integration, loader bootstrap, and Mixin security.
  - [neoforge/AGENTS.md](file:///d:/projects/DDC/neoforge/AGENTS.md) — NeoForge event handling, registry bus, and capabilities.
- 📘 **Product & Architecture Specs**:
  - [docs/PRD.md](file:///d:/projects/DDC/docs/PRD.md) — Product Requirement Document detailing features, UI, combat, and publishing rules.
  - [docs/ARCHITECTURE.md](file:///d:/projects/DDC/docs/ARCHITECTURE.md) — Technical layout, packet sync protocol, rendering pipelines, and Twitch sockets.
- 🏛️ **Architectural Decision Records (ADRs)**:
  - [0001-multi-loader-architecture.md](file:///d:/projects/DDC/docs/ADR/0001-multi-loader-architecture.md) — Choice of Gradle multi-project structures and Architectury API.
  - [0002-data-driven-ruleset.md](file:///d:/projects/DDC/docs/ADR/0002-data-driven-ruleset.md) — Data Packs for classes, spells, and code-free addon support.
  - [0003-gm-networking-sync.md](file:///d:/projects/DDC/docs/ADR/0003-gm-networking-sync.md) — Validation layers and C2S inputs compression for mob possession.

---

## 🛡️ Gameplay Perspectives

### 1. From the Player's Perspective

Players experience the mod as an immersive 3D RPG:

1. **Character Sheet & HUD**:
   - Hearts and vanilla armor bars are replaced by a sleek, glassmorphic HUD displaying numeric **HP** (e.g., `45 / 45`), a **Class/Level** tag (e.g., `FIGHTER - LVL 5`), and **Armor Class (AC)** (e.g., `AC: 16`).
   - Spellcasters see active **Spell Slots** markers directly above their hotbar.
   - Pressing **`C`** opens the full Character Sheet for stats (STR, DEX, CON, INT, WIS, CHA) and gear management.
2. **Dice Checks in the World**:
   - Performing locks picks, jumping gaps, or breaking reinforced doors triggers a **d20 roll**. A 3D model of the die falls and bounces off the blocks in front of the player.
   - Nearby players see the rolling die. Once settled, the modifier is added and printed in chat (e.g., `[Player] Rolled a d20: 17 + 3 (DEX) = 20! (Success)`).
3. **Class Actions & Magic**:
   - **Fighters** use stamina to perform melee combat maneuvers.
   - **Wizards** must prepare spells in a *Spellbook* and cast them using *Wands* or *Staffs*, consuming spell slots.
   - **Rogues** deal sneak attack damage when striking from behind or in stealth.

---

### 2. From the Game Master's (GM) Perspective

The GM does not play survival; they act as a director/storyteller:

1. **Spectator UI & GM Wand**:
   - GMs fly around in invisible spectator mode. Players are outlined with glowing indicators showing their health cards and targets.
   - Right-clicking blocks with the **GM Wand** opens radial action menus (e.g., spawn encounter, trigger traps, link triggers, lock chests).
2. **The Control Center (`G` key)**:
   - **Narrator**: Type narrative text (e.g., *"The air turns freezing cold as a low hum grows..."*). Sending it displays cinematic widescreen letterbox subtitles on the players' screens alongside atmospheric soundscapes (screeches, wind, thunder).
   - **Encounter Spawner**: Instantly place predefined mob groups (e.g., "Goblin Ambush") using a placement preview grid.
   - **Party Editor**: Hand out XP, restore spell slots, edit attributes, or prompt players to perform active rolls.
3. **Mob Possession**:
   - The GM can right-click any monster with the GM Wand to take direct control of it.
   - The camera snaps to the mob's eyes. The GM moves and attacks as the monster, using a custom boss bar and custom skill hotbars (e.g., "Flame Breath" for dragons).

---

### 3. From the Streamer / Twitch Audience Perspective ("Twitch-Ready")

DDC is built to turn campaigns into interactive streaming content:

- **OBS Browser Overlay**: The client hosts a local WebSocket server that exports player stats, quests, and roll history to OBS. Viewers see live, animated D&D cards on stream with zero setup.
- **Fanfare & Cues**: Natural 20 rolls trigger screen shakes, slow-motion impact filters, and golden particle explosions on players' screens.
- **Chat Interaction**: Viewers can vote on check difficulties or vote to give streamers Advantage or Disadvantage on crucial saves.

---

## 🛠️ Quickstart Developer Setup

To build and compile the project, run:
```bash
# Clone the repository
git clone https://github.com/redstone-md/DDC.git
cd DDC

# Setup Gradle wrapper and build dependencies
./gradlew build

# Run Fabric Developer Client
./gradlew :fabric:runClient

# Run NeoForge Developer Client
./gradlew :neoforge:runClient
```
All code styles are validated via `./gradlew checkstyleMain`. All unit tests must pass before pushing to `main`.
