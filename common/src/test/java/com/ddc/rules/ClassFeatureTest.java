package com.ddc.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.core.dice.DiceExpression;
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

/** Covers the class features PRD 3.1 gives each class, and the files DDC ships them in. */
class ClassFeatureTest {

    private static CharacterClass builtIn(String file) throws IOException {
        String path = "/data/ddc/ddc_classes/" + file + ".json";
        try (InputStream in = ClassFeatureTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing built-in class: " + path);
            return CharacterClass.CODEC.parse(JsonOps.INSTANCE,
                            JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)))
                    .getOrThrow(message -> new AssertionError(path + ": " + message));
        }
    }

    @Test
    @DisplayName("the built-in rogue sneaks, the fighter catches its breath, the cleric channels")
    void theBuiltInClassesCarryTheirFeatures() throws IOException {
        assertTrue(builtIn("rogue").feature(ClassFeature.SneakAttack.class).isPresent());
        assertTrue(builtIn("fighter").feature(ClassFeature.SecondWind.class).isPresent());
        assertTrue(builtIn("cleric").feature(ClassFeature.ChannelDivinity.class).isPresent());
    }

    @Test
    void aClassDoesNotCarryAFeatureItWasNotGiven() throws IOException {
        assertTrue(builtIn("wizard").feature(ClassFeature.SneakAttack.class).isEmpty());
        assertTrue(builtIn("rogue").feature(ClassFeature.SecondWind.class).isEmpty());
        assertTrue(builtIn("rogue").feature(ClassFeature.CombatSuperiority.class).isEmpty(),
                "superiority dice are the fighter's, not everyone's");
        assertTrue(builtIn("wizard").feature(ClassFeature.ActionSurge.class).isEmpty());
    }

    @Test
    @DisplayName("the fighter's pack carries what a fighter can do")
    void theFighterHasItsFeatures() throws IOException {
        CharacterClass fighter = builtIn("fighter");

        assertTrue(fighter.feature(ClassFeature.SecondWind.class).isPresent());
        assertTrue(fighter.feature(ClassFeature.ActionSurge.class).isPresent());
        ClassFeature.CombatSuperiority superiority =
                fighter.feature(ClassFeature.CombatSuperiority.class).orElseThrow();
        assertEquals(4, superiority.uses(), "the SRD's fighter carries four superiority dice");
        assertEquals("1d8", superiority.dice().toString());
    }

    @ParameterizedTest(name = "a level {0} rogue sneak attacks for {1}")
    @CsvSource({"1,1d6", "2,1d6", "3,2d6", "4,2d6", "5,3d6", "11,6d6", "19,10d6", "20,10d6"})
    void reproducesTheSrdSneakAttackProgression(int level, String expected) throws IOException {
        ClassFeature.SneakAttack sneak = builtIn("rogue")
                .feature(ClassFeature.SneakAttack.class).orElseThrow();

        assertEquals(expected, sneak.diceAtLevel(level).toString());
    }

    @Test
    @DisplayName("a homebrew rogue can buy dice at its own pace")
    void theProgressionIsTheDataPacksToSet() {
        ClassFeature.SneakAttack fast = new ClassFeature.SneakAttack(DiceExpression.parse("1d8"), 1);

        assertEquals("1d8", fast.diceAtLevel(1).toString());
        assertEquals("5d8", fast.diceAtLevel(5).toString());
    }

    @Test
    void aFeatureWithAModifierScalesItToo() {
        ClassFeature.SneakAttack odd = new ClassFeature.SneakAttack(DiceExpression.parse("1d6+1"), 2);

        assertEquals("2d6+2", odd.diceAtLevel(3).toString());
    }

    @Test
    void secondWindCarriesItsDice() throws IOException {
        assertEquals("1d10", builtIn("fighter").feature(ClassFeature.SecondWind.class)
                .orElseThrow().dice().toString());
    }

    @Test
    @DisplayName("channel divinity reaches the SRD's 30 feet")
    void channelDivinityCarriesItsRadius() throws IOException {
        ClassFeature.ChannelDivinity channel = builtIn("cleric")
                .feature(ClassFeature.ChannelDivinity.class).orElseThrow();

        assertEquals(6.0, channel.radius(), "30 feet is 6 blocks");
        assertEquals(30, channel.seconds());
    }

    @Test
    void reportsAnUnknownFeatureTypeByName() {
        DataResult<CharacterClass> result = CharacterClass.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {"name": "Homebrew", "hit_die": "d8", "primary_ability": "charisma",
                         "features": [{"type": "ddc:time_travel"}]}"""));

        assertTrue(result.isError());
        assertTrue(result.error().orElseThrow().message().contains("time_travel"));
    }

    @Test
    void reportsBadDiceInAFeature() {
        DataResult<CharacterClass> result = CharacterClass.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {"name": "Homebrew", "hit_die": "d8", "primary_ability": "dexterity",
                         "features": [{"type": "ddc:sneak_attack", "dice": "1d7"}]}"""));

        assertTrue(result.isError());
        assertTrue(result.error().orElseThrow().message().contains("d7"));
    }

    @Test
    void featuresRoundTripBackToJson() throws IOException {
        CharacterClass rogue = builtIn("rogue");

        var encoded = CharacterClass.CODEC.encodeStart(JsonOps.INSTANCE, rogue)
                .getOrThrow(message -> new AssertionError(message));

        assertEquals(rogue, CharacterClass.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError(message)));
    }
}
