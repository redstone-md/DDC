# DDC (Dungeons, Dragons & Crafting) - Product Requirement Document (PRD)

## 1. Executive Summary
**DDC (Dungeons, Dragons & Crafting)** is a Minecraft Java Edition modification targeting version **26.1.2** with native support for the **Fabric** and **NeoForge** mod loaders. The core mission of DDC is to seamlessly merge the immersive storytelling and mechanical rules of tabletop role-playing games (like Dungeons & Dragons) into Minecraft's 3D voxel sandbox. 

Unlike traditional RPG mods that merely add stats and items, DDC introduces an **asymmetric gameplay model**. Players experience Minecraft as heroic characters with D&D-style classes, stats, spell slots, and real-time dice rolls, while a designated **Game Master (GM)** controls the world using a custom spectator toolkit to spawn encounters, possess monsters, play soundscapes, and narrate the adventure in real-time.

---

## 2. Product Goals & Vision
- **Tabletop Fidelity in 3D Space**: Bring the randomness, tension, and structure of D&D to life with real 3D dice rolls in the world, visible to other players, and active attribute checks.
- **True Asymmetric Cooperative Play**: Elevate Minecraft adventure maps into live-run campaigns. A single GM can run a dungeon raid for 4-5 players, adjusting the difficulty on the fly.
- **Clean Modrinth Integration**: Package the mod with pristine assets, an easy-to-use client-server setup, clear dependency lists, and clean separation between Common, Fabric, and NeoForge logic.
- **Highly Extensible & Data-Driven**: Make all rules (classes, races, spells, abilities, leveling tables) customizable via standard Minecraft Data Packs so players can write their own rule systems.

---

## 3. User Roles & Experience (UX)

### 3.1. The Player Role
Players experience the game through the lens of their character sheet.

```
+-------------------------------------------------------------+
|  [FIGHTER - LVL 5]  HP: 45/45   AC: 16   Speed: 30ft        |
|  STR: 16 (+3)   DEX: 12 (+1)   CON: 14 (+2)                 |
|  INT:  8 (-1)   WIS: 10 (+0)   CHA: 14 (+2)                 |
|  Spell Slots: [X] [X] [ ] [ ]                               |
+-------------------------------------------------------------+
```

1. **Character Sheet Overlay (HUD)**: 
   - A modern, glassmorphic UI overlay showing Class, Level, Current/Max HP (overriding the vanilla hearts bar), Armor Class (AC), active Spell Slots, and the 6 D&D Attributes (Strength, Dexterity, Constitution, Intelligence, Wisdom, Charisma).
   - Pressing a hotkey (`C` by default) opens the full Character Sheet for equipping gear, assigning skill points, and selecting prepared spells.
2. **Dice System (Interaction)**:
   - Rolling dice is integrated into chat commands (`/roll 1d20+3`) and context actions.
   - Performing a difficult action (e.g., picking a locked chest, disarming a trap, or jumping a massive gap) prompts a **Difficulty Class (DC)** check. A d20 is rolled and rendered in 3D physics floating in the world.
3. **Class-Specific Mechanics**:
   - **Fighter**: Melee weapon combos trigger special maneuvers (e.g., Tripping Attack, Parry). They have resource pools like "Superiority Dice" and active abilities like "Action Surge".
   - **Wizard**: Must craft and write in a *Spellbook* item to prepare spells. Spells are cast using wands/staffs and consume spell slots.
   - **Rogue**: Sneak attacks deal massive bonus damage if the player strikes from behind or while invisible, or if an ally is standing near the target.
   - **Cleric**: Channels divinity to heal players, buff attributes, or turn undead in a radius.

---

### 3.2. The Game Master (GM) Role
Players marked as GMs (via operators, permissions, or custom `/ddc gm` assignments) do not play the standard survival game. They are invisible, can fly, pass through blocks, and possess a completely different UI.

1. **The GM Wand**:
   - A primary interaction tool. Right-clicking blocks opens context menus (e.g., spawn encounter, lock/unlock door, link chest to a trigger, create an invisible wall).
2. **The GM Control Panel**:
   - Accessed via a keybind (`G` by default), this panel provides a command center:
     - **Narrative Overlays**: Type a text message and trigger a cinematic overlay on player screens (e.g., *"The walls begin to tremble as a low rumble echoes from the deep..."*).
     - **Encounter Generator**: Select pre-made monster groups (e.g., "Goblin Ambush", "Skeleton Patrol") and click to spawn them with custom equipment and AI parameters.
     - **World Control**: Instantly toggle day/night, trigger thunder, lock player movement (for dramatic monologues), or slow down time.
3. **Mob Possession**:
   - The GM can right-click any mob (vanilla or modded) using the GM Wand to **possess** it. 
   - While possessing a mob:
     - The GM's camera attaches to the mob's perspective (first-person or third-person).
     - The GM controls the mob's movement and attacks.
     - The mob's stats are scaled up (becoming a Mini-Boss/Boss), and custom ability cooldowns appear on the GM's hotbar (e.g., "Fire Breath" for a dragon, "Web Spray" for a spider).
     - Dying as a possessed mob ejects the GM safely back into spectator mode.

---

## 4. Detailed Feature Specifications

### 4.1. Visual 3D Dice Rendering
- When a roll is made, a 3D model of the corresponding die (d4, d6, d8, d10, d12, d20, d100) is spawned floating in front of the player's camera.
- The die spins and falls onto the block surface (using client-side physics) before settling on a number.
- The resulting number is broadcasted to the chat for players within a 32-block radius (e.g., `[Player] Rolled a d20: 17 + 3 (STR) = 20! (Success)`).
- **GM Mode Rolls**: GMs can choose to make "Hidden Rolls" (rendered only on the GM's screen) or "Public Rolls" (visible to all).

### 4.2. Combat & AC System
- Minecraft's vanilla armor system is bypassed or re-mapped.
- **Armor Class (AC)** is calculated as: `10 + Dexterity Modifier + Armor Base + Shield Bonus`.
- When an entity attacks, a hidden d20 roll is made. If the attack roll meets or exceeds the target's AC, the attack hits; otherwise, it is a "Miss" (triggering a custom visual particle effect like a shield block or dodge sweep).

### 4.3. Data-Driven Rules Engine & Addon Ecosystem
DDC is designed as an open, highly extensible RPG platform. It handles copyright and customization through a clean, decoupled design:
- **Copyright & Open Licensing Compliance**: Tabletop RPG mechanics (such as d20 roll resolution, modifiers, and standard classes/spells) are mechanically safe and legal. DDC's default built-in configuration utilizes the Creative Commons (CC-BY-4.0) D&D 5e System Reference Document (SRD 5.1). No trademarked or proprietary Wizards of the Coast terms (like "Beholder" or "Mind Flayer") are shipped in the core compiled jar.
- **First-Class Addon Ecosystem**: Because rules are loaded via Minecraft's native Data Packs, anyone can build and distribute addons for DDC. Third-party mod developers or players can add new classes, spells, and races without writing Java/Kotlin code. They simply package JSON files under their own namespaces (e.g. `data/my_addon_mod/ddc_classes/paladin.json`).
- **Dynamic Registry Loader**: The core engine automatically scans all loaded data packs and merges them:
  - `data/<namespace>/ddc_classes/`: Instantiates class parameters (base HP, saving throws, leveling milestones).
  - `data/<namespace>/ddc_spells/`: Adds spells, damage equations, visual particle paths, and items/components needed for casting.
  - `data/<namespace>/ddc_races/`: Registers attributes and passive traits (e.g. darkvision, speed, flight).


---

## 5. Modrinth & Publication Strategy

DDC will be published on **Modrinth**, taking full advantage of the platform's features:

1. **Modrinth Metadata**:
   - **Supported Loaders**: Fabric, NeoForge.
   - **Categories**: Adventure, RPG, Magic, Multiplayer.
   - **Environment**: Client & Server required (the client renders the UI, dice, and spectator overlay; the server runs the rules engine, capability storage, and syncs data).
2. **Page & Marketing Assets**:
   - A professionally generated logo, banners, and screenshots showcasing:
     - The custom player HUD and dice rolling.
     - The GM Control Panel in action.
     - A side-by-side comparison of a Player fighting a GM-possessed boss.
   - Markdown description highlighting the asymmetric multiplayer gameplay, target version `26.1.2`, and setup instructions.
3. **Release Packaging**:
   - `ddc-1.0.0-fabric-1.26.1.2.jar` (target version 26.1.2, depends on Fabric API, Architectury API).
   - `ddc-1.0.0-neoforge-1.26.1.2.jar` (target version 26.1.2, depends on Architectury API).
   - A companion template data pack (`ddc-srd-template.zip`) distributed under a free open-source license.

---

## 6. Non-Functional Requirements & Compatibility

### 6.1. Performance & Tick Optimization
- **Entity Possession Overhead**: The server syncs possession by re-routing the player's controller input to the mob entity rather than the player entity. This must execute in `<0.5ms` per tick.
- **Client Renderers**: 3D dice rendering must use efficient instanced rendering (via LWJGL) to ensure it doesn't drop frames, even if multiple players roll dice simultaneously.

### 6.2. Mod Compatibility (Version 26.1.2)
- **Sodium / Iris**: The custom glassmorphic HUD and 3D dice rendering must render on top of Sodium/Iris pipelines without causing texture clipping or breaking shader logic.
- **Roughly Enough Items (REI) / Just Enough Items (JEI)**: Spell scrolls and wands should be discoverable and show custom crafting/spell preparation recipes.
