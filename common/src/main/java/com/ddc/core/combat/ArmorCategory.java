package com.ddc.core.combat;

import java.util.OptionalInt;

/**
 * How much Dexterity an armour lets its wearer use.
 *
 * <p>{@link ArmorClass} folds this cap in, which is why the AC formula here is slightly stricter
 * than the flat {@code 10 + DEX + armor + shield} sketched in ARCHITECTURE.md: without a cap, plate
 * armour and a high Dexterity stack into an AC the SRD never allows.
 */
public enum ArmorCategory {
    /** No armour worn. Full Dexterity applies. */
    UNARMORED(null),
    /** Light armour. Full Dexterity applies. */
    LIGHT(null),
    /** Medium armour. Dexterity contributes at most +2. */
    MEDIUM(2),
    /** Heavy armour. Dexterity does not contribute at all. */
    HEAVY(0);

    private final Integer dexterityCap;

    ArmorCategory(Integer dexterityCap) {
        this.dexterityCap = dexterityCap;
    }

    /** The most Dexterity this category allows, or empty when uncapped. */
    public OptionalInt dexterityCap() {
        return dexterityCap == null ? OptionalInt.empty() : OptionalInt.of(dexterityCap);
    }

    /** Applies the cap to a Dexterity modifier. Negative modifiers always apply in full. */
    public int applyDexterityCap(int dexterityModifier) {
        return dexterityCap == null ? dexterityModifier : Math.min(dexterityModifier, dexterityCap);
    }
}
