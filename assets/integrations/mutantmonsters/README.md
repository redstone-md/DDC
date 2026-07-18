# DDC × L_Ender's Mutant Monsters

Five of the mutants, as Game Master encounters: a boss the party fights, a warband it wades through.
Drop this data pack in with **both** mods installed and the mutants appear on the GM wand's wheel like
any other encounter — to spawn, to possess, and to hand out experience for.

## Installing

1. Have [DDC](https://github.com/redstone-md/DDC) **and**
   [L_Ender's Mutant Monsters](https://modrinth.com/mod/mutant-monsters) installed, both on Minecraft
   1.21.1.
2. Unzip into `<world>/datapacks/` (or keep the zip there — Minecraft reads either), and `/reload`.
3. The encounters are on the wand: hold it, press the wheel key, and there they are.

Without Mutant Monsters, DDC does not break — it reports the unknown entity and skips it — but there
is nothing to spawn, so there is no reason to install this pack without the mod.

## What is here

| Encounter | The fight |
|---|---|
| The Mutant Zombie Lord | A single named boss with 250 hit points — a set-piece, not a mob. |
| Bonecracker | A mutant skeleton captain and its ordinary skeleton archers. |
| The Hollow King | A mutant enderman flanked by two endersoul clones. |
| Creeping Doom | A mutant creeper and a pack of creeper minions. |
| The Beastmaster's Menagerie | Two spider pigs and a frost-hearted mutant snow golem. |

Experience is worth what a thing is worth: a mutant has a great many hit points, so killing one is
worth a great many levels. A Game Master can also possess any of them — the mutant is theirs to drive,
with the wand's boss abilities on 1, 2 and 3.

## Writing your own integration

This pack is nothing but DDC's own encounter format pointing at another mod's entity ids. **That is the
whole pattern**, and it needs no code and no dependency: DDC scans every namespace's `ddc_encounters/`
directory, and an entity id it does not recognise is reported at spawn rather than crashing a load. So
any mod's mobs become DDC encounters — write a file like these, point it at their ids, and `/reload`.

The [Cataclysm pack](../cataclysm/) next door is the same pattern pointed at a different mod's bosses.
Any large mob mod on 1.21.1 — Bosses of Mass Destruction, Alex's Mobs, and the rest — takes a pack
that looks exactly like this one.

## Credit and licence

This pack contains **no assets and no code** from Mutant Monsters — only DDC data files that name its
entities. Mutant Monsters is by L_Ender; install it from its own page. This pack is MIT, like DDC.
