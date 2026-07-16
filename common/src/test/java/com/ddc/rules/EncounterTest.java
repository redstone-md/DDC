package com.ddc.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.MinecraftBootstrapExtension;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EncounterTest {

    @BeforeAll
    static void setUp() {
        MinecraftBootstrapExtension.bootstrap();
    }

    private static DataResult<Encounter> parse(String json) {
        return Encounter.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static Encounter load(String file) throws IOException {
        String path = "/data/ddc/ddc_encounters/" + file + ".json";
        try (InputStream in = EncounterTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing built-in encounter: " + path);
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getOrThrow(message -> new AssertionError(path + ": " + message));
        }
    }

    @ParameterizedTest(name = "the built-in {0} loads and spawns {2}")
    @CsvSource({"zombie_patrol,Zombie Patrol,3", "skeleton_ambush,Skeleton Ambush,5", "cave_lurkers,Cave Lurkers,5"})
    void theBuiltInEncountersLoad(String file, String name, int total) throws IOException {
        Encounter encounter = load(file);

        assertEquals(name, encounter.name());
        assertEquals(total, encounter.total());
    }

    @Test
    void countDefaultsToOne() {
        Encounter single = parse("""
                {"name": "Lone Wolf", "members": [{"entity": "minecraft:wolf"}]}""")
                .getOrThrow(message -> new AssertionError(message));

        assertEquals(1, single.total());
    }

    @Test
    @DisplayName("one click cannot ask the server for a thousand mobs")
    void refusesAnEncounterBiggerThanTheCap() {
        DataResult<Encounter> result = parse("""
                {"name": "Apocalypse", "members": [{"entity": "minecraft:zombie", "count": 99}]}""");

        assertTrue(result.isError());
    }

    @Test
    void refusesTheCapEvenWhenSpreadAcrossMembers() {
        assertThrows(IllegalArgumentException.class, () -> new Encounter("Swarm", List.of(
                new Encounter.Member(Identifier.withDefaultNamespace("zombie"), 30),
                new Encounter.Member(Identifier.withDefaultNamespace("skeleton"), 30))));
    }

    @Test
    void refusesAnEmptyEncounter() {
        assertThrows(IllegalArgumentException.class, () -> new Encounter("Nothing", List.of()));
        assertTrue(parse("{\"name\": \"Nothing\", \"members\": []}").isError());
    }

    @Test
    @DisplayName("an unknown entity is left to the spawn, since another mod may add it later")
    void anUnknownEntityIdStillParses() {
        DataResult<Encounter> result = parse("""
                {"name": "Modded", "members": [{"entity": "some_mod:dragon_lord", "count": 1}]}""");

        assertTrue(result.result().isPresent(), "loading must not depend on what else is installed");
    }
}
