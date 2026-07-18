package com.ddc.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.MinecraftBootstrapExtension;
import com.ddc.core.character.Ability;
import com.ddc.core.character.AbilityScores;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Covers the wizard's spellbook: what it holds, how much, and that it survives a save. */
class PreparedSpellsTest {

    private static final ResourceLocation FIREBALL = ResourceLocation.fromNamespaceAndPath("ddc", "fireball");
    private static final ResourceLocation MAGIC_MISSILE = ResourceLocation.fromNamespaceAndPath("ddc", "magic_missile");

    @BeforeAll
    static void setUp() {
        MinecraftBootstrapExtension.bootstrap();
    }

    private static CharacterSheet wizard(int level, int intelligence) {
        return CharacterSheet.initial()
                .withAbilities(AbilityScores.defaults().with(Ability.INTELLIGENCE, intelligence))
                .withLevel(level);
    }

    @Test
    void aNewSheetHasAnEmptyBook() {
        assertTrue(CharacterSheet.initial().preparedSpells().isEmpty());
        assertFalse(CharacterSheet.initial().hasPrepared(FIREBALL));
    }

    @Test
    void writingASpellInPutsItInTheBook() {
        CharacterSheet sheet = wizard(5, 16).withPrepared(FIREBALL);

        assertTrue(sheet.hasPrepared(FIREBALL));
        assertFalse(sheet.hasPrepared(MAGIC_MISSILE));
    }

    @Test
    void writingTheSameSpellTwiceDoesNotFillTheBookTwice() {
        CharacterSheet sheet = wizard(5, 16).withPrepared(FIREBALL).withPrepared(FIREBALL);

        assertEquals(1, sheet.preparedSpells().size());
    }

    @Test
    void scrubbingASpellOutMakesRoom() {
        CharacterSheet sheet = wizard(5, 16).withPrepared(FIREBALL).withPrepared(MAGIC_MISSILE);

        assertEquals(1, sheet.withoutPrepared(FIREBALL).preparedSpells().size());
        assertFalse(sheet.withoutPrepared(FIREBALL).hasPrepared(FIREBALL));
    }

    @Test
    void writingReturnsACopy() {
        CharacterSheet original = wizard(5, 16);

        original.withPrepared(FIREBALL);

        assertTrue(original.preparedSpells().isEmpty());
    }

    @ParameterizedTest(name = "a level {0} wizard with {1} Intelligence prepares {2} spells")
    @CsvSource({"1,16,4", "5,16,8", "5,20,10", "1,10,1", "20,20,25"})
    void theBookHoldsTheSrdsCountOfSpells(int level, int intelligence, int expected) {
        assertEquals(expected, wizard(level, intelligence).preparedSpellLimit(Ability.INTELLIGENCE));
    }

    @Test
    @DisplayName("a hopeless caster may still prepare one spell")
    void theLimitIsNeverZero() {
        assertEquals(1, wizard(1, 1).preparedSpellLimit(Ability.INTELLIGENCE));
    }

    @Test
    @DisplayName("a rest does not wipe the book: sleeping does not unlearn magic")
    void restingKeepsTheBook() {
        CharacterSheet rested = wizard(5, 16).withPrepared(FIREBALL).withSlotSpent(1).rested();

        assertTrue(rested.hasPrepared(FIREBALL));
        assertEquals(0, rested.usedSlots(1));
    }

    @Test
    void theBookSurvivesTheFormatTheWorldSavesIn() {
        CharacterSheet original = wizard(5, 16).withPrepared(FIREBALL).withPrepared(MAGIC_MISSILE);

        var encoded = CharacterSheet.CODEC.encodeStart(NbtOps.INSTANCE, original)
                .getOrThrow(message -> new AssertionError("saving failed: " + message));
        CharacterSheet loaded = CharacterSheet.CODEC.parse(NbtOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError("loading failed: " + message));

        assertEquals(original, loaded);
        assertTrue(loaded.hasPrepared(FIREBALL));
        assertTrue(loaded.hasPrepared(MAGIC_MISSILE));
    }

    @Test
    void anEmptyBookRoundTripsToo() {
        var encoded = CharacterSheet.CODEC.encodeStart(JsonOps.INSTANCE, CharacterSheet.initial())
                .getOrThrow(message -> new AssertionError(message));

        assertEquals(CharacterSheet.initial(), CharacterSheet.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError(message)));
    }
}
