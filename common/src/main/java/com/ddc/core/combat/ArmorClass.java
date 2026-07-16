package com.ddc.core.combat;

import com.ddc.core.character.Ability;
import com.ddc.core.character.AbilityScores;
import java.util.Objects;

/**
 * How hard a character is to hit.
 *
 * <p>{@code 10 + capped Dexterity modifier + armour + shield + misc}, replacing vanilla armour
 * points entirely. The Dexterity cap comes from {@link ArmorCategory}.
 *
 * @param armorBonus  what the worn armour contributes above the base, 0 when unarmoured
 * @param category    the armour's category, which decides the Dexterity cap
 * @param shieldBonus what a shield contributes, 0 when none is held
 * @param miscBonus   spells, class features, and magic items; may be negative
 */
public record ArmorClass(int armorBonus, ArmorCategory category, int shieldBonus, int miscBonus) {

    /** The floor every character starts from before any modifier. */
    public static final int BASE = 10;

    public ArmorClass {
        Objects.requireNonNull(category, "category");
        if (armorBonus < 0) {
            throw new IllegalArgumentException("Armor bonus cannot be negative: " + armorBonus);
        }
        if (shieldBonus < 0) {
            throw new IllegalArgumentException("Shield bonus cannot be negative: " + shieldBonus);
        }
    }

    /** An unarmoured, unshielded character. */
    public static ArmorClass unarmored() {
        return new ArmorClass(0, ArmorCategory.UNARMORED, 0, 0);
    }

    public static ArmorClass of(int armorBonus, ArmorCategory category) {
        return new ArmorClass(armorBonus, category, 0, 0);
    }

    public ArmorClass withShield(int shieldBonus) {
        return new ArmorClass(armorBonus, category, shieldBonus, miscBonus);
    }

    public ArmorClass withMisc(int miscBonus) {
        return new ArmorClass(armorBonus, category, shieldBonus, miscBonus);
    }

    /** The Dexterity actually contributing, after the armour's cap. */
    public int effectiveDexterityModifier(AbilityScores scores) {
        Objects.requireNonNull(scores, "scores");
        return category.applyDexterityCap(scores.modifier(Ability.DEXTERITY));
    }

    /** The number an attack roll must meet or beat. */
    public int value(AbilityScores scores) {
        return BASE + effectiveDexterityModifier(scores) + armorBonus + shieldBonus + miscBonus;
    }
}
