package com.ddc.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.core.character.Ability;
import com.ddc.core.dice.Die;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Covers the data pack contract: the JSON an addon author writes is what the codec accepts, and a
 * mistake in it is reported rather than defaulted away.
 */
class CharacterClassTest {

    private static DataResult<CharacterClass> parse(String json) {
        return CharacterClass.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static CharacterClass parseOrThrow(String json) {
        return parse(json).getOrThrow(message -> new AssertionError(message));
    }

    @Test
    void readsAClassFromJson() {
        CharacterClass fighter = parseOrThrow("""
                {
                  "name": "Fighter",
                  "hit_die": "d10",
                  "primary_ability": "strength",
                  "saving_throws": ["strength", "constitution"]
                }
                """);

        assertEquals("Fighter", fighter.name());
        assertEquals(Die.D10, fighter.hitDie().die());
        assertEquals(Ability.STRENGTH, fighter.primaryAbility());
        assertTrue(fighter.isProficientInSave(Ability.CONSTITUTION));
        assertFalse(fighter.isProficientInSave(Ability.CHARISMA));
    }

    @Test
    @DisplayName("saving throws are optional, because a homebrew class may grant none")
    void savingThrowsDefaultToNone() {
        CharacterClass barbarian = parseOrThrow("""
                {"name": "Brawler", "hit_die": "d12", "primary_ability": "STR"}
                """);

        assertTrue(barbarian.savingThrows().isEmpty());
    }

    @Test
    void reportsAnUnknownAbilityByName() {
        DataResult<CharacterClass> result = parse("""
                {"name": "Bard", "hit_die": "d8", "primary_ability": "luck"}
                """);

        assertTrue(result.isError());
        assertTrue(result.error().orElseThrow().message().contains("luck"),
                "the error must name the offending value so the pack author can find it");
    }

    @Test
    void reportsAnUnsupportedDie() {
        DataResult<CharacterClass> result = parse("""
                {"name": "Bard", "hit_die": "d7", "primary_ability": "charisma"}
                """);

        assertTrue(result.isError());
        assertTrue(result.error().orElseThrow().message().contains("d7"));
    }

    @Test
    void reportsAMissingRequiredField() {
        assertTrue(parse("{\"name\": \"Bard\"}").isError());
    }

    @Test
    void roundTripsBackToJson() {
        CharacterClass original = parseOrThrow("""
                {"name": "Rogue", "hit_die": "d8", "primary_ability": "dexterity",
                 "saving_throws": ["dexterity"]}
                """);

        var encoded = CharacterClass.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(message -> new AssertionError(message));

        assertEquals(original, CharacterClass.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError(message)));
    }

    /** The built-in pack is shipped, not tested by hand: if a file rots, this fails. */
    @ParameterizedTest(name = "the built-in {0} loads")
    @CsvSource({"fighter,d10,STRENGTH", "wizard,d6,INTELLIGENCE", "rogue,d8,DEXTERITY", "cleric,d8,WISDOM"})
    void theBuiltInSrdClassesLoad(String file, String die, Ability primary) throws IOException {
        String path = "/data/ddc/ddc_classes/" + file + ".json";
        try (InputStream in = CharacterClassTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing built-in class file: " + path);
            CharacterClass loaded = parseOrThrow(new String(in.readAllBytes(), StandardCharsets.UTF_8));

            assertEquals(die, loaded.hitDie().die().toString());
            assertEquals(primary, loaded.primaryAbility());
        }
    }
}
