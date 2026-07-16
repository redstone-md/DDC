package com.ddc.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.core.character.Ability;
import com.ddc.core.character.AbilityScores;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Covers the two data pack directories added for races and spells, and the files DDC ships in them. */
class RaceAndSpellTest {

    private static <T> T load(Codec<T> codec, String path) throws IOException {
        try (InputStream in = RaceAndSpellTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing built-in file: " + path);
            return codec.parse(JsonOps.INSTANCE,
                            JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)))
                    .getOrThrow(message -> new AssertionError(path + ": " + message));
        }
    }

    private static <T> DataResult<T> parse(Codec<T> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    @ParameterizedTest(name = "the built-in {0} loads")
    @CsvSource({"dwarf,Dwarf,25", "elf,Elf,30", "human,Human,30", "halfling,Halfling,25"})
    void theBuiltInRacesLoad(String file, String name, int speed) throws IOException {
        Race race = load(Race.CODEC, "/data/ddc/ddc_races/" + file + ".json");

        assertEquals(name, race.name());
        assertEquals(speed, race.speed());
    }

    @Test
    @DisplayName("a dwarf's Constitution bonus reaches the character sheet")
    void raceBonusesApplyToScores() throws IOException {
        Race dwarf = load(Race.CODEC, "/data/ddc/ddc_races/dwarf.json");

        AbilityScores raised = dwarf.applyTo(AbilityScores.defaults().with(Ability.CONSTITUTION, 14));

        assertEquals(16, raised.score(Ability.CONSTITUTION));
        assertEquals(10, raised.score(Ability.STRENGTH), "a dwarf raises nothing else");
    }

    @Test
    void raceBonusesClampAtTheCapRatherThanRefusingToApply() {
        Race pushy = parse(Race.CODEC, """
                {"name": "Titan", "ability_bonuses": {"strength": 4}}""")
                .getOrThrow(message -> new AssertionError(message));

        AbilityScores raised = pushy.applyTo(AbilityScores.defaults().with(Ability.STRENGTH, 29));

        assertEquals(30, raised.score(Ability.STRENGTH));
    }

    @Test
    void aRaceNeedsOnlyAName() {
        Race plain = parse(Race.CODEC, "{\"name\": \"Human Variant\"}")
                .getOrThrow(message -> new AssertionError(message));

        assertEquals(30, plain.speed(), "speed defaults to the SRD's usual 30 feet");
        assertTrue(plain.traits().isEmpty());
        assertTrue(plain.abilityBonuses().isEmpty());
    }

    @Test
    void reportsAnUnknownAbilityInARaceByName() {
        DataResult<Race> result = parse(Race.CODEC, """
                {"name": "Gremlin", "ability_bonuses": {"luck": 2}}""");

        assertTrue(result.isError());
        assertTrue(result.error().orElseThrow().message().contains("luck"));
    }

    @ParameterizedTest(name = "the built-in {0} loads")
    @CsvSource({
            "fire_bolt,Fire Bolt,0,1d10",
            "magic_missile,Magic Missile,1,3d4+3",
            "burning_hands,Burning Hands,1,3d6",
            "fireball,Fireball,3,8d6",
            "sacred_flame,Sacred Flame,0,1d8",
    })
    void theBuiltInSpellsLoad(String file, String name, int level, String dice) throws IOException {
        Spell spell = load(Spell.CODEC, "/data/ddc/ddc_spells/" + file + ".json");

        assertEquals(name, spell.name());
        assertEquals(level, spell.level());
        assertEquals(dice, spell.damageDice().orElseThrow().toString());
    }

    @Test
    @DisplayName("fireball is a Dexterity save for half, and reaches across a battlefield")
    void fireballMatchesTheSrd() throws IOException {
        Spell fireball = load(Spell.CODEC, "/data/ddc/ddc_spells/fireball.json");

        assertEquals(Ability.DEXTERITY, fireball.savingThrow().orElseThrow().ability());
        assertEquals(Spell.SavingThrow.Effect.HALF_DAMAGE,
                fireball.savingThrow().orElseThrow().effectOnSuccess());
        assertEquals(150, fireball.range());
        assertEquals(30.0, fireball.rangeInBlocks(), "150 feet is 30 blocks");
    }

    @Test
    void aCantripCostsNoSlot() throws IOException {
        assertTrue(load(Spell.CODEC, "/data/ddc/ddc_spells/fire_bolt.json").isCantrip());
        assertTrue(!load(Spell.CODEC, "/data/ddc/ddc_spells/fireball.json").isCantrip());
    }

    @Test
    @DisplayName("sacred flame's save leaves the target untouched, not singed")
    void aSaveCanNegateEntirely() throws IOException {
        Spell sacredFlame = load(Spell.CODEC, "/data/ddc/ddc_spells/sacred_flame.json");

        assertEquals(Spell.SavingThrow.Effect.NONE, sacredFlame.savingThrow().orElseThrow().effectOnSuccess());
    }

    @Test
    void reportsBadDamageDiceByWhatWasWrongWithThem() {
        DataResult<Spell> result = parse(Spell.CODEC, """
                {"name": "Broken", "level": 1, "damage_dice": "8d7"}""");

        assertTrue(result.isError());
        assertTrue(result.error().orElseThrow().message().contains("d7"));
    }

    @Test
    void reportsAnUnknownSaveEffect() {
        DataResult<Spell> result = parse(Spell.CODEC, """
                {"name": "Odd", "level": 1,
                 "saving_throw": {"ability": "wisdom", "effect_on_success": "reflect"}}""");

        assertTrue(result.isError());
        assertTrue(result.error().orElseThrow().message().contains("reflect"));
    }
}
