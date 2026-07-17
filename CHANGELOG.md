# Changelog

All notable changes to DDC are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Each GitHub release carries notes generated from that release's commits by
[git-cliff](https://git-cliff.org) (see `cliff.toml`), so they always match what shipped. This file
is written by hand for what the commits cannot say: why a release is shaped the way it is, and what
it deliberately leaves out.

## [1.7.1] - 2026-07-17

### Fixed
- **The wheel piled its options on top of each other.** The ring was a fixed 76 pixels whatever was on
  it, so seven options -- which is what a fighter has now -- had nowhere to sit, and Russian labels are
  wider than the English ones the size was picked for. The ring grows to fit its cards now, and a card
  grows to fit its text rather than the text running out over the edges.
- **The party cards covered the player's own HUD in OBS.** Both drew in the top-left, so the widget
  hid the sheet it was describing. The party moved to the bottom-left, which is the corner neither the
  game's HUD nor the roll alerts nor a streamer's camera wants.
- **The HUD says less, more clearly.** Race and class and level on one line, hit points and armour
  class on the next. The proficiency bonus is gone from it: it never changes between levels, the sheet
  screen has it, and it was making the busiest line of an always-on panel busier for a question nobody
  asks mid-fight.

## [1.7.0] - 2026-07-17

The parts of the documentation that were still only documentation, and two faults a player found by
playing.

### Added
- **Wands and staffs** (PRD 3.1's "spells are cast using wands/staffs"). Hold one, right-click what
  you are aiming at, and your chosen spell goes off; sneak-click cycles the choice. A wand carries
  cantrips and never runs dry, a staff carries anything prepared. Casting through a menu works and
  always will, but a wizard at a table points at a thing and says a word.
- **Races hand over a starting kit** — an elf's bow, a dwarf's axe. Which items is the pack's story
  about what an elf is, so it lives in the race's file: `"items": ["minecraft:bow"]`.
- **Levelling** (ADR-0002's "XP levels via data packs", ARCHITECTURE 5's "leveling milestones"). It
  did not exist: every character was level 1 forever, and hit points, proficiency and spell slots all
  read from that level, so the whole progression half of the rules was dead code. Killing something
  earns experience — a creature is worth its hit points, since D&D measures in challenge rating and
  Minecraft has none — and `/ddc xp` lets a Game Master award it for the evening's talking rather
  than its fighting. The table is the class's, so a pack can level a party in a weekend.
- **The fighter is a class now** (PRD 3.1). It had second wind and nothing else. Action surge grants
  an extra action in the SRD and there are no turns here to take one in, so it buys seconds instead:
  a few of swinging and moving faster than anyone. Manoeuvres — trip, parry, push — spend superiority
  dice. All of it is on the wheel, aimed at what you are looking at.
- **Locked doors ask for the roll themselves** (PRD 3.1's headline). It was a command a player had to
  remember to run, which is the bookkeeping DDC exists to remove. Which blocks ask is data:
  `data/minecraft/ddc_checks/iron_door.json`.
- **Armour class and spell slots on the HUD**, which PRD 3.1 lists and the HUD never had. Armour class
  could not be shown because it was never sent; slots are pips, because the question at a table is
  "have I got a second level left".
- **The OBS widget draws party health cards** (PRD 4.5). The event existed with no caller and the page
  could not have drawn it. The server sends the party once a second, and only when it changed.
- **A possessed monster can attack** (ARCHITECTURE 6's `ddc:possess_mob`, listed as planned). A GM
  could steer a monster and never hit anyone with it. It hits what the GM looks at; the client sends
  only that the click happened, because a packet naming its own target is the thing ADR-0003 refuses.
- **`cast_time`, `components` and `area_of_effect` are parsed.** ARCHITECTURE's own example schema
  printed all three and the codec ignored them, so a pack copying the documentation had two thirds of
  its file silently dropped. Casting time also makes PRD 4.4's runes mean something: the ground lights
  up and the spell lands after, rather than both in the same tick.

### Fixed
- **Your race looked like it reset every time you rejoined.** It never did — the save had it all along
  — but nothing displayed it anywhere, so there was no way to tell remembered from lost. The HUD says
  it now.
- **Picking a race twice paid twice.** Scores are stored with the bonus already in them, and a player
  who could not see their race would naturally pick again to be sure. Changing race hands the old
  one's bonuses back first.
- **Refreshing the OBS browser source left the party blank** until somebody's health changed. The
  party is a state, not a moment, so a widget is told where things stand the moment it connects.

## [1.6.1] - 2026-07-17

### Fixed
- **Joining a world disconnected you immediately.** The rules the server sends on join were built with
  a null id in every entry and the real one patched in a line later; the entry refuses to exist
  without one, so the payload threw while the player was being placed. Present since 1.5.0, which
  makes 1.5.0 and 1.6.0 unplayable — use this one. Nothing caught it because the tests had registries
  with nothing in them, and an empty registry describes nothing, so no test ever built the entry that
  throws. Registries can be filled in a test now, and the payload is tested with things in them.

### Added
- **Colour grading on a natural 20** (PRD 4.4) — gold on a 20, grey on a 1, for the length of the
  shake. It is the game's own `post/color_convolve` with a different matrix in the pack file, because
  a colour grade *is* a matrix and Minecraft already ships a shader that multiplies by one. It will
  not start when something else is grading the screen: a player spectating a creeper chose that, and
  a good roll must not take it away.
- **One mixin**, and only one: the renderer's `setPostEffect` is private and the public ways in pick
  the effect from an entity type, so there is no vanilla road from "rolled a 20" to "grade the
  screen". It injects nothing and changes no behaviour — it makes an existing method callable. This
  is the last thing in the PRD that had no API to go through.

## [1.6.0] - 2026-07-17

The last two things the documentation asked for that I had written off as impossible. Both were, it
turns out, only impossible the way I had been thinking about them.

### Added
- **A natural 20 slows the world down** (PRD 4.4). I had called this a shader problem and shelved it,
  because the post-processing hook is private. It is not a shader problem: the game can be told to run
  its world slower — that is what `/tick rate` does — so the whole table shares the moment rather than
  one client pretending while everyone else's world carries on. It lasts under a second, because a
  table that had to wait out a cinematic every good roll would stop rolling well on purpose, and it
  refuses when a Game Master has stopped the clock themselves: a fanfare must never quietly undo
  something the GM did on purpose.
- **The GM wand's radial menu** (PRD 3.2). Holding the wand and pressing the wheel key gives a Game
  Master their encounters; a GM playing a character presses the same key and gets their character,
  because holding the wand is the ask. Picking one only selects it — the wand still places it where
  the GM points, since choosing a fight and choosing where it happens are two decisions, and a menu
  that did both would drop a patrol wherever the GM was standing.
- **`/ddc encounter <id>`**, which is what the wheel sends. Everything the wheel does is a command a
  player could have typed, so it goes through the same permission check rather than a second door.

### Changed
- The rules sent to the client now carry the encounters and whether this player is a Game Master. The
  encounters go only to a GM — not because they are a secret, the server refuses the spawn either way,
  but because a wheel that offers a player something they cannot do is a wheel that lies to them.

### Still not done
- **Colour grading on a natural 20.** `GameRenderer.setPostEffect` is private, and reaching it needs a
  mixin. Worth doing; not worth pretending it is done.
- **Modrinth.** The workflow is written. Publishing is the owner's call, not mine.

## [1.5.0] - 2026-07-17

Everything in this release came from someone playing the mod and saying what was wrong with it.

### Added
- **An action wheel on `R`.** A character who does not exist yet gets Class and Race on it, and
  nothing else, because nothing else works yet; a character who does gets their actions — roll, cast
  at whatever you are looking at, second wind, channel divinity, rest, sheet. Point to choose, release
  to run. Nothing has to be typed any more.
- **The rules are synced to the client** (ARCHITECTURE.md 2's "Registry → Client", which had never
  been built). Data packs are the server's, so a client had no way to know a class was called Fighter
  or that one existed — which is why picking a class was an id typed by hand. Names and ids only; the
  rules stay where they can be checked. Sent again after `/reload`, so an addon's class reaches the
  wheel with no reconnect.
- **Everything the mod says is translatable, in English and Russian.** 111 keys. The server decides
  what happened; only the player's own client knows what language to say it in, so a server that
  hardcoded English was deciding that for them.
- **Twitch chat votes** and **spell runes** (PRD 4.4), carried over from 1.4.1.

### Fixed
- **The wheel crashed the game.** Screens already blur what is behind them, and asking for a second
  blur in one frame is an error the renderer throws on. The screens let the renderer do it now.
- **`/ddc sheet` printed `proficiency +2
STR 10`.** Chat does not break a component's lines, so it
  prints two messages instead of one with a newline in it.

## [1.4.1] - 2026-07-17

Three faults that only showed up when somebody actually played the mod. All three
were in 1.4.0, and all three are the kind of thing a test cannot see.

### Fixed
- **`/ddc` was completely broken on a client.** The overlay and Twitch commands registered `/ddc` as a
  *client* command, and a client command tree swallows the root it registers: every `/ddc sheet`,
  `/ddc class`, `/ddc cast` answered "incorrect argument" and the server never saw them. The streamer
  commands now live under their own `/ddcstream` root, which cannot shadow the server's.
- **The whole game was blurred, permanently.** Minecraft 26's blur is a full-frame pass — it blurs
  everything drawn beneath the stratum, which is the world — and the HUD is on screen the entire time
  a player is playing. PRD 3.1's glassmorphic card needs a blur bounded to the card's own rectangle,
  which the renderer does not offer; until it does, the HUD is a translucent card. The sheet and GM
  screens keep the blur, because a screen is a moment rather than a state.
- **The HUD's text was invisible, and had been since 1.0.0.** Its colours were written `0xFFFFFF`,
  which is fully transparent: alpha is the top byte and leaving it out means nothing draws. The card
  had been rendering as an empty box for every release, and nobody had looked at it.

### Added
- **Twitch chat votes** (PRD 4.4). `/ddcstream twitch connect <channel>` reads a channel, `vote` opens
  the floor, chat types `!adv` or `!dis`, and `close` reports what chat chose — it does not roll for
  the table. Read-only and anonymous: Twitch allows an anonymous reader, so no OAuth token is ever
  asked for or held. One viewer, one vote, and a tie decides nothing rather than flipping a coin.
- **Spell runes** (PRD 4.4). A ring under the caster and the target before a spell lands, sized by the
  spell's level, drawn with particles rather than a decal shader.

## [1.4.0] - 2026-07-16

Everything left in the PRD that DDC can build for itself.

### Added
- **The wizard's spellbook** (PRD 3.1). Craft it from a book, an amethyst shard and an ink sac.
  `/ddc prepare` writes a spell in and `/ddc forget` scrubs one out, both with the book in hand;
  casting an unprepared spell now fails and says why. Cantrips need no preparation — the SRD knows
  them rather than prepares them. The book holds Intelligence plus level spells, and never fewer than
  one. What is written in it lives on the sheet: a book left in a chest should not take a wizard's
  magic with it.
- **The Nat 20 and Nat 1 fanfare** (PRD 4.4). Your own natural 20 shakes the camera, throws gold and
  sounds a note; a natural 1 puffs ash. Only your own dice: a screen that shook whenever anyone rolled
  would be unplayable in a party of five.
- **The character sheet on `C`** (PRD 3.1). A page, not a form — DDC has no way for a client to change
  a sheet, and buttons that asked the server nicely would be a lie about where the rules live.
- **The Game Master panel on `G`** (PRD 3.2). Narration and world control, one click each. Every button
  sends the command it names, so the permission checks ADR-0003 insists on are the ones already there.
- **The OBS overlay's WebSocket feed** (ARCHITECTURE.md 5). `/ddc overlay start` opens
  `ws://localhost:8082` and every roll goes out as JSON in the shape that document promises. Off until
  asked for, bound to loopback, and stopped when you leave a world.

### Known issues
- **Only the overlay was verified.** It is tested against a real WebSocket client on a real socket. The
  fanfare's shake curve and the dice physics are tested as maths. Everything else on the screen — the
  sheet, the panel, the dice as they look, the particles — has only been checked as far as "the client
  boots clean with it, no missing models, no errors". Nobody has looked at any of it.
- **Not built, and not pretended**: the glassmorphic blur and the Nat 20 slow motion (both want a
  post-processing pass Minecraft 26's renderer would have to be given), spell runes, the GM's radial
  menu and encounter preview grid, the streamer HUD layout, and Twitch chat integration.
- **Modrinth publishing is not wired up** (PRD 5). The project does not exist yet — the `ddc` slug
  belongs to another mod — and arming a publish-to-the-world pipeline is the owner's call, not the
  build's.

## [1.3.0] - 2026-07-16

### Added
- **The dice are in the world** (PRD 4.1). A roll throws them in front of the roller; they tumble,
  bounce, settle, and are read by everyone nearby. A natural 20 lands gold and a natural 1 red. A
  discarded advantage die is drawn faint: it was thrown, it just did not count.
- The flight is a function of `(seed, die, time)` and nothing else. Nothing is ticked and nothing
  about the tumble is sent, so every client draws the same throw and a hundred dice cost the server
  nothing. That is ARCHITECTURE.md's deterministic replay.
- The faces are looked up from the roll's payload by seed rather than recomputed, so the die showing
  a 17 and the roll log saying 17 cannot disagree.

### Changed
- **Two things ARCHITECTURE.md describes do not exist in Minecraft 26, and the design changed to
  suit.**
  - It draws the dice from "a custom client-side render layer" with no entity behind them. Minecraft
    26's renderer extracts state and submits geometry to be drawn later rather than drawing in place,
    and neither Fabric's API nor Architectury still exposes a hook for arbitrary world geometry. So
    there is an entity: one per roll rather than one per die, no AI, no collision, no gravity, never
    saved, and gone in four seconds.
  - Architectury has no entity-renderer registry, so each loader registers the shared renderer for
    itself.

### Known issues
- **The dice have never been looked at.** They compile, both servers load them, and the physics is
  covered by tests -- the same seed tumbles identically, no die falls through the floor, every throw
  settles, a landed die stops spinning exactly when it stops moving. None of that is the same as
  having seen one land. Report anything that looks wrong.
- Every die is drawn as an icosahedron, whatever it is. A d6 that is really a d20 is a lie the eye
  can see, and the other solids are not built yet.
- The faces carry no numbers: the die's colour says whether it was a natural 20 or a 1, and the roll
  log says what it was.

## [1.2.0] - 2026-07-16

Everything PRD 3.1 and 3.2 describe that does not need a screen. The classes have
mechanics, the party can be asked to roll, and the Game Master can drive a monster.

### Added

**Class mechanics** (PRD 3.1)
- The rogue's **sneak attack**: 1d6 per two levels, when unseen, behind the target, or with a player
  within five feet of it. The SRD's condition is "advantage, or an ally within 5 feet"; DDC has no
  turn order, so the trigger is read off the world and the reading is stated in `SneakAttackRules`.
- The fighter's **second wind**: `/ddc second-wind` heals 1d10 plus level, once per rest.
- The cleric's **channel divinity**: `/ddc channel-divinity` turns the undead within 30 feet. They are
  slowed, weakened and lit up rather than made to flee, because Minecraft has no fleeing state to set.
- Which class has which is data. The kinds of feature are code, because each is behaviour rather than
  numbers; a pack tunes what is here, a mod adds a new kind.

**Ability checks** (PRD 3.1)
- `/ddc check <ability> <dc>` for picking a lock or jumping a gap. Public, unlike an attack roll: a
  hidden lockpick tells the player nothing.
- A GM can call for someone else's roll with a trailing player name.

**Possession** (PRD 3.2, ADR-0003)
- Right-click a creature with the wand to drive it. The GM goes to spectator and the mob follows the
  point they fly at, which is ADR-0003's "translate movement into pathfinding" rather than mirroring
  input: no new packet exists to forge.
- A possessed mob is a mini-boss: four times the health, twice the damage, both given back on release.
- Sneaking lets go. So does the mob dying, which ejects the GM rather than killing them, and so does
  the GM logging out.

**World control** (PRD 3.2)
- `/ddc world day|night|storm|clear|pause-time|resume-time|freeze|release`. The freeze is an effect
  that wears off, so a GM who disconnects mid-scene cannot strand the table.

### Still not shipped
Everything left needs a screen or a shader: 3D dice with physics, the sheet on `C`, the GM panel on
`G`, the Nat 20 fanfare, spell runes, the glassmorphic blur, the streamer HUD, and the Twitch and OBS
integration. The wizard's spellbook is not in either.

## [1.1.1] - 2026-07-16

### Fixed
- **Casting a spell broke the character sheet's save.** Spent spell slots were keyed by an int, and a
  map key has to be a string: the world saves as NBT, and the first save after a cast failed with
  "Not a string". Sheets round trip through NBT now, and a test pins it in the format the world
  actually uses rather than in JSON, which is what let this through in 1.1.0.
- **Hit points were a number in a panel.** `/ddc sheet` said 44/44 while the player walked around
  with vanilla's 20 and died at 20: the sheet's hit points and the player's health were two separate
  lives, and the rules cared about the fiction. There is one now. Vanilla health is the health, and
  the class's hit die sizes it through a transient modifier on max health, re-applied on join,
  respawn and every change. A player with no class keeps vanilla's 20: taking someone's hearts away
  for not having filled in a character sheet would be a strange welcome.
- **Saving throws ignored class proficiency.** `CharacterClass.isProficientInSave` had no callers, so
  listing a class's saving throws did nothing at all. A wizard now adds proficiency to Intelligence
  saves, which is the point of the field.

### Changed
- The sheet no longer stores current hit points, and its payload no longer carries them: the client
  reads its own health. Sheets saved by 1.1.0 still load.
- `/ddc sheet` shows spell slots as remaining-of-total per level. There was previously no way to see
  them.

### Removed
- Nothing shipped uses `HealthService` to hide vanilla's hearts yet: 44 hit points render as 22
  hearts. The numeric panel is in the HUD; replacing the hearts bar needs the client work that is
  still outstanding.

## [1.1.0] - 2026-07-16

The mod starts playing like D&D rather than describing it: attacks roll against
armour class, spells cost slots, and the Game Master can place a fight.

### Added

**Combat** (PRD 4.2)
- An attack roll now stands between a swing and its damage. The d20 must meet the target's armour
  class; a miss cancels the damage and shows a dodge. The roll is hidden, so only the attacker sees
  the numbers.
- Two mappings neither Minecraft nor the SRD defines, so DDC states them: vanilla armour points are
  halved onto armour class, which puts iron at 17 against chain mail's 16 and keeps full diamond
  inside the SRD's own ceiling; a mob's to-hit is half its attack damage, capped at +10, which puts
  a zombie at +1 and a vindicator at +6.
- Armour category decides how much Dexterity survives, so plate feels like plate.

**Races and spells** (ADR-0002)
- `ddc_races` and `ddc_spells` join `ddc_classes`: all scanned across every namespace, all reloading
  with `/reload`. The addon promise now covers what it said it would.
- `/ddc race <id>` applies a race's ability bonuses to the sheet.
- `/ddc cast <spell> <target>` spends a slot, rolls damage in public where the table can see it,
  rolls the target's save against DC 8 + proficiency + casting ability, and halves or negates it by
  what the spell says.
- `/ddc rest` takes a long rest: slots and hit points back.
- Spell slots are a table in the class's own JSON, because that is what they are in the SRD. A
  homebrew half-caster writes its own progression instead of asking DDC to know what one is.
- Ships the SRD's dwarf, elf, human and halfling, and fire bolt, magic missile, burning hands,
  fireball and sacred flame.

**Game Master** (PRD 3.2)
- The Game Master's Wand, the mod's first item. Right-click the ground to place the selected
  encounter; sneak-right-click to step through them. Holding it grants nothing: every use re-checks
  the GM permission.
- `ddc_encounters`, a fourth data pack directory, so a GM writes their campaign's fights and an addon
  can ship a bestiary. Capped at 32 mobs per encounter.

### Fixed
- **A typo in an addon could take down a whole data pack reload.** The encounter and spell-slot
  codecs let their constructors throw, so bad JSON raised an exception instead of being reported
  against its own file. Both now validate before constructing, which is what ADR-0002 asks for. Found
  by a test, not in the wild.

### Changed
- **The character sheet's saved shape and its payload changed** (it carries a race and spent spell
  slots now). Sheets from 1.0.x load; a 1.1.0 client still needs a 1.1.0 server.

### Still not shipped
3D dice with physics, mob possession, the character sheet screen on `C`, class mechanics beyond hit
points, Twitch and the OBS overlay, and the Nat 20 fanfare. Armour class is calculated and used in
combat, but hit points still do not replace hearts. See 1.0.0's list below; it is otherwise unchanged.

## [1.0.1] - 2026-07-16

### Fixed
- **Dice rolls could desync between the server and a client on a different build of the mod.** The
  server sent only the seed and each client re-ran the roll locally, trusting that both sides would
  turn that seed into the same faces. They do, but only while both sides parse dice notation and
  consume the random stream identically — so a client on another version could show the table a
  number the server never scored, silently. The server now sends the faces it rolled and the client
  displays them. The seed still travels, for the dice physics ARCHITECTURE.md calls for; it decides
  how a die tumbles, never which face it lands on.

  The expression travels as structure (pools and a modifier) rather than as the string "1d20+3", so
  the roll no longer depends on both sides parsing notation the same way. The packet got smaller.

- A roll result now has to account for exactly the dice its expression asked for, which is what
  refuses a malformed result arriving over the network instead of drawing it.

### Changed
- **The `ddc:dice_result` payload changed shape**, so a 1.0.1 client and a 1.0.0 server will not
  agree about it. Update both.

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

[1.5.0]: https://github.com/redstone-md/DDC/releases/tag/v1.5.0
[1.4.1]: https://github.com/redstone-md/DDC/releases/tag/v1.4.1
[1.4.0]: https://github.com/redstone-md/DDC/releases/tag/v1.4.0
[1.3.0]: https://github.com/redstone-md/DDC/releases/tag/v1.3.0
[1.2.0]: https://github.com/redstone-md/DDC/releases/tag/v1.2.0
[1.1.1]: https://github.com/redstone-md/DDC/releases/tag/v1.1.1
[1.1.0]: https://github.com/redstone-md/DDC/releases/tag/v1.1.0
[1.0.1]: https://github.com/redstone-md/DDC/releases/tag/v1.0.1
[1.0.0]: https://github.com/redstone-md/DDC/releases/tag/v1.0.0
