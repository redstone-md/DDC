package com.ddc.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.core.character.Ability;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SpellcastingTest {

    /** The SRD full-caster table, as the built-in wizard and cleric ship it. */
    private static Spellcasting fullCaster() throws IOException {
        try (InputStream in = SpellcastingTest.class.getResourceAsStream("/data/ddc/ddc_classes/wizard.json")) {
            CharacterClass wizard = CharacterClass.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseString(
                            new String(in.readAllBytes(), StandardCharsets.UTF_8)))
                    .getOrThrow(message -> new AssertionError(message));
            return wizard.spellcasting().orElseThrow();
        }
    }

    @ParameterizedTest(name = "a level {0} wizard has {2} slot(s) of spell level {1}")
    @CsvSource({
            "1,1,2", "1,2,0",
            "3,1,4", "3,2,2", "3,3,0",
            "5,3,2", "5,4,0",
            "20,9,1", "20,1,4",
    })
    void reproducesTheSrdSlotTable(int characterLevel, int spellLevel, int expected) throws IOException {
        assertEquals(expected, fullCaster().slotsFor(characterLevel, spellLevel));
    }

    @Test
    @DisplayName("a fireball needs level 5, which is when the third-level slots arrive")
    void fireballBecomesCastableAtLevelFive() throws IOException {
        Spellcasting casting = fullCaster();

        assertEquals(0, casting.slotsFor(4, 3));
        assertEquals(2, casting.slotsFor(5, 3));
    }

    @Test
    void reportsTheHighestSlotItHas() throws IOException {
        Spellcasting casting = fullCaster();

        assertEquals(1, casting.highestSlotLevel(1));
        assertEquals(3, casting.highestSlotLevel(5));
        assertEquals(9, casting.highestSlotLevel(20));
    }

    @Test
    void theBuiltInClassesCastWithTheRightAbility() throws IOException {
        assertEquals(Ability.INTELLIGENCE, fullCaster().ability());
    }

    @Test
    @DisplayName("a fighter has no spellcasting at all")
    void aNonCasterHasNoTable() throws IOException {
        try (InputStream in = SpellcastingTest.class.getResourceAsStream("/data/ddc/ddc_classes/fighter.json")) {
            CharacterClass fighter = CharacterClass.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseString(
                            new String(in.readAllBytes(), StandardCharsets.UTF_8)))
                    .getOrThrow(message -> new AssertionError(message));

            assertTrue(fighter.spellcasting().isEmpty());
            assertTrue(!fighter.canCast());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10})
    void hasNoSlotsForALevelOutsideTheSpellLevels(int spellLevel) throws IOException {
        assertEquals(0, fullCaster().slotsFor(5, spellLevel));
    }

    @Test
    @DisplayName("a short table keeps its last row rather than dropping a caster's magic")
    void aTableShorterThanTheCharacterKeepsItsLastRow() {
        Spellcasting stubby = new Spellcasting(Ability.CHARISMA, List.of(List.of(2), List.of(3)));

        assertEquals(3, stubby.slotsFor(20, 1));
    }

    @Test
    void rejectsAnEmptyTable() {
        assertThrows(IllegalArgumentException.class, () -> new Spellcasting(Ability.WISDOM, List.of()));
    }

    @Test
    void rejectsATableLongerThanTheLevelProgression() {
        List<List<Integer>> tooLong = java.util.Collections.nCopies(21, List.of(1));

        assertThrows(IllegalArgumentException.class, () -> new Spellcasting(Ability.WISDOM, tooLong));
    }

    /**
     * A pack author's mistake has to come back as an error against their file. If the codec let the
     * constructor throw instead, one broken addon would take down the whole reload.
     */
    @Test
    @DisplayName("a broken slot table is reported, not thrown")
    void reportsABrokenTableRatherThanThrowing() {
        var result = CharacterClass.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"name": "Broken", "hit_die": "d8", "primary_ability": "wisdom",
                 "spellcasting": {"ability": "wisdom", "slots": []}}"""));

        assertTrue(result.isError());
        assertTrue(result.error().orElseThrow().message().contains("slot table"));
    }
}
