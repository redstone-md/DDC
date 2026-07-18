# DDC × Monsters & Spellbooks

[Monsters & Spellbooks](https://www.curseforge.com/minecraft/mc-mods/monsters-spellbooks-iss) is an
Iron's Spells 'n Spellbooks addon: a hundred-odd new spells and a company of monsters that cast them.
This pack brings both into DDC — its casters as Game Master encounters, and six of its spells as DDC
spells you can prepare and cast.

## Installing

1. Have [DDC](https://github.com/redstone-md/DDC), **Iron's Spells 'n Spellbooks**, and **Monsters &
   Spellbooks** installed — all on Minecraft 1.21.1, NeoForge (Iron's and this addon are NeoForge-only).
2. Unzip into `<world>/datapacks/` (or keep the zip there), and `/reload`.

Without the mods, DDC does not break — an unknown entity is skipped at spawn and an unknown spell falls
back to DDC's own effect — but there is nothing to spawn or cast, so install the mods too.

## What is here

### The monsters, as encounters

Twelve warbands built from Monsters & Spellbooks' spell-casting mobs, on the GM wand's wheel:

| Encounter | The fight |
|---|---|
| The Draugr Host | A draugr evoker and elite vindicator over a line of vindicators and pillagers. |
| The Illager Cabal | An enchanter flanked by fire- and ice-ologers. |
| The Herobrine Cult | A cult mage and its assassins. |
| The Wither Legion | A wither warlock and its warriors. |
| The Soul Horde | A soul wizard, its wraiths, and vile skeletons. |
| The Death Knight | The knight and the dead it raises. |
| The Elemental Conclave | A magma atronach and a redstone elemental. |
| The Dwarven Incursion | Slicers, a sphere, and a swarm of drones. |
| The Jungle Ambush | A whisperer, leapleaves, spriggans, and quill vines. |
| The Frozen Deep | An ice serpent and a prismarine keeper. |
| The Rancor Phantom | The phantom and its skeletal wards. |
| The Wrath | The set-piece, alone. |

### The spells, as DDC spells

Six of the mod's spells, wrapped as DDC spells with D&D levels so a caster can prepare and cast them.
This is the whole point of the [Iron's bridge](../../..): a DDC spell names an Iron's-registry spell in
its `irons_spell` field, and DDC casts it as that — Monsters & Spellbooks registers into Iron's, so its
spells are reachable the same way Iron's own are.

| DDC spell | Level | Casts as |
|---|---|---|
| Bone Dagger | cantrip | `monsterspellbooks:bone_dagger` |
| Blood Thorn | 1 | `monsterspellbooks:blood_thorn` |
| Frost Breath | 2 | `monsterspellbooks:frost_breath` |
| Banshee Scream | 2 | `monsterspellbooks:banshee_scream` |
| Brimstone Rain | 3 | `monsterspellbooks:brimstone_rain` |
| Graveyard Fissure | 4 | `monsterspellbooks:graveyard_fissure` |

DDC gates them by its rules (the class can cast, the slot is paid, the target is in range); the effect,
damage and look are the mod's own. With Monsters & Spellbooks absent they fall back to DDC's particles.

## Writing your own

The encounter files are DDC's encounter format pointing at the mod's entity ids; the spell files are
DDC spells pointing at its spell ids. Both were read from the mod's own `en_us.json`, not guessed.
**Any Iron's Spells addon works this way** — its spells are in Iron's registry, so a DDC spell with an
`irons_spell` field reaches them with no code. Point a file at their ids and `/reload`.

## Credit and licence

This pack contains **no assets and no code** from Monsters & Spellbooks or Iron's Spells — only DDC data
files that name their entities and spells. Monsters & Spellbooks is by RedReaper; install it from its
own page. This pack is MIT, like DDC.
