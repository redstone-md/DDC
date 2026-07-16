package com.ddc.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.core.character.Ability;
import com.ddc.core.character.AbilityScores;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Covers the mapping from Minecraft's armour points onto the SRD's armour, which is a decision DDC
 * makes rather than one either system defines.
 */
class VanillaArmorMappingTest {

    private static AbilityScores dexterity(int score) {
        return AbilityScores.defaults().with(Ability.DEXTERITY, score);
    }

    @ParameterizedTest(name = "{0} armour points is {1}")
    @CsvSource({
            "0,UNARMORED",   // nothing worn
            "7,LIGHT",       // leather
            "8,LIGHT",
            "9,MEDIUM",
            "12,MEDIUM",     // chainmail
            "15,MEDIUM",     // iron
            "17,HEAVY",
            "20,HEAVY",      // diamond and netherite
    })
    void mapsVanillaArmourOntoTheSrdCategories(int points, ArmorCategory expected) {
        assertEquals(expected, ArmorCategory.forArmorPoints(points));
    }

    @Test
    @DisplayName("iron armour lands near chain mail rather than at an unhittable number")
    void ironArmourIsAboutChainMail() {
        ArmorClass iron = ArmorClass.fromVanillaArmor(15);

        assertEquals(17, iron.value(AbilityScores.defaults()), "10 + 7, against chain mail's 16");
    }

    @Test
    @DisplayName("full diamond stays inside the SRD's own ceiling")
    void diamondArmourIsNotUnhittable() {
        ArmorClass diamond = ArmorClass.fromVanillaArmor(20);

        assertEquals(20, diamond.value(dexterity(20)), "heavy armour ignores even a +5 Dexterity");
        assertTrue(diamond.value(dexterity(20)) <= 21,
                "plate and shield is AC 20 in the SRD; nothing here may beat that by much");
    }

    @Test
    void unarmouredIsTenPlusDexterity() {
        assertEquals(13, ArmorClass.fromVanillaArmor(0).value(dexterity(16)));
    }

    @Test
    @DisplayName("leather keeps a rogue's Dexterity; iron does not")
    void lightArmourKeepsDexterityAndMediumCapsIt() {
        assertEquals(3, ArmorClass.fromVanillaArmor(7).effectiveDexterityModifier(dexterity(16)));
        assertEquals(2, ArmorClass.fromVanillaArmor(15).effectiveDexterityModifier(dexterity(16)));
    }

    @ParameterizedTest
    @ValueSource(ints = {-5, -1})
    void treatsNegativeArmourAsNone(int points) {
        assertEquals(10, ArmorClass.fromVanillaArmor(points).value(AbilityScores.defaults()));
    }

    @Test
    void everyArmourValueProducesAReachableArmourClass() {
        for (int points = 0; points <= 30; points++) {
            int ac = ArmorClass.fromVanillaArmor(points).value(dexterity(20));
            assertTrue(ac >= 10 && ac <= 25, points + " armour points gave AC " + ac);
        }
    }
}
