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
    @CsvSource({"zombie_patrol,Zombie Patrol,4", "skeleton_ambush,Skeleton Ambush,5", "cave_lurkers,Cave Lurkers,5"})
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

    @Test
    @DisplayName("a pack can arm a mob, name it, and make it tougher")
    void aMemberCanBeDressed() throws IOException {
        Encounter patrol = load("zombie_patrol");

        Encounter.Member captain = patrol.members().stream()
                .filter(member -> member.name().isPresent())
                .findFirst()
                .orElseThrow(() -> new AssertionError("the patrol has a captain"));

        assertEquals("Patrol Captain", captain.name().orElseThrow());
        assertEquals(40.0, captain.health().orElseThrow());
        assertEquals(net.minecraft.resources.Identifier.parse("minecraft:iron_sword"),
                captain.equipment().get(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
    }

    @Test
    @DisplayName("a mob a pack says nothing about is an ordinary mob that stays put")
    void everythingIsOptional() {
        Encounter.Member plain = new Encounter.Member(
                net.minecraft.resources.Identifier.parse("minecraft:zombie"), 2);

        assertTrue(plain.equipment().isEmpty());
        assertTrue(plain.health().isEmpty());
        assertTrue(plain.name().isEmpty());
        assertTrue(plain.persistent(), "a placed encounter that despawned would delete a GM's ambush");
    }

    @Test
    @DisplayName("an unknown equipment slot is an error against the file, not a guess")
    void badSlotIsReported() {
        var result = Encounter.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("""
                        {"name": "Bad", "members": [{"entity": "minecraft:zombie",
                         "equipment": {"hat": "minecraft:iron_helmet"}}]}"""));

        assertTrue(result.error().isPresent());
        assertTrue(result.error().get().message().contains("hat"), result.error().get().message());
    }
}
