# DDC campaign template

A data pack that adds a class, a race, a spell, an encounter and a locked chest. Everything here is
an example you are meant to edit rather than a set of rules to keep.

## Using it

1. Unzip into `<world>/datapacks/`, or keep the zip there — Minecraft reads either.
2. Rename `data/my_campaign/` to your campaign's own name. Nothing in DDC cares what it is called; the
   name only has to be unique, because two packs with the same namespace overwrite each other.
3. `/reload`. Your class is on the wheel immediately, with no restart and no client update — the rules
   are sent to every client that joins.

## What is here

| File | What it shows |
|---|---|
| `ddc_classes/paladin.json` | Hit die, saving throws, a class feature, and a `leveling` table that levels a party faster than the SRD does. |
| `ddc_races/tiefling.json` | Ability bonuses, speed, traits, and the `items` a character starts with. |
| `ddc_spells/searing_smite.json` | Damage dice, a saving throw, and a `cast_time` — a spell with one draws its runes first and lands after. |
| `ddc_encounters/goblin_ambush.json` | A group, with a named boss carrying real equipment. |
| `data/minecraft/ddc_checks/chest.json` | A chest that asks for a Dexterity check before it opens. The file's own id names the block, so this one is `minecraft:chest`. |

## Rules of the road

- **Ids are what commands take, names are what menus show.** `ddc:paladin` is the id; `"name"` is the
  label a player reads.
- **A broken file reports itself** and leaves the rest of the reload standing. Watch the server log.
- **The server owns the rules.** A client is told the names so it can draw a menu, and nothing else.
