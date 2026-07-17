<img src="assets/banner.png" alt="Dungeons, Dragons &amp; Crafting - dice, character sheets, and a Game Master who runs the world. Minecraft 26.1.2, Fabric and NeoForge." width="100%">

# DDC (Dungeons, Dragons & Crafting)

[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-blue.svg)](https://minecraft.net)
[![Loader](https://img.shields.io/badge/Loader-Fabric%20%7C%20NeoForge-purple.svg)](https://modrinth.com)
[![Java](https://img.shields.io/badge/Java-25-red.svg)](https://oracle.com/java)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![CI](https://github.com/redstone-md/DDC/actions/workflows/ci.yml/badge.svg)](https://github.com/redstone-md/DDC/actions/workflows/ci.yml)
[![Framework](https://img.shields.io/badge/Framework-MCAF%20v1.2-success.svg)](https://mcaf.managed-code.com)

**DDC** is a Minecraft Java Edition mod that merges tabletop roleplaying rules and storytelling (like Dungeons & Dragons) into Minecraft's 3D voxel world. It targets version **26.1.2** with native support for both **Fabric** and **NeoForge**.

DDC introduces an **asymmetric gameplay model**. Players explore the world as heroic characters with D&D classes, spell slots, and dice checks, while a **Game Master (GM)** controls the environment, possesses monsters, triggers sounds, and narrates the adventure in real-time.

> ### Status: 1.9.2
>
> **This page describes the full design. The released mod is smaller.** Shipping today: the rules
> engine, `/roll`, character sheets whose hit points are the player's real health, attack rolls
> against armour class, spells with slots, class mechanics, ability checks, classes, races, spells and
> encounters from data packs, the GM's wand, mob possession, world control, and GM narration.
> The PRD is built: dice tumble in the world, the action wheel is on `R` so nothing has to be typed,
> the sheet is on `C`, the GM panel is on `G`, the OBS overlay is a browser source you can paste a URL
> into, Twitch chat votes, and a natural 20 slows the world down and grades the screen gold.
> Everything the mod says is translated into English and Russian. Not built: Modrinth publishing —
> that is the repository owner's decision to make, not mine.
>
> [**CHANGELOG.md**](CHANGELOG.md) lists exactly what is in the release and what is not. Sections
> below marked _(planned)_ are design intent, not behaviour.

🇷🇺 **[Нажмите здесь, чтобы прочитать README на русском языке.](file:///d:/projects/DDC/README.RU.md)**

---

## 🗺️ Repository Navigation & Documentation Map

- 📜 **Release**:
  - [CHANGELOG.md](CHANGELOG.md) — what 1.0.0 ships, and what it deliberately does not.
  - [LICENSE](LICENSE) — MIT; the built-in rules data pack is SRD 5.1 under CC-BY-4.0.
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

## 🎲 What 1.9.2 actually does

| Command | Who | What |
|---|---|---|
| `/roll <expr> [advantage\|disadvantage]` | anyone | Rolls dice; result goes to chat and the roll log of everyone within 32 blocks. |
| `/roll <expr> ... hidden` | Game Master | Rolls privately; nobody else sees the number. |
| `/ddc sheet` | anyone | Shows your class, level, hit points, proficiency, and abilities. |
| `/ddc class <id>` | anyone | Picks a class from any loaded data pack. |
| `/ddc race <id>` | anyone | Picks a race; its ability bonuses land on your sheet. |
| `/ddc cast <spell> <target>` | anyone | Spends a slot, rolls damage in public, rolls the target's save. |
| `/ddc rest` | anyone | A long rest: spell slots and hit points back. |
| `/ddc check <ability> <dc>` | anyone | An ability check: picking a lock, jumping a gap. Public. |
| `/ddc second-wind` | Fighter | Heals 1d10 + level, once per rest. |
| `/ddc channel-divinity` | Cleric | Turns the undead within 30 feet. |
| `/ddc narrate <text>` | Game Master | Letterboxed cinematic narration on every screen. |
| `/ddc world <change>` | Game Master | Day, night, storm, stop the clock, freeze the party. |
| `/ddc encounter <id>` | Game Master | Sets what the wand will place. The wand still chooses where. |

**Nothing has to be typed.** Press `R` for the wheel: it makes your character, and then it plays
them. A Game Master holding the wand gets the wand's wheel instead — their encounters, one press away.
`C` is your sheet, `G` is the Game Master's panel. The commands below still work, and the wheel sends
them for you.

A **natural 20 slows the whole world** for a moment and grades the screen gold, so the table sees it
happen rather than reading about it in chat.

Attacks are resolved with the SRD's d20 against armour class: a miss cancels the damage and shows a
dodge, and the roll is hidden so only the attacker sees the numbers. The **Game Master's Wand** places encounters (right-click the ground, sneak-right-click to change
which one) and possesses creatures (right-click one, sneak to let go).

A Game Master is any player with Minecraft 26's `COMMANDS_GAMEMASTER` permission (what used to be
operator level 2). The server checks this on every GM action; the client is never asked.

**Add a class, race, spell or encounter with no code** — drop a file into any data pack and
`/reload`. Four directories are scanned across every namespace: `ddc_classes`, `ddc_races`,
`ddc_spells`, `ddc_encounters`.

```json
// data/my_addon/ddc_classes/paladin.json
{
  "name": "Paladin",
  "hit_die": "d10",
  "primary_ability": "strength",
  "saving_throws": ["wisdom", "charisma"]
}
```

---

## 🛡️ Gameplay Perspectives _(design intent; see the status note above)_

### 1. From the Player's Perspective _(partly planned)_

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

### 2. From the Game Master's (GM) Perspective _(narration shipped; the rest planned)_

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

### 3. From the Streamer / Twitch Audience Perspective ("Twitch-Ready") _(planned)_

DDC is built to turn campaigns into interactive streaming content:

- **OBS Browser Overlay**: The client hosts a local WebSocket server that exports player stats, quests, and roll history to OBS. Viewers see live, animated D&D cards on stream with zero setup.
- **Fanfare & Cues**: Natural 20 rolls trigger screen shakes, slow-motion impact filters, and golden particle explosions on players' screens.
- **Chat Interaction**: Viewers can vote on check difficulties or vote to give streamers Advantage or Disadvantage on crucial saves.

---

## 🛠️ Quickstart Developer Setup

To build and compile the project, run:
Requires **JDK 25** — Minecraft 26.1.2 will not run on anything older.

```bash
# Clone the repository
git clone https://github.com/redstone-md/DDC.git
cd DDC

# Build both jars and run the tests
./gradlew build

# Run a developer client
./gradlew :fabric:runClient
./gradlew :neoforge:runClient

# Run a dedicated server
./gradlew :fabric:runServer
./gradlew :neoforge:runServer
```

Jars land in `fabric/build/libs/` and `neoforge/build/libs/`. All unit tests must pass before pushing
to `main`; `./gradlew build` runs them.

Minecraft 26.x ships unobfuscated, so this build declares no mappings and uses the
`loom-no-remap` plugin. See [AGENTS.md](AGENTS.md) for the verified version matrix and the 26.x API
renames that catch people out.
