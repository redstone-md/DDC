package com.ddc.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AbilityScoresTest {

    @ParameterizedTest
    @EnumSource(Ability.class)
    void defaultsEveryAbilityToTen(Ability ability) {
        assertEquals(10, AbilityScores.defaults().score(ability));
        assertEquals(0, AbilityScores.defaults().modifier(ability));
    }

    @Test
    void buildsTheFighterFromThePrd() {
        AbilityScores scores = AbilityScores.builder()
                .set(Ability.STRENGTH, 16)
                .set(Ability.DEXTERITY, 12)
                .set(Ability.CONSTITUTION, 14)
                .set(Ability.INTELLIGENCE, 8)
                .set(Ability.WISDOM, 10)
                .set(Ability.CHARISMA, 14)
                .build();

        assertEquals(3, scores.modifier(Ability.STRENGTH));
        assertEquals(1, scores.modifier(Ability.DEXTERITY));
        assertEquals(2, scores.modifier(Ability.CONSTITUTION));
        assertEquals(-1, scores.modifier(Ability.INTELLIGENCE));
        assertEquals(0, scores.modifier(Ability.WISDOM));
        assertEquals(2, scores.modifier(Ability.CHARISMA));
    }

    @Test
    void rejectsAPartialMapRatherThanDefaultingTheGap() {
        Map<Ability, Integer> partial = new EnumMap<>(Ability.class);
        partial.put(Ability.STRENGTH, 16);

        assertThrows(IllegalArgumentException.class, () -> AbilityScores.of(partial));
    }

    @Test
    void rejectsAnOutOfRangeScore() {
        assertThrows(IllegalArgumentException.class, () -> AbilityScores.builder().set(Ability.STRENGTH, 31));
        assertThrows(IllegalArgumentException.class, () -> AbilityScores.defaults().with(Ability.STRENGTH, 0));
    }

    @Test
    void withReturnsACopyAndLeavesTheOriginalAlone() {
        AbilityScores original = AbilityScores.defaults();

        AbilityScores raised = original.with(Ability.STRENGTH, 18);

        assertEquals(18, raised.score(Ability.STRENGTH));
        assertEquals(10, original.score(Ability.STRENGTH));
    }

    @Test
    void plusClampsInsteadOfThrowingBecauseBuffsAndDrainsOvershoot() {
        AbilityScores scores = AbilityScores.defaults().with(Ability.STRENGTH, 29);

        assertEquals(30, scores.plus(Ability.STRENGTH, 4).score(Ability.STRENGTH));
        assertEquals(1, scores.plus(Ability.STRENGTH, -40).score(Ability.STRENGTH));
    }

    @Test
    void equalsComparesScoresNotIdentity() {
        assertEquals(AbilityScores.defaults(), AbilityScores.defaults().with(Ability.WISDOM, 10));
        assertEquals(AbilityScores.defaults().hashCode(), AbilityScores.defaults().hashCode());
    }

    @Test
    void asMapReturnsADetachedCopy() {
        AbilityScores scores = AbilityScores.defaults();

        scores.asMap().put(Ability.STRENGTH, 20);

        assertEquals(10, scores.score(Ability.STRENGTH));
    }
}
