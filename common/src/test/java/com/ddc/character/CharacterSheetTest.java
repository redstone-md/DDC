package com.ddc.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.MinecraftBootstrapExtension;
import com.ddc.core.character.Ability;
import com.ddc.core.character.AbilityScores;
import com.ddc.core.character.HitDie;
import com.ddc.core.dice.Die;
import com.ddc.rules.CharacterClass;
import com.mojang.serialization.JsonOps;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CharacterSheetTest {

    private static final Identifier FIGHTER_ID = Identifier.fromNamespaceAndPath("ddc", "fighter");
    private static final CharacterClass FIGHTER = new CharacterClass(
            "Fighter", new HitDie(Die.D10), Ability.STRENGTH,
            Set.of(Ability.STRENGTH, Ability.CONSTITUTION), Optional.empty());

    @BeforeAll
    static void setUp() {
        MinecraftBootstrapExtension.bootstrap();
    }

    private static CharacterSheet prdFighter() {
        return CharacterSheet.initial()
                .withAbilities(AbilityScores.builder()
                        .set(Ability.STRENGTH, 16)
                        .set(Ability.DEXTERITY, 12)
                        .set(Ability.CONSTITUTION, 14)
                        .build())
                .withLevel(5)
                .withClass(FIGHTER_ID, FIGHTER);
    }

    @Test
    void aNewSheetHasNoClassAndStartsAtLevelOne() {
        CharacterSheet sheet = CharacterSheet.initial();

        assertFalse(sheet.hasClass());
        assertEquals(1, sheet.level());
        assertEquals(2, sheet.proficiencyBonus());
    }

    @Test
    @DisplayName("picking a class fills in hit points from the class's die")
    void choosingAClassSetsFullHitPoints() {
        CharacterSheet sheet = prdFighter();

        assertTrue(sheet.hasClass());
        assertEquals(Optional.of(FIGHTER_ID), sheet.characterClass());
        assertEquals(44, sheet.maxHitPoints(FIGHTER), "the PRD's level 5 fighter");
        assertEquals(44, sheet.currentHitPoints());
    }

    @Test
    void hitPointsNeverGoNegative() {
        assertEquals(0, prdFighter().withCurrentHitPoints(-20).currentHitPoints());
    }

    @Test
    void everyChangeReturnsACopy() {
        CharacterSheet original = prdFighter();

        original.withLevel(9).withCurrentHitPoints(1);

        assertEquals(5, original.level());
        assertEquals(44, original.currentHitPoints());
    }

    @Test
    @DisplayName("a sheet survives a save and load unchanged")
    void roundTripsThroughItsCodec() {
        CharacterSheet original = prdFighter().withCurrentHitPoints(17);

        var encoded = CharacterSheet.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(message -> new AssertionError(message));
        CharacterSheet loaded = CharacterSheet.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError(message));

        assertEquals(original, loaded);
        assertEquals(16, loaded.abilities().score(Ability.STRENGTH));
        assertEquals(17, loaded.currentHitPoints());
        assertEquals(Optional.of(FIGHTER_ID), loaded.characterClass());
    }

    @Test
    void aClasslessSheetAlsoRoundTrips() {
        var encoded = CharacterSheet.CODEC.encodeStart(JsonOps.INSTANCE, CharacterSheet.initial())
                .getOrThrow(message -> new AssertionError(message));

        assertEquals(CharacterSheet.initial(), CharacterSheet.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError(message)));
    }

    @Test
    void rejectsALevelOutsideTheProgression() {
        assertTrue(CharacterSheet.CODEC.parse(JsonOps.INSTANCE,
                        com.google.gson.JsonParser.parseString("""
                                {"level": 25, "abilities": {"strength": 10, "dexterity": 10,
                                 "constitution": 10, "intelligence": 10, "wisdom": 10, "charisma": 10},
                                 "current_hit_points": 5}"""))
                .isError());
    }

    @Test
    void proficiencyTracksLevel() {
        assertEquals(3, prdFighter().proficiencyBonus());
        assertEquals(6, prdFighter().withLevel(20).proficiencyBonus());
    }
}
