# Changelog

All notable changes to DDC are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Each GitHub release carries notes generated from that release's commits by
[git-cliff](https://git-cliff.org) (see `cliff.toml`), so they always match what shipped. This file
is written by hand for what the commits cannot say: why a release is shaped the way it is, and what
it deliberately leaves out.

## [1.0.0] - 2026-07-16

The first release: the rules engine, dice, character sheets, a data-driven class registry, and the
foundation of the Game Master role, on Fabric and NeoForge for Minecraft 26.1.2.

This release deliberately ships a smaller mod than the PRD describes. What is here is complete and
tested; what is not here is listed under [Not in this release](#not-in-this-release) rather than
half-built. See that section before filing a bug.

### Added

**Rules engine** (`com.ddc.core`, no Minecraft dependency, unit tested on its own)
- Dice notation parser: `1d20+3`, `8d6`, `1d6+1d4-2`, with bounds on dice count and expression length.
- Deterministic roller. Every roll carries the seed it came from, so a client replays the server's
  roll rather than being told the answer.
- Advantage and disadvantage, which cancel each other out per the SRD and apply only to a single d20.
- Ability scores and modifiers, the proficiency progression, hit points from a class's hit die, and
  armour class including each armour category's Dexterity cap.
- d20 tests (ability checks, saving throws, attack rolls) with natural 20 and natural 1 handling.

**Dice in play**
- `/roll <expression> [advantage|disadvantage]` — rolls, and shows the result in chat and in an
  on-screen roll log for every player within 32 blocks.
- `/roll <expression> ... hidden` — a Game Master's private roll, seen only by them.
- Natural 20s and natural 1s are highlighted in the roll log.

**Characters**
- `/ddc sheet` — your class, level, hit points, proficiency bonus, and the six abilities.
- `/ddc class <id>` — pick a class from any loaded data pack; ids complete from the packs themselves.
- A HUD panel with level, hit points, and ability modifiers, coloured by how hurt you are.
- Sheets are saved with the world and survive death, respawn, and dimension changes. They sync to
  their owner on join, on respawn, and on every change.

**Data-driven classes** (ADR-0002)
- `data/<namespace>/ddc_classes/*.json` is scanned across **all** loaded namespaces, so an addon adds
  classes with no Java and no registration. A world pack overrides an addon by normal pack order.
- A malformed file is reported with its own name and the offending value, and does not stop the load.
- Ships the SRD 5.1 fighter, wizard, rogue, and cleric under CC-BY-4.0. No Wizards of the Coast
  product identity is in the jar.

**Game Master** (ADR-0003)
- `/ddc narrate <text>` — cinematic letterboxed narration on every player's screen.
- One server-side gate for every GM capability, requiring the Minecraft 26 `COMMANDS_GAMEMASTER`
  permission (the equivalent of the operator level 2 the ADR specifies). Permission plugins that
  grant it work without further support.

### Build
- Multi-loader Gradle 9 build against the real Minecraft 26.1.2, on Java 25.
- Committed Gradle wrapper; the toolchain downloads a JDK if the machine lacks one.
- 160 unit tests, run by `./gradlew build`.

### Not in this release

The PRD describes a larger mod. None of the following is implemented, and the documentation for it
describes intent rather than behaviour:

- **3D dice rendering with physics.** Rolls are resolved and broadcast with a replayable seed, which
  is the hard half, but the dice are shown as text rather than models.
- **Mob possession** and the GM wand, control panel, and encounter spawner.
- **Spells, spell slots, and spellbooks.** Only classes are data-driven so far; `ddc_spells` and
  `ddc_races` are not read yet.
- **Combat replacement.** Armour class is calculated but does not yet override vanilla damage, and
  hit points do not replace hearts.
- **Twitch integration and the OBS WebSocket overlay.**
- **Natural 20 screen shake, slow motion, spell runes**, and the glassmorphic blur. The character
  panel and narration bars are drawn plainly.
- **Character sheet screen** on the `C` key; use `/ddc sheet`.

### Known issues
- On NeoForge, the mod logs three `@OnlyIn` deprecation warnings at startup, one per client-only
  class. They are harmless: NeoForge no longer strips annotated members, and the mod never relies on
  that stripping — its client classes are only ever loaded on a client. Architectury's own API
  produces the same warnings.
- The development server's console cannot run commands, including vanilla ones. It is an environment
  fault rather than a mod fault; use a client to try the commands.

[1.0.0]: https://github.com/redstone-md/DDC/releases/tag/v1.0.0
