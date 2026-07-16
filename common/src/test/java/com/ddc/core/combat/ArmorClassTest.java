package com.ddc.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ddc.core.character.Ability;
import com.ddc.core.character.AbilityScores;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArmorClassTest {

    private static AbilityScores withDexterity(int score) {
        return AbilityScores.defaults().with(Ability.DEXTERITY, score);
    }

    @Test
    void anUnarmoredCharacterSitsAtTenPlusDexterity() {
        assertEquals(13, ArmorClass.unarmored().value(withDexterity(16)));
        assertEquals(10, ArmorClass.unarmored().value(AbilityScores.defaults()));
    }

    @Test
    @DisplayName("the PRD fighter: chain mail on a DEX 12 character gives the AC 16 on the sheet")
    void matchesThePrdFighterSheet() {
        ArmorClass chainMail = ArmorClass.of(6, ArmorCategory.HEAVY);

        assertEquals(16, chainMail.value(withDexterity(12)));
        assertEquals(18, chainMail.withShield(2).value(withDexterity(12)), "a shield takes them to 18");
    }

    @Test
    void lightArmorAppliesFullDexterity() {
        assertEquals(15, ArmorClass.of(1, ArmorCategory.LIGHT).value(withDexterity(18)));
    }

    @Test
    void mediumArmorCapsDexterityAtTwo() {
        ArmorClass armor = ArmorClass.of(4, ArmorCategory.MEDIUM);

        assertEquals(16, armor.value(withDexterity(18)), "a +4 Dexterity must contribute only +2");
        assertEquals(15, armor.value(withDexterity(12)), "a +1 Dexterity is under the cap and applies in full");
    }

    @Test
    void heavyArmorIgnoresDexterityEntirely() {
        ArmorClass armor = ArmorClass.of(8, ArmorCategory.HEAVY);

        assertEquals(18, armor.value(withDexterity(20)));
        assertEquals(18, armor.value(withDexterity(10)));
    }

    @Test
    void aDexterityPenaltyAppliesEvenUnderACap() {
        assertEquals(9, ArmorClass.of(2, ArmorCategory.MEDIUM).value(withDexterity(4)));
    }

    @Test
    void shieldAndMiscStack() {
        ArmorClass armor = ArmorClass.of(2, ArmorCategory.LIGHT).withShield(2).withMisc(1);

        assertEquals(16, armor.value(withDexterity(12)));
    }

    @Test
    void miscMayBeNegativeForCursesAndDebuffs() {
        assertEquals(8, ArmorClass.unarmored().withMisc(-2).value(AbilityScores.defaults()));
    }

    @Test
    void rejectsNegativeArmorAndShieldBonuses() {
        assertThrows(IllegalArgumentException.class, () -> ArmorClass.of(-1, ArmorCategory.LIGHT));
        assertThrows(IllegalArgumentException.class, () -> ArmorClass.unarmored().withShield(-1));
    }

    @Test
    void reportsTheDexterityItActuallyUsed() {
        assertEquals(2, ArmorClass.of(4, ArmorCategory.MEDIUM).effectiveDexterityModifier(withDexterity(18)));
        assertEquals(0, ArmorClass.of(8, ArmorCategory.HEAVY).effectiveDexterityModifier(withDexterity(18)));
        assertEquals(4, ArmorClass.of(1, ArmorCategory.LIGHT).effectiveDexterityModifier(withDexterity(18)));
    }
}
