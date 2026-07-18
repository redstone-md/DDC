package com.ddc.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.rules.CharacterClass;
import com.ddc.rules.DataRegistry;
import com.ddc.rules.Encounter;
import com.ddc.rules.Race;
import com.ddc.rules.Spell;
import com.ddc.rules.TestRegistries;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the client is told it may pick from.
 *
 * <p>These exist because {@link RulesPayload#of} shipped building every entry with a null id, which
 * {@link RulesPayload.Entry} refuses -- so joining a world threw before the player was placed in it,
 * and the mod was unplayable. Nothing caught it: the tests had registries, but empty ones, and an
 * empty registry describes nothing. Every case here puts something in the registry first.
 */
class RulesPayloadTest {

    private static final ResourceLocation FIGHTER = ResourceLocation.parse("ddc:fighter");
    private static final ResourceLocation WIZARD = ResourceLocation.parse("ddc:wizard");

    @Test
    @DisplayName("every entry carries the id its command takes")
    void entriesKeepTheirIds() {
        DataRegistry<CharacterClass> classes = TestRegistries.classes(Map.of(
                FIGHTER, TestRegistries.characterClass("Fighter"),
                WIZARD, TestRegistries.characterClass("Wizard")));

        RulesPayload payload = RulesPayload.of(classes, TestRegistries.races(Map.of()),
                TestRegistries.spells(Map.of()), TestRegistries.encounters(Map.of()), false);

        assertEquals(List.of(FIGHTER, WIZARD), payload.classes().stream().map(RulesPayload.Entry::id).toList());
        assertEquals(List.of("Fighter", "Wizard"), payload.classes().stream().map(RulesPayload.Entry::name).toList());
    }

    @Test
    @DisplayName("entries are sorted, so a menu does not shuffle between joins")
    void entriesAreSorted() {
        DataRegistry<CharacterClass> classes = TestRegistries.classes(Map.of(
                WIZARD, TestRegistries.characterClass("Wizard"),
                FIGHTER, TestRegistries.characterClass("Fighter")));

        RulesPayload payload = RulesPayload.of(classes, TestRegistries.races(Map.of()),
                TestRegistries.spells(Map.of()), TestRegistries.encounters(Map.of()), false);

        assertEquals(List.of(FIGHTER, WIZARD), payload.classes().stream().map(RulesPayload.Entry::id).toList());
    }

    @Test
    @DisplayName("a player is not told the encounters")
    void encountersAreForGameMastersOnly() {
        DataRegistry<Encounter> encounters = TestRegistries.encounters(Map.of(
                ResourceLocation.parse("ddc:patrol"), TestRegistries.encounter("Patrol")));

        RulesPayload player = RulesPayload.of(TestRegistries.classes(Map.of()),
                TestRegistries.races(Map.of()), TestRegistries.spells(Map.of()), encounters, false);
        RulesPayload master = RulesPayload.of(TestRegistries.classes(Map.of()),
                TestRegistries.races(Map.of()), TestRegistries.spells(Map.of()), encounters, true);

        assertTrue(player.encounters().isEmpty());
        assertTrue(player.gameMaster() == false);
        assertEquals(1, master.encounters().size());
        assertTrue(master.gameMaster());
    }

    @Test
    @DisplayName("a spell keeps its level, because the wheel shows it")
    void spellsKeepTheirLevel() {
        ResourceLocation fireBolt = ResourceLocation.parse("ddc:fire_bolt");
        DataRegistry<Spell> spells = TestRegistries.spells(Map.of(fireBolt, TestRegistries.spell("Fire Bolt", 0)));

        RulesPayload payload = RulesPayload.of(TestRegistries.classes(Map.of()),
                TestRegistries.races(Map.of()), spells, TestRegistries.encounters(Map.of()), false);

        assertEquals(0, payload.spells().getFirst().level());
        assertEquals(fireBolt, payload.spells().getFirst().id());
    }

    @Test
    @DisplayName("a race the pack named is offered under that name")
    void racesAreDescribed() {
        ResourceLocation elf = ResourceLocation.parse("ddc:elf");
        DataRegistry<Race> races = TestRegistries.races(Map.of(elf, TestRegistries.race("Elf")));

        RulesPayload payload = RulesPayload.of(TestRegistries.classes(Map.of()), races,
                TestRegistries.spells(Map.of()), TestRegistries.encounters(Map.of()), false);

        assertEquals(elf, payload.races().getFirst().id());
        assertEquals("Elf", payload.races().getFirst().name());
    }
}
