package com.ddc.rules;

import com.ddc.core.character.Ability;
import com.ddc.core.character.HitDie;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A character class, as a data pack describes it.
 *
 * <p>Nothing about a class is compiled in. The mod ships these as JSON under {@code
 * data/ddc/ddc_classes/}, and an addon adds its own under its own namespace with no Java at all,
 * which is the whole point of ADR-0002.
 *
 * <p>Example, {@code data/ddc/ddc_classes/fighter.json}:
 * <pre>{@code
 * {
 *   "name": "Fighter",
 *   "hit_die": "d10",
 *   "primary_ability": "strength",
 *   "saving_throws": ["strength", "constitution"]
 * }
 * }</pre>
 *
 * @param name          the display name shown on the sheet
 * @param hitDie        the die this class rolls for hit points
 * @param primaryAbility the ability the class is built around
 * @param savingThrows  the saving throws the class is proficient in
 * @param spellcasting  what it can cast, absent for a class with no magic
 */
public record CharacterClass(String name, HitDie hitDie, Ability primaryAbility, Set<Ability> savingThrows,
        Optional<Spellcasting> spellcasting) {

    public static final Codec<CharacterClass> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(CharacterClass::name),
            DDCCodecs.DIE.xmap(HitDie::new, HitDie::die).fieldOf("hit_die").forGetter(CharacterClass::hitDie),
            DDCCodecs.ABILITY.fieldOf("primary_ability").forGetter(CharacterClass::primaryAbility),
            DDCCodecs.ABILITY.listOf().xmap(Set::copyOf, List::copyOf)
                    .optionalFieldOf("saving_throws", Set.of())
                    .forGetter(CharacterClass::savingThrows),
            Spellcasting.CODEC.optionalFieldOf("spellcasting").forGetter(CharacterClass::spellcasting)
    ).apply(instance, CharacterClass::new));

    public CharacterClass {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(hitDie, "hitDie");
        Objects.requireNonNull(primaryAbility, "primaryAbility");
        savingThrows = Set.copyOf(Objects.requireNonNull(savingThrows, "savingThrows"));
        Objects.requireNonNull(spellcasting, "spellcasting");
    }

    /** Whether this class casts spells at all. */
    public boolean canCast() {
        return spellcasting.isPresent();
    }

    /** Whether this class adds its proficiency bonus to a saving throw. */
    public boolean isProficientInSave(Ability ability) {
        return savingThrows.contains(ability);
    }
}
