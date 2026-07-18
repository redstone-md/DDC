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

    private static List<Path> integrationEncounters() throws IOException {
        if (!Files.isDirectory(INTEGRATIONS)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(INTEGRATIONS)) {
            return walk.filter(path -> path.toString().replace('\\', '/').contains("/ddc_encounters/"))
                    .filter(path -> path.toString().endsWith(".json"))
                    .toList();
        }
    }
}
