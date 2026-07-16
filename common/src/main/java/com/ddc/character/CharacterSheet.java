package com.ddc.character;

import com.ddc.core.character.Ability;
import com.ddc.core.character.AbilityScores;
import com.ddc.core.character.Proficiency;
import com.ddc.rules.CharacterClass;
import com.ddc.rules.DDCCodecs;
import com.mojang.serialization.Codec;
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
 * @param characterClass    the class the player picked, empty until they pick one
 * @param level             the character's level
 * @param abilities         the six ability scores
 * @param currentHitPoints  hit points remaining; capped against the derived maximum on use
 */
public record CharacterSheet(Optional<Identifier> characterClass, int level, AbilityScores abilities,
        int currentHitPoints) {

    private static final Codec<AbilityScores> ABILITY_SCORES_CODEC =
            Codec.unboundedMap(DDCCodecs.ABILITY, Codec.intRange(Ability.MIN_SCORE, Ability.MAX_SCORE))
                    .xmap(AbilityScores::of, scores -> new EnumMap<>(scores.asMap()));

    public static final Codec<CharacterSheet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("class").forGetter(CharacterSheet::characterClass),
            Codec.intRange(Proficiency.MIN_LEVEL, Proficiency.MAX_LEVEL).fieldOf("level")
                    .forGetter(CharacterSheet::level),
            ABILITY_SCORES_CODEC.fieldOf("abilities").forGetter(CharacterSheet::abilities),
            Codec.INT.fieldOf("current_hit_points").forGetter(CharacterSheet::currentHitPoints)
    ).apply(instance, CharacterSheet::new));

    public CharacterSheet {
        Objects.requireNonNull(characterClass, "characterClass");
        Objects.requireNonNull(abilities, "abilities");
        Proficiency.validateLevel(level);
    }

    /** A fresh, classless level 1 character with average scores. */
    public static CharacterSheet initial() {
        return new CharacterSheet(Optional.empty(), 1, AbilityScores.defaults(), 0);
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

    /** Returns a copy that has picked a class and starts at full health for it. */
    public CharacterSheet withClass(Identifier id, CharacterClass definition) {
        Objects.requireNonNull(id, "id");
        CharacterSheet chosen = new CharacterSheet(Optional.of(id), level, abilities, currentHitPoints);
        return chosen.withCurrentHitPoints(chosen.maxHitPoints(definition));
    }

    public CharacterSheet withAbilities(AbilityScores scores) {
        return new CharacterSheet(characterClass, level, scores, currentHitPoints);
    }

    public CharacterSheet withLevel(int newLevel) {
        return new CharacterSheet(characterClass, newLevel, abilities, currentHitPoints);
    }

    /** Clamps at zero: a sheet never carries negative hit points. */
    public CharacterSheet withCurrentHitPoints(int hitPoints) {
        return new CharacterSheet(characterClass, level, abilities, Math.max(0, hitPoints));
    }

    /** Whether the player has finished character creation. */
    public boolean hasClass() {
        return characterClass.isPresent();
    }

    public Map<Ability, Integer> scores() {
        return abilities.asMap();
    }
}
