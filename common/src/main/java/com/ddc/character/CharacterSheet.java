package com.ddc.character;

import com.ddc.core.character.Ability;
import com.ddc.core.character.AbilityScores;
import com.ddc.core.character.Proficiency;
import com.ddc.rules.CharacterClass;
import com.ddc.rules.Race;
import com.ddc.rules.Spellcasting;
import com.ddc.rules.DDCCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 */
public record CharacterSheet(Optional<Identifier> characterClass, int level, AbilityScores abilities,
        Optional<Identifier> race, Map<Integer, Integer> usedSpellSlots) {

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

    public static final Codec<CharacterSheet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("class").forGetter(CharacterSheet::characterClass),
            Codec.intRange(Proficiency.MIN_LEVEL, Proficiency.MAX_LEVEL).fieldOf("level")
                    .forGetter(CharacterSheet::level),
            ABILITY_SCORES_CODEC.fieldOf("abilities").forGetter(CharacterSheet::abilities),
            Identifier.CODEC.optionalFieldOf("race").forGetter(CharacterSheet::race),
            Codec.unboundedMap(SPELL_LEVEL_KEY, Codec.intRange(0, 99))
                    .optionalFieldOf("used_spell_slots", Map.of())
                    .forGetter(CharacterSheet::usedSpellSlots)
    ).apply(instance, CharacterSheet::new));

    public CharacterSheet {
        Objects.requireNonNull(characterClass, "characterClass");
        Objects.requireNonNull(abilities, "abilities");
        Objects.requireNonNull(race, "race");
        usedSpellSlots = Map.copyOf(Objects.requireNonNull(usedSpellSlots, "usedSpellSlots"));
        Proficiency.validateLevel(level);
    }

    /** A fresh, classless level 1 character with average scores. */
    public static CharacterSheet initial() {
        return new CharacterSheet(Optional.empty(), 1, AbilityScores.defaults(), Optional.empty(), Map.of());
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
        return new CharacterSheet(Optional.of(id), level, abilities, race, usedSpellSlots);
    }

    public CharacterSheet withAbilities(AbilityScores scores) {
        return new CharacterSheet(characterClass, level, scores, race, usedSpellSlots);
    }

    public CharacterSheet withLevel(int newLevel) {
        return new CharacterSheet(characterClass, newLevel, abilities, race, usedSpellSlots);
    }

    /** Returns a copy that has picked a race, with the race's bonuses applied to its scores. */
    public CharacterSheet withRace(Identifier id, Race definition) {
        Objects.requireNonNull(id, "id");
        return new CharacterSheet(characterClass, level, definition.applyTo(abilities),
                Optional.of(id), usedSpellSlots);
    }

    /** How many slots of a spell level have been spent since the last rest. */
    public int usedSlots(int spellLevel) {
        return usedSpellSlots.getOrDefault(spellLevel, 0);
    }

    /** Returns a copy with one more slot of that level spent. */
    public CharacterSheet withSlotSpent(int spellLevel) {
        Map<Integer, Integer> spent = new java.util.HashMap<>(usedSpellSlots);
        spent.merge(spellLevel, 1, Integer::sum);
        return new CharacterSheet(characterClass, level, abilities, race, spent);
    }

    /** Returns a copy rested: every slot back. Health is restored by {@link HealthService}. */
    public CharacterSheet rested() {
        return new CharacterSheet(characterClass, level, abilities, race, Map.of());
    }

    public boolean hasRace() {
        return race.isPresent();
    }

    /** Whether the player has finished character creation. */
    public boolean hasClass() {
        return characterClass.isPresent();
    }

    public Map<Ability, Integer> scores() {
        return abilities.asMap();
    }
}
