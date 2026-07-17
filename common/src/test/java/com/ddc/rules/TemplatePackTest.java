package com.ddc.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.MinecraftBootstrapExtension;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The template pack shipped with every release: PRD 5's {@code ddc-srd-template.zip}.
 *
 * <p>It is the first DDC file most pack authors will ever read, so a version of it that no longer
 * parses would teach every one of them a schema that does not exist. These load it through the same
 * codecs the game does, which is the only way to know it still works.
 */
class TemplatePackTest {

    private static final Path TEMPLATE = Path.of("..", "assets", "template");

    @BeforeAll
    static void setUp() {
        MinecraftBootstrapExtension.bootstrap();
    }

    @Test
    @DisplayName("the template's class parses, with the levelling table it is showing off")
    void theClassParses() throws IOException {
        CharacterClass paladin = parse("data/my_campaign/ddc_classes/paladin.json", CharacterClass.CODEC);

        assertEquals("Paladin", paladin.name());
        assertEquals(200, paladin.leveling().experienceFor(2), "the template levels faster than the SRD");
        assertTrue(paladin.feature(ClassFeature.SecondWind.class).isPresent());
    }

    @Test
    @DisplayName("the template's race parses, with the kit it hands out")
    void theRaceParses() throws IOException {
        Race tiefling = parse("data/my_campaign/ddc_races/tiefling.json", Race.CODEC);

        assertEquals("Tiefling", tiefling.name());
        assertEquals(2, tiefling.items().size());
    }

    @Test
    @DisplayName("the template's spell parses, including the fields the docs used to lie about")
    void theSpellParses() throws IOException {
        Spell smite = parse("data/my_campaign/ddc_spells/searing_smite.json", Spell.CODEC);

        assertEquals(1, smite.castTime(), "a spell with a casting time is what makes the runes mean something");
        assertEquals(List.of("verbal"), smite.components());
        assertTrue(smite.savingThrow().isPresent());
    }

    @Test
    @DisplayName("the template's encounter parses, boss and all")
    void theEncounterParses() throws IOException {
        Encounter ambush = parse("data/my_campaign/ddc_encounters/goblin_ambush.json", Encounter.CODEC);

        assertEquals(4, ambush.total());
        assertTrue(ambush.members().stream().anyMatch(member -> member.name().isPresent()));
    }

    @Test
    @DisplayName("the template's locked chest parses")
    void theCheckParses() throws IOException {
        BlockCheck chest = parse("data/minecraft/ddc_checks/chest.json", BlockCheck.CODEC);

        assertEquals(12, chest.dc());
    }

    @Test
    @DisplayName("the template is a data pack Minecraft will load")
    void thePackHasItsMetadata() throws IOException {
        String meta = Files.readString(TEMPLATE.resolve("pack.mcmeta"));

        assertTrue(meta.contains("pack_format"), "without this the game ignores the folder entirely");
    }

    private static <T> T parse(String file, Codec<T> codec) throws IOException {
        String json = Files.readString(TEMPLATE.resolve(file));
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(message -> new AssertionError(file + ": " + message));
    }
}
