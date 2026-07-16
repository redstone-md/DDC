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

    /**
     * The category a suit of vanilla Minecraft armour falls into, from its armour points.
     *
     * <p>Minecraft describes armour as points from 0 to 20; the SRD describes it as light, medium or
     * heavy. Nothing in either system maps them, so DDC picks the boundaries and states them here:
     * leather (7) is light, chain and iron (12 and 15) are medium, diamond and netherite (20) are
     * heavy. The effect a player feels is how much of their Dexterity still counts, which is what
     * makes plate feel like plate.
     *
     * @param armorPoints vanilla armour points, as an entity reports them
     */
    public static ArmorCategory forArmorPoints(int armorPoints) {
        if (armorPoints <= 0) {
            return UNARMORED;
        }
        if (armorPoints <= 8) {
            return LIGHT;
        }
        return armorPoints <= 16 ? MEDIUM : HEAVY;
    }

    /** Applies the cap to a Dexterity modifier. Negative modifiers always apply in full. */
    public int applyDexterityCap(int dexterityModifier) {
        return dexterityCap == null ? dexterityModifier : Math.min(dexterityModifier, dexterityCap);
    }
}
