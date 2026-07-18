package com.ddc.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.MinecraftBootstrapExtension;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The integration packs shipped alongside the mod.
 *
 * <p>DDC's whole compatibility story is that another mod's mobs become encounters through data and
 * nothing else -- a file that names their entity ids, parsed by DDC's own codec, spawned only if that
 * mod is installed. This holds that promise for the packs DDC ships: every one of them must parse, so
 * a player who installs the mod it names finds working encounters rather than a broken pack.
 *
 * <p>The entity ids are deliberately <em>not</em> checked against a registry. That is the point: a
 * reference to {@code mutantmonsters:mutant_zombie} is valid whether or not Mutant Monsters is loaded,
 * because DDC resolves it at spawn and reports an unknown one rather than refusing the file.
 */
class IntegrationPackTest {

    private static final Path INTEGRATIONS = Path.of("..", "assets", "integrations");

    @BeforeAll
    static void setUp() {
        MinecraftBootstrapExtension.bootstrap();
    }

    @Test
    @DisplayName("every encounter in every shipped integration pack parses")
    void everyIntegrationEncounterParses() throws IOException {
        List<Path> encounters = integrationEncounters();
        assertFalse(encounters.isEmpty(), "there should be integration packs to check");

        for (Path file : encounters) {
            Encounter encounter = Encounter.CODEC.parse(JsonOps.INSTANCE,
                            JsonParser.parseString(Files.readString(file)))
                    .getOrThrow(message -> new AssertionError(file + ": " + message));
            assertFalse(encounter.members().isEmpty(), file + " has no members");
            assertTrue(encounter.total() > 0, file + " spawns nothing");
        }
    }

    @Test
    @DisplayName("the Mutant Monsters pack points at that mod's own namespace")
    void theMutantPackNamesItsMod() throws IOException {
        Path pack = INTEGRATIONS.resolve("mutantmonsters/data/ddc_mm/ddc_encounters");
        assertTrue(Files.isDirectory(pack), "the mutant monsters encounters are where the pack expects");

        boolean namesTheMod;
        try (Stream<Path> files = Files.list(pack)) {
            namesTheMod = files.anyMatch(file -> {
                try {
                    return Files.readString(file).contains("mutantmonsters:");
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
        assertTrue(namesTheMod, "an integration pack that never names the mod integrates with nothing");
    }

    @Test
    @DisplayName("the Cataclysm pack points at that mod's own namespace")
    void theCataclysmPackNamesItsMod() throws IOException {
        Path pack = INTEGRATIONS.resolve("cataclysm/data/ddc_cataclysm/ddc_encounters");
        assertTrue(Files.isDirectory(pack), "the cataclysm encounters are where the pack expects");

        boolean namesTheMod;
        try (Stream<Path> files = Files.list(pack)) {
            namesTheMod = files.anyMatch(file -> {
                try {
                    return Files.readString(file).contains("cataclysm:");
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
        assertTrue(namesTheMod, "an integration pack that never names the mod integrates with nothing");
    }

    @Test
    @DisplayName("every spell a pack ships parses, and its irons_spell names a real id")
    void everyIntegrationSpellParses() throws IOException {
        List<Path> spells = integrationFiles("/ddc_spells/");
        // Not every pack ships spells (the mob packs do not), so this only asserts on the ones present.
        for (Path file : spells) {
            Spell spell = Spell.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
                    .getOrThrow(message -> new AssertionError(file + ": " + message));
            // A spell in an integration pack exists to reach another mod's spell; one with no link is a
            // spell that does nothing the base mod does not, which is not what these packs are for.
            assertTrue(spell.ironsSpell().isPresent(), file + " ships in an integration pack but links no spell");
            assertTrue(spell.ironsSpell().orElseThrow().contains(":"),
                    file + ": irons_spell must be a namespaced id like monsterspellbooks:bone_dagger");
        }
    }

    @Test
    @DisplayName("the Monsters & Spellbooks pack points at that mod's own namespace")
    void theMonstersPackNamesItsMod() throws IOException {
        Path pack = INTEGRATIONS.resolve("monsterspellbooks/data/ddc_msb");
        assertTrue(Files.isDirectory(pack), "the Monsters & Spellbooks pack is where it expects");
        boolean namesTheMod;
        try (Stream<Path> tree = Files.walk(pack)) {
            namesTheMod = tree.filter(Files::isRegularFile).anyMatch(file -> {
                try {
                    return Files.readString(file).contains("monsterspellbooks:");
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
        assertTrue(namesTheMod, "an integration pack that never names the mod integrates with nothing");
    }

    @Test
    @DisplayName("every integration pack declares the 1.21.1 data pack format")
    void everyPackDeclaresTheFormat() throws IOException {
        try (Stream<Path> packs = Files.list(INTEGRATIONS)) {
            List<Path> metas = packs.filter(Files::isDirectory)
                    .map(dir -> dir.resolve("pack.mcmeta"))
                    .filter(Files::exists)
                    .toList();
            assertFalse(metas.isEmpty(), "an integration pack needs a pack.mcmeta to load at all");
            for (Path meta : metas) {
                int format = JsonParser.parseString(Files.readString(meta)).getAsJsonObject()
                        .getAsJsonObject("pack").get("pack_format").getAsInt();
                assertTrue(format == 48, meta + ": pack_format is " + format + ", not 48 (Minecraft 1.21.1)");
            }
        }
    }

    private static List<Path> integrationEncounters() throws IOException {
        return integrationFiles("/ddc_encounters/");
    }

    /** Every {@code .json} under any integration pack whose path passes through {@code segment}. */
    private static List<Path> integrationFiles(String segment) throws IOException {
        if (!Files.isDirectory(INTEGRATIONS)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(INTEGRATIONS)) {
            return walk.filter(path -> path.toString().replace('\\', '/').contains(segment))
                    .filter(path -> path.toString().endsWith(".json"))
                    .toList();
        }
    }
}
