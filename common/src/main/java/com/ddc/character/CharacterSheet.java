package com.ddc.character;

import com.ddc.core.character.Ability;
import com.ddc.core.character.AbilityScores;
import com.ddc.core.character.Proficiency;
import com.ddc.rules.CharacterClass;
import com.ddc.rules.ClassFeature;
import com.ddc.rules.Race;
import com.ddc.rules.Spellcasting;
import com.ddc.rules.DDCCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * A player's character, as stored and synced.
 *
 * <p>Deliberately small: it holds the choices a player made and nothing that can be worked out from
 * them. Maximum hit points, armour class, and modifiers are all derived, so they can never drift out
 * of step with the class definition a data pack supplies.
 *
 * <p>Current hit points are not here either. The player already carries them, as vanilla health that
 * {@link HealthService} sizes from the hit die; storing them again would be a second answer to the
 * same question, and the two would drift the first time anything damaged a player without asking DDC.
 *
 * @param characterClass    the class the player picked, empty until they pick one
 * @param level             the character's level
 * @param abilities         the six ability scores
 * @param race              the race the player picked, empty until they pick one
 * @param usedSpellSlots    how many slots of each spell level have been spent since the last rest
 * @param usedFeatures      how many times each per-rest class feature has been spent
 * @param preparedSpells    the spells written into the character's spellbook
 * @param experience        experience earned, which is what the level is worked out from
 */
public record CharacterSheet(Optional<Identifier> characterClass, int level, AbilityScores abilities,
        Optional<Identifier> race, Map<Integer, Integer> usedSpellSlots, Map<ClassFeature.Type, Integer> usedFeatures,
        Set<Identifier> preparedSpells, int experience) {

    private static final Codec<AbilityScores> ABILITY_SCORES_CODEC =
            Codec.unboundedMap(DDCCodecs.ABILITY, Codec.intRange(Ability.MIN_SCORE, Ability.MAX_SCORE))
                    .xmap(AbilityScores::of, scores -> new EnumMap<>(scores.asMap()));

    /**
     * Spell levels as map keys.
     *
     * <p>A map key has to be a string: JSON has no other kind, and an int codec here encodes to a
     * number and fails with "Not a string" the moment a sheet with a spent slot is saved. That is not
     * hypothetical -- it shipped in 1.1.0, where casting a spell quietly broke the sheet's save.
     */
    private static final Codec<Integer> SPELL_LEVEL_KEY = Codec.STRING.comapFlatMap(
            key -> {
                try {
                    int level = Integer.parseInt(key);
                    if (level < 1 || level > Spellcasting.MAX_SPELL_LEVEL) {
                        return DataResult.error(() -> "Not a spell level: " + key);
                    }
                    return DataResult.success(level);
                } catch (NumberFormatException e) {
                    return DataResult.error(() -> "Not a spell level: " + key);
                }
            },
            String::valueOf);

    /**
     * Which per-rest features have been spent, and how many times.
     *
     * <p>A count rather than a flag, because a fighter's superiority dice are four uses of one
     * feature. Sheets written before this counted read as one use each, so a character mid-session
     * when the mod updates keeps having spent what they spent rather than getting it back.
     */
    private static final Codec<Map<ClassFeature.Type, Integer>> USED_FEATURES = Codec.either(
            Codec.unboundedMap(ClassFeature.Type.CODEC, Codec.intRange(0, 99)),
            ClassFeature.Type.CODEC.listOf())
            .xmap(either -> either.map(
                            counted -> counted,
                            flagged -> flagged.stream().collect(java.util.stream.Collectors.toMap(
                                    type -> type, type -> 1, (a, b) -> a))),
                    com.mojang.datafixers.util.Either::left);

    public static final Codec<CharacterSheet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("class").forGetter(CharacterSheet::characterClass),
            Codec.intRange(Proficiency.MIN_LEVEL, Proficiency.MAX_LEVEL).fieldOf("level")
                    .forGetter(CharacterSheet::level),
            ABILITY_SCORES_CODEC.fieldOf("abilities").forGetter(CharacterSheet::abilities),
            Identifier.CODEC.optionalFieldOf("race").forGetter(CharacterSheet::race),
            Codec.unboundedMap(SPELL_LEVEL_KEY, Codec.intRange(0, 99))
                    .optionalFieldOf("used_spell_slots", Map.of())
                    .forGetter(CharacterSheet::usedSpellSlots),
            USED_FEATURES.optionalFieldOf("used_features", Map.of())
                    .forGetter(CharacterSheet::usedFeatures),
            Identifier.CODEC.listOf().xmap(Set::copyOf, List::copyOf)
                    .optionalFieldOf("prepared_spells", Set.of())
                    .forGetter(CharacterSheet::preparedSpells),
            // Optional so every sheet saved before levelling existed still loads, at zero.
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("experience", 0)
                    .forGetter(CharacterSheet::experience)
    ).apply(instance, CharacterSheet::new));

    public CharacterSheet {
        Objects.requireNonNull(characterClass, "characterClass");
        Objects.requireNonNull(abilities, "abilities");
        Objects.requireNonNull(race, "race");
        usedSpellSlots = Map.copyOf(Objects.requireNonNull(usedSpellSlots, "usedSpellSlots"));
        usedFeatures = Map.copyOf(Objects.requireNonNull(usedFeatures, "usedFeatures"));
        preparedSpells = Set.copyOf(Objects.requireNonNull(preparedSpells, "preparedSpells"));
        Proficiency.validateLevel(level);
        if (experience < 0) {
            throw new IllegalArgumentException("Experience cannot be negative but was " + experience);
        }
    }

    /** A fresh, classless level 1 character with average scores. */
    public static CharacterSheet initial() {
        return new CharacterSheet(Optional.empty(), 1, AbilityScores.defaults(), Optional.empty(),
                Map.of(), Map.of(), Set.of(), 0);
    }

    public int proficiencyBonus() {
        return Proficiency.bonusAtLevel(level);
    }

    public int modifier(Ability ability) {
        return abilities.modifier(ability);
    }

    /**
     * Maximum hit points for this sheet under the given class definition.
     *
     * <p>Takes the definition rather than looking it up: the registry lives on the server, and this
     * record is also what the client holds.
     */
    public int maxHitPoints(CharacterClass definition) {
        return definition.hitDie().maxHitPoints(level, modifier(Ability.CONSTITUTION));
    }

    /** Returns a copy that has picked a class. The player's health is sized by {@link HealthService}. */
    public CharacterSheet withClass(Identifier id, CharacterClass definition) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(definition, "definition");
        return new CharacterSheet(Optional.of(id), level, abilities, race, usedSpellSlots, usedFeatures,
                preparedSpells, experience);
    }

    public CharacterSheet withAbilities(AbilityScores scores) {
        return new CharacterSheet(characterClass, level, scores, race, usedSpellSlots, usedFeatures,
                preparedSpells, experience);
    }

    /**
     * Returns a copy with experience added and the level that experience earns.
     *
     * <p>Level and experience move together and only here, because a sheet whose level disagreed with
     * its experience would be a sheet with two answers to one question. The table comes from the
     * class, so a data pack's own levelling pace is what applies.
     */
    public CharacterSheet withExperienceGained(int amount, CharacterClass definition) {
        if (amount < 0) {
            throw new IllegalArgumentException("Experience gained cannot be negative but was " + amount);
        }
        int total = experience + amount;
        return new CharacterSheet(characterClass, definition.leveling().levelFor(total), abilities, race,
                usedSpellSlots, usedFeatures, preparedSpells, total);
    }

    public CharacterSheet withLevel(int newLevel) {
        return new CharacterSheet(characterClass, newLevel, abilities, race, usedSpellSlots, usedFeatures,
                preparedSpells, experience);
    }

    /**
     * Returns a copy that has picked a race, with the race's bonuses applied to its scores.
     *
     * <p>The race being replaced takes its bonuses back on the way out. Without that, a player who
     * picked human twice would be walking around with the bonus twice: the scores are stored with the
     * bonuses already in them, so changing race has to undo before it does.
     *
     * @param previous the race being replaced, if there was one
     */
    public CharacterSheet withRace(Identifier id, Race definition, Optional<Race> previous) {
        Objects.requireNonNull(id, "id");
        AbilityScores base = previous.map(old -> old.removeFrom(abilities)).orElse(abilities);
        return new CharacterSheet(characterClass, level, definition.applyTo(base),
                Optional.of(id), usedSpellSlots, usedFeatures, preparedSpells, experience);
    }

    /** How many slots of a spell level have been spent since the last rest. */
    public int usedSlots(int spellLevel) {
        return usedSpellSlots.getOrDefault(spellLevel, 0);
    }

    /** Returns a copy with one more slot of that level spent. */
    public CharacterSheet withSlotSpent(int spellLevel) {
        Map<Integer, Integer> spent = new java.util.HashMap<>(usedSpellSlots);
        spent.merge(spellLevel, 1, Integer::sum);
        return new CharacterSheet(characterClass, level, abilities, race, spent, usedFeatures, preparedSpells, experience);
    }

    /**
     * Returns a copy rested: every slot and every once-per-rest feature back. Health is restored by
     * {@link HealthService}.
     */
    public CharacterSheet rested() {
        return new CharacterSheet(characterClass, level, abilities, race, Map.of(), Map.of(), preparedSpells, experience);
    }

    /** How many times a per-rest feature has been spent since the last rest. */
    public int featureUses(ClassFeature.Type feature) {
        return usedFeatures.getOrDefault(feature, 0);
    }

    /** Whether a once-per-rest feature has been spent since the last rest. */
    public boolean hasUsedFeature(ClassFeature.Type feature) {
        return featureUses(feature) > 0;
    }

    /** Returns a copy with one more use of a per-rest feature spent. */
    public CharacterSheet withFeatureUsed(ClassFeature.Type feature) {
        Map<ClassFeature.Type, Integer> used = new java.util.HashMap<>(usedFeatures);
        used.merge(feature, 1, Integer::sum);
        return new CharacterSheet(characterClass, level, abilities, race, usedSpellSlots, used,
                preparedSpells, experience);
    }

    public boolean hasRace() {
        return race.isPresent();
    }

    /** Whether this spell is written in the character's book. */
    public boolean hasPrepared(Identifier spell) {
        return preparedSpells.contains(spell);
    }

    /** Returns a copy with a spell written into the book. */
    public CharacterSheet withPrepared(Identifier spell) {
        Set<Identifier> prepared = new java.util.HashSet<>(preparedSpells);
        prepared.add(Objects.requireNonNull(spell, "spell"));
        return new CharacterSheet(characterClass, level, abilities, race, usedSpellSlots, usedFeatures,
                prepared, experience);
    }

    /** Returns a copy with a spell scrubbed out of the book. */
    public CharacterSheet withoutPrepared(Identifier spell) {
        Set<Identifier> prepared = new java.util.HashSet<>(preparedSpells);
        prepared.remove(spell);
        return new CharacterSheet(characterClass, level, abilities, race, usedSpellSlots, usedFeatures,
                prepared, experience);
    }

    /**
     * How many spells this character may keep prepared: the SRD's casting ability modifier plus their
     * level, and never fewer than one.
     */
    public int preparedSpellLimit(Ability castingAbility) {
        return Math.max(1, modifier(castingAbility) + level);
    }

    /** Whether the player has finished character creation. */
    public boolean hasClass() {
        return characterClass.isPresent();
    }

    public Map<Ability, Integer> scores() {
        return abilities.asMap();
    }
}
