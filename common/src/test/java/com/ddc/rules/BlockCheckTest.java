package com.ddc.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.core.character.Ability;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rule that makes a door ask for a roll, as a pack writes it.
 *
 * <p>The codec is the contract with pack authors, so the failures matter as much as the successes:
 * ADR-0002 promises a bad file reports itself rather than taking the reload down with it.
 */
class BlockCheckTest {

    @Test
    @DisplayName("a pack's file becomes a rule")
    void aFileParses() {
        BlockCheck check = parse("""
                {"ability": "strength", "dc": 15, "message": "ddc.check.block.door"}""");

        assertEquals(Ability.STRENGTH, check.ability());
        assertEquals(15, check.dc());
        assertEquals("ddc.check.block.door", check.message());
    }

    @Test
    @DisplayName("a file that says nothing about failing still gets a line to say")
    void theMessageHasADefault() {
        assertEquals("ddc.check.block.failed", parse("""
                {"ability": "dexterity", "dc": 13}""").message());
    }

    @Test
    @DisplayName("an unknown ability is an error, not a guess")
    void badAbilityIsReported() {
        var result = BlockCheck.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {"ability": "luck", "dc": 13}"""));

        assertTrue(result.error().isPresent());
        assertTrue(result.error().get().message().contains("luck"), result.error().get().message());
    }

    @Test
    @DisplayName("a DC outside the SRD's range is refused")
    void difficultyHasLimits() {
        assertTrue(BlockCheck.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"ability": "strength", "dc": 0}""")).error().isPresent());
        assertTrue(BlockCheck.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"ability": "strength", "dc": 31}""")).error().isPresent());
    }

    private static BlockCheck parse(String json) {
        return BlockCheck.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(message -> new AssertionError(message));
    }
}
