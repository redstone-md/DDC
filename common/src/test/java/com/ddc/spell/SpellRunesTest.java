package com.ddc.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.MinecraftBootstrapExtension;
import com.ddc.rules.Spell;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Covers the rune's size, which is the part of PRD 4.4's warning that has no screen in it. */
class SpellRunesTest {

    @BeforeAll
    static void setUp() {
        MinecraftBootstrapExtension.bootstrap();
    }

    private static Spell spell(int level) {
        return Spell.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                        "{\"name\": \"Test\", \"level\": " + level + "}"))
                .getOrThrow(message -> new AssertionError(message));
    }

    @ParameterizedTest(name = "a level {0} spell draws a {1} block rune")
    @CsvSource({"0,0.8", "1,1.2", "3,2.0", "9,4.0"})
    void theRuneGrowsWithTheSpell(int level, double expected) {
        assertEquals(expected, SpellRunes.radiusOf(spell(level)), 1e-9);
    }

    @Test
    @DisplayName("a cantrip scratches the floor; a ninth-level spell does not carpet the room")
    void theRuneStaysWithinItsBounds() {
        for (int level = 0; level <= 9; level++) {
            double radius = SpellRunes.radiusOf(spell(level));
            assertTrue(radius >= 0.8 && radius <= 4.0, "level " + level + " drew " + radius);
        }
    }

    @Test
    void aBiggerSpellNeverDrawsASmallerRune() {
        for (int level = 1; level <= 9; level++) {
            assertTrue(SpellRunes.radiusOf(spell(level)) >= SpellRunes.radiusOf(spell(level - 1)));
        }
    }
}
