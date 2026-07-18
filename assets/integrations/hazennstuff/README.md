# DDC × Hazen 'N Stuff

[Hazen 'N Stuff](https://www.curseforge.com/minecraft/mc-mods/hazen-n-stuff) is an Iron's Spells 'n
Spellbooks addon — a large pile of gear, two new spell schools, and a cast of Terraria-flavoured
bosses and casters. This pack brings its monsters into DDC as Game Master encounters and seven of its
spells as castable DDC spells.

## Installing

1. Have [DDC](https://github.com/redstone-md/DDC), **Iron's Spells 'n Spellbooks**, and **Hazen 'N
   Stuff** installed — all on Minecraft 1.21.1, NeoForge (Iron's and this addon are NeoForge-only).
2. Unzip into `<world>/datapacks/` (or keep the zip there), and `/reload`.

Without the mods, DDC does not break — an unknown entity is skipped at spawn and an unknown spell falls
back to DDC's own effect — but there is nothing to spawn or cast, so install the mods too.

## What is here

### The monsters, as encounters

| Encounter | The fight |
|---|---|
| The Nameless One | The set-piece boss, alone. |
| The Recluse | The spider-boss, alone. |
| Pyromus | The fire-lord and its piglin pyromancers. |
| The Necromancer's Court | A necromancer and its ender servants. |
| The Bishop of Deceit | The illusionist boss. |
| The Conclave | An electromancer, a pyromancer and a dryad together. |
| The Void Wanderer | The wanderer and its servants. |

### The spells, as DDC spells

Seven of the mod's spells, as DDC spells with D&D levels. As with the [Iron's bridge](../../..), each
names a spell in the mod's registry via `irons_spell`; Hazen 'N Stuff registers into Iron's, so DDC
reaches its spells the same way it reaches Iron's own.

| DDC spell | Level | Casts as |
|---|---|---|
| Water Bolt | cantrip | `hazennstuff:water_bolt` |
| Ice Arrow | 1 | `hazennstuff:ice_arrow` |
| Fiery Dagger | 1 | `hazennstuff:fiery_dagger` |
| Shadow Reaver | 2 | `hazennstuff:shadow_reaver` |
| Cosmic Bolt | 3 | `hazennstuff:cosmic_bolt` |
| Shooting Star | 4 | `hazennstuff:shooting_star` |
| Supernova | 5 | `hazennstuff:supernova` |

DDC gates them by its rules (the class can cast, the slot is paid, the target is in range); the effect,
damage and look are the mod's own.

## Credit and licence

This pack contains **no assets and no code** from Hazen 'N Stuff or Iron's Spells — only DDC data files
that name their entities and spells, read from the mod's own `en_us.json`. Hazen 'N Stuff is by
Hazentouvel; install it from its own page. This pack is MIT, like DDC.
