package com.ddc.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Covers the mob-to-hit mapping. The rest of {@link CombatRules} needs live entities and is exercised
 * by booting a server rather than here.
 */
class CombatRulesTest {

    @ParameterizedTest(name = "a mob dealing {0} damage attacks at +{1}")
    @CsvSource({
            "0,0",
            "3,1",    // zombie
            "4,2",    // husk
            "13,6",   // vindicator
            "15,7",   // ravager
    })
    void derivesAMobsToHitFromItsDamage(double damage, int expected) {
        assertEquals(expected, CombatRules.mobAttackBonus(damage));
    }

    @Test
    @DisplayName("a modded boss cannot roll a bonus that beats every armour class automatically")
    void capsRidiculousDamage() {
        assertEquals(CombatRules.MAX_MOB_ATTACK_BONUS, CombatRules.mobAttackBonus(1000));
    }

    @Test
    void neverGoesNegative() {
        assertEquals(0, CombatRules.mobAttackBonus(-5));
    }

    @Test
    @DisplayName("even the strongest mob can still miss the best armour")
    void theRollAlwaysMatters() {
        int bestBonus = CombatRules.mobAttackBonus(1000);
        int toughestArmorClass = 20;

        assertTrue(bestBonus + 1 < toughestArmorClass,
                "a natural 1 must be able to miss, so the bonus alone cannot reach the AC");
    }
}
