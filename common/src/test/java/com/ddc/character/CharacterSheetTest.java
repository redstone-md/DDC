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
import net.minecraft.nbt.NbtOps;
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
            Set.of(Ability.STRENGTH, Ability.CONSTITUTION), Optional.empty(), java.util.List.of(),
            com.ddc.core.character.LevelTable.SRD);

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
    @DisplayName("picking a class decides how many hit points the class is worth")
    void choosingAClassSetsTheMaximum() {
        CharacterSheet sheet = prdFighter();

        assertTrue(sheet.hasClass());
        assertEquals(Optional.of(FIGHTER_ID), sheet.characterClass());
        assertEquals(44, sheet.maxHitPoints(FIGHTER), "the PRD's level 5 fighter");
    }

    @Test
    void everyChangeReturnsACopy() {
        CharacterSheet original = prdFighter();

        original.withLevel(9).withSlotSpent(1);

        assertEquals(5, original.level());
        assertEquals(0, original.usedSlots(1));
    }

    @Test
    @DisplayName("a rest gives every slot back")
    void restingClearsSpentSlots() {
        CharacterSheet spent = prdFighter().withSlotSpent(1).withSlotSpent(1).withSlotSpent(3);

        assertEquals(2, spent.usedSlots(1));
        assertEquals(0, spent.rested().usedSlots(1));
        assertEquals(0, spent.rested().usedSlots(3));
    }

    @Test
    @DisplayName("a sheet survives a save and load unchanged")
    void roundTripsThroughItsCodec() {
        CharacterSheet original = prdFighter().withSlotSpent(2);

        var encoded = CharacterSheet.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(message -> new AssertionError(message));
        CharacterSheet loaded = CharacterSheet.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError(message));

        assertEquals(original, loaded);
        assertEquals(16, loaded.abilities().score(Ability.STRENGTH));
        assertEquals(1, loaded.usedSlots(2));
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
                                 "constitution": 10, "intelligence": 10, "wisdom": 10, "charisma": 10}}"""))
                .isError());
    }

    /**
     * The world saves as NBT, not JSON, and the two disagree about what a map key may be. Round
     * tripping through JsonOps alone missed exactly that: 1.1.0 shipped with an int-keyed slot map
     * that threw "Not a string" the first time a caster's sheet was saved.
     */
    @Test
    @DisplayName("a sheet with spent slots survives the format the world actually saves in")
    void roundTripsThroughNbtAsTheWorldSavesIt() {
        CharacterSheet original = prdFighter().withSlotSpent(1).withSlotSpent(3);

        var encoded = CharacterSheet.CODEC.encodeStart(NbtOps.INSTANCE, original)
                .getOrThrow(message -> new AssertionError("saving a sheet failed: " + message));
        CharacterSheet loaded = CharacterSheet.CODEC.parse(NbtOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError("loading a sheet failed: " + message));

        assertEquals(original, loaded);
        assertEquals(1, loaded.usedSlots(1));
        assertEquals(1, loaded.usedSlots(3));
    }

    @Test
    void refusesASpellLevelThatIsNotOne() {
        assertTrue(CharacterSheet.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString("""
                {"level": 5, "abilities": {"strength": 10, "dexterity": 10, "constitution": 10,
                 "intelligence": 10, "wisdom": 10, "charisma": 10},
                 "used_spell_slots": {"banana": 1}}""")).isError());
    }

    @Test
    void proficiencyTracksLevel() {
        assertEquals(3, prdFighter().proficiencyBonus());
        assertEquals(6, prdFighter().withLevel(20).proficiencyBonus());
    }
}
