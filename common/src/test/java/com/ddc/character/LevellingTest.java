package com.ddc.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.core.character.LevelTable;
import com.ddc.core.character.Proficiency;
import com.ddc.rules.CharacterClass;
import com.ddc.rules.TestRegistries;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A character earning their levels, which until now they could not do.
 *
 * <p>The sheet could hold a level and nothing but a test ever set one. These cover the way in: that
 * experience buys a level, that the level always agrees with the experience that earned it, and that
 * a pack's own table is what decides.
 */
class LevellingTest {

    private static final CharacterClass FIGHTER = TestRegistries.characterClass("Fighter");

    @Test
    @DisplayName("experience earns a level")
    void experienceLevelsACharacter() {
        CharacterSheet sheet = CharacterSheet.initial().withExperienceGained(300, FIGHTER);

        assertEquals(2, sheet.level());
        assertEquals(300, sheet.experience());
    }

    @Test
    @DisplayName("a level short of the next one does not round up")
    void notQuiteEnoughIsNotEnough() {
        CharacterSheet sheet = CharacterSheet.initial().withExperienceGained(299, FIGHTER);

        assertEquals(1, sheet.level());
    }

    @Test
    @DisplayName("experience accumulates across awards")
    void experienceAddsUp() {
        CharacterSheet sheet = CharacterSheet.initial()
                .withExperienceGained(200, FIGHTER)
                .withExperienceGained(100, FIGHTER);

        assertEquals(300, sheet.experience());
        assertEquals(2, sheet.level());
    }

    @Test
    @DisplayName("proficiency and hit points follow the level up")
    void levellingChangesWhatFollowsFromIt() {
        CharacterSheet first = CharacterSheet.initial().withClass(
                net.minecraft.resources.Identifier.parse("ddc:fighter"), FIGHTER);
        CharacterSheet fifth = first.withExperienceGained(6_500, FIGHTER);

        assertEquals(5, fifth.level());
        assertEquals(3, fifth.proficiencyBonus());
        assertTrue(fifth.maxHitPoints(FIGHTER) > first.maxHitPoints(FIGHTER),
                "a level 5 fighter has more hit points than a level 1 one");
    }

    @Test
    @DisplayName("a pack that levels faster levels the character faster")
    void thePacksTableIsWhatDecides() {
        CharacterClass brisk = new CharacterClass(FIGHTER.name(), FIGHTER.hitDie(), FIGHTER.primaryAbility(),
                FIGHTER.savingThrows(), FIGHTER.spellcasting(), FIGHTER.features(),
                new LevelTable(java.util.stream.IntStream.rangeClosed(1, Proficiency.MAX_LEVEL - 1)
                        .map(step -> step * 10).boxed().toList()));

        assertEquals(2, CharacterSheet.initial().withExperienceGained(10, brisk).level());
        assertEquals(1, CharacterSheet.initial().withExperienceGained(10, FIGHTER).level());
    }

    @Test
    @DisplayName("a class with no table of its own gets the SRD's")
    void theTableIsOptionalInAPack() {
        CharacterClass parsed = CharacterClass.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"name": "Paladin", "hit_die": "d10", "primary_ability": "strength"}"""))
                .getOrThrow(message -> new AssertionError(message));

        assertEquals(LevelTable.SRD, parsed.leveling());
    }

    @Test
    @DisplayName("a pack's own table survives the round trip")
    void aPackMayWriteATable() {
        String table = java.util.stream.IntStream.rangeClosed(1, Proficiency.MAX_LEVEL - 1)
                .mapToObj(step -> String.valueOf(step * 100))
                .collect(java.util.stream.Collectors.joining(", "));
        CharacterClass parsed = CharacterClass.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"name": "Paladin", "hit_die": "d10", "primary_ability": "strength",
                 "leveling": [%s]}""".formatted(table)))
                .getOrThrow(message -> new AssertionError(message));

        assertEquals(100, parsed.leveling().experienceFor(2));
        assertEquals(2, parsed.leveling().levelFor(150));
    }

    @Test
    @DisplayName("a broken table is an error against its own file, not a crash")
    void aBadTableIsReported() {
        var result = CharacterClass.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"name": "Paladin", "hit_die": "d10", "primary_ability": "strength",
                 "leveling": [300, 100]}"""));

        assertTrue(result.error().isPresent(), "a two-entry table cannot cover nineteen levels");
    }

    @Test
    void experienceCannotGoBackwards() {
        assertThrows(IllegalArgumentException.class,
                () -> CharacterSheet.initial().withExperienceGained(-1, FIGHTER));
    }

    @Test
    @DisplayName("a sheet saved before levelling existed still loads")
    void oldSheetsLoadAtZero() {
        CharacterSheet parsed = CharacterSheet.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"level": 1, "abilities": {"strength": 10, "dexterity": 10, "constitution": 10,
                 "intelligence": 10, "wisdom": 10, "charisma": 10}}"""))
                .getOrThrow(message -> new AssertionError(message));

        assertEquals(0, parsed.experience());
    }
}
