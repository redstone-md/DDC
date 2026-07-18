# DDC × L_Ender's Cataclysm

Cataclysm's bosses and warbands, as Game Master encounters: the eight dungeon bosses each as a
set-piece the party fights, and the mod's lesser monsters gathered into warbands to wade through.
Drop this data pack in with **both** mods installed and they appear on the GM wand's wheel like any
other encounter — to spawn, to possess, and to hand out experience for.

## Installing

1. Have [DDC](https://github.com/redstone-md/DDC) **and**
   [L_Ender's Cataclysm](https://modrinth.com/mod/l_enders-cataclysm) installed, both on Minecraft
   1.21.1.
2. Unzip into `<world>/datapacks/` (or keep the zip there — Minecraft reads either), and `/reload`.
3. The encounters are on the wand: hold it, press the wheel key, and there they are.

Without Cataclysm, DDC does not break — it reports the unknown entity and skips it — but there is
nothing to spawn, so there is no reason to install this pack without the mod.

## What is here

### The eight bosses

Each is placed exactly as the mod makes it: its own boss bar, its own health, its own fight. DDC only
puts it in front of the party.

| Encounter | The fight |
|---|---|
| The Netherite Monstrosity | The nether's siege engine, alone — it needs no help. |
| The Ender Guardian | The End's warden, with a flight of endermaptera at its side. |
| Ignis, the Fire Titan | The forge-lord of the blazing dungeon. |
| The Harbinger | The desert's phantom knight. |
| The Ancient Remnant | The sand-buried colossus. |
| The Leviathan | The deep's great serpent — fight it on the water. |
| Scylla | The tidal horror of the coral shrine. |
| Maledictus, the Cursed | The cursed pharaoh, raised. |

### The warbands

Cataclysm's lesser monsters, gathered into fights the size of an ambush.

| Encounter | The fight |
|---|---|
| The Kobold Arena | A kobolediator and five koboleton fighters. |
| The Draugr Warband | A royal draugr, two elite draugr, and six draugr. |
| The Deepling Raid | A deepling priest, brutes, warlocks, and their footsoldiers. |
| The Ignited Horde | An ignited revenant leading three berserkers. |
| Wadjet's Tomb | The serpent-guardian and its koboleton wards. |
| The Prowler | The stalking mini-boss, alone. |
| The Amethyst Crab | The crystal-shelled brute. |
| The Coralssus | The reef-golem. |

Experience is worth what a thing is worth: a Cataclysm boss has a great many hit points, so killing
one is worth a great many levels. A Game Master can also possess any of them — the boss is theirs to
drive, with the wand's boss abilities on 1, 2 and 3.

## Writing your own integration

This pack is nothing but DDC's own encounter format pointing at another mod's entity ids. **That is the
whole pattern**, and it needs no code and no dependency: DDC scans every namespace's `ddc_encounters/`
directory, and an entity id it does not recognise is reported at spawn rather than crashing a load. So
any mod's mobs become DDC encounters — write a file like these, point it at their ids, and `/reload`.

The ids here were read from L_Ender's own resource files, not guessed, so they match what the mod
registers. If a future Cataclysm version renames one, DDC reports it at spawn and the rest still work.

## Credit and licence

This pack contains **no assets and no code** from Cataclysm — only DDC data files that name its
entities. L_Ender's Cataclysm is by L_Ender; install it from its own page. This pack is MIT, like DDC.
