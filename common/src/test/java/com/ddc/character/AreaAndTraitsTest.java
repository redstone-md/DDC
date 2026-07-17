package com.ddc.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.MinecraftBootstrapExtension;
import com.ddc.rules.Race;
import com.ddc.rules.Spell;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A fireball that catches more than one thing, and races whose traits are not decoration.
 *
 * <p>Both were true on paper and false in the world: the codec parsed an area nothing set, and a
 * dwarf's 25 feet were printed in a chat message and then forgotten. These hold the data as much as
 * the code, because the data is where both of them went wrong.
 */
class AreaAndTraitsTest {

    @BeforeAll
    static void setUp() {
        MinecraftBootstrapExtension.bootstrap();
    }

    @Test
    @DisplayName("the fireball that ships actually splashes")
    void fireballHasItsArea() throws IOException {
        Spell fireball = builtInSpell("fireball");

        assertTrue(fireball.isAreaOfEffect(), "a fireball that hits one zombie is not a fireball");
        assertEquals(20, fireball.areaOfEffect());
        assertEquals(4.0, fireball.areaInBlocks(), "twenty feet is four blocks");
    }

    @Test
    @DisplayName("a spell that names one target keeps naming one")
    void mostSpellsAreSingleTarget() throws IOException {
        assertFalse(builtInSpell("fire_bolt").isAreaOfEffect());
        assertFalse(builtInSpell("magic_missile").isAreaOfEffect());
    }

    @Test
    @DisplayName("the documentation's own schema parses")
    void theDocumentedShapeWorks() {
        // ARCHITECTURE prints an object; the codec used to take only a number, so a pack copying the
        // documentation failed to load. Both are read.
        Spell object = parse("""
                {"name": "Fireball", "level": 3,
                 "area_of_effect": {"type": "sphere", "radius": 20}}""");
        Spell number = parse("""
                {"name": "Fireball", "level": 3, "area_of_effect": 20}""");

        assertEquals(20, object.areaOfEffect());
        assertEquals(number.areaOfEffect(), object.areaOfEffect());
    }

    @Test
    @DisplayName("darkvision is read however a pack writes it")
    void darkvisionIsRecognised() {
        assertTrue(RaceTraits.hasDarkvision(new Race("Dwarf", java.util.Map.of(), 25,
                java.util.List.of("darkvision", "stonecunning"), java.util.List.of())));
        assertTrue(RaceTraits.hasDarkvision(new Race("Elf", java.util.Map.of(), 30,
                java.util.List.of("Superior Darkvision"), java.util.List.of())));
        assertFalse(RaceTraits.hasDarkvision(new Race("Human", java.util.Map.of(), 30,
                java.util.List.of("versatile"), java.util.List.of())));
    }

    @Test
    @DisplayName("the races that ship have the traits they claim")
    void theBuiltInRacesAreWhatTheySay() throws IOException {
        Race dwarf = builtInRace("dwarf");
        Race elf = builtInRace("elf");

        assertEquals(25, dwarf.speed(), "a dwarf is slower, and now feels it");
        assertTrue(RaceTraits.hasDarkvision(dwarf));
        assertTrue(RaceTraits.hasDarkvision(elf));
        assertEquals(30, builtInRace("human").speed());
        assertFalse(RaceTraits.hasDarkvision(builtInRace("human")));
    }

    private static Spell builtInSpell(String name) throws IOException {
        return parse(read("/data/ddc/ddc_spells/" + name + ".json"));
    }

    private static Race builtInRace(String name) throws IOException {
        return Race.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString(read("/data/ddc/ddc_races/" + name + ".json")))
                .getOrThrow(message -> new AssertionError(message));
    }

    private static Spell parse(String json) {
        return Spell.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(message -> new AssertionError(message));
    }

    private static String read(String resource) throws IOException {
        try (InputStream stream = AreaAndTraitsTest.class.getResourceAsStream(resource)) {
            return new String(java.util.Objects.requireNonNull(stream, resource).readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }
}
