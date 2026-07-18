package com.ddc.character;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ddc.core.character.Ability;
import com.ddc.rules.Race;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Changing race, which used to pay twice.
 *
 * <p>Scores are stored with the race's bonuses already in them, so picking a race a second time added
 * a second helping. That is easy to do by accident: a player who cannot see their race anywhere --
 * which was every player, until the HUD started saying -- picks one again to be sure.
 */
class RaceChangeTest {

    private static final ResourceLocation DWARF_ID = ResourceLocation.parse("ddc:dwarf");
    private static final ResourceLocation ELF_ID = ResourceLocation.parse("ddc:elf");

    private static final Race DWARF = new Race("Dwarf", Map.of(Ability.CONSTITUTION, 2), 25, java.util.List.of(), java.util.List.of());
    private static final Race ELF = new Race("Elf", Map.of(Ability.DEXTERITY, 2), 30, java.util.List.of(), java.util.List.of());

    @Test
    @DisplayName("a race raises what it says it raises")
    void aRaceGivesItsBonus() {
        CharacterSheet sheet = CharacterSheet.initial().withRace(DWARF_ID, DWARF, Optional.empty());

        assertEquals(12, sheet.abilities().score(Ability.CONSTITUTION));
        assertEquals(Optional.of(DWARF_ID), sheet.race());
    }

    @Test
    @DisplayName("picking the same race twice does not pay twice")
    void pickingTwiceIsNotPaidTwice() {
        CharacterSheet once = CharacterSheet.initial().withRace(DWARF_ID, DWARF, Optional.empty());
        CharacterSheet twice = once.withRace(DWARF_ID, DWARF, Optional.of(DWARF));

        assertEquals(12, twice.abilities().score(Ability.CONSTITUTION), "not 14");
    }

    @Test
    @DisplayName("changing race hands the old race's bonus back")
    void changingRaceGivesTheOldBonusBack() {
        CharacterSheet dwarf = CharacterSheet.initial().withRace(DWARF_ID, DWARF, Optional.empty());
        CharacterSheet elf = dwarf.withRace(ELF_ID, ELF, Optional.of(DWARF));

        assertEquals(10, elf.abilities().score(Ability.CONSTITUTION), "the dwarf's gift is gone");
        assertEquals(12, elf.abilities().score(Ability.DEXTERITY), "the elf's is here");
        assertEquals(Optional.of(ELF_ID), elf.race());
    }

    @Test
    @DisplayName("everything else on the sheet survives a change of race")
    void changingRaceKeepsTheCharacter() {
        CharacterSheet before = CharacterSheet.initial()
                .withRace(DWARF_ID, DWARF, Optional.empty())
                .withExperienceGained(300, com.ddc.rules.TestRegistries.characterClass("Fighter"));
        CharacterSheet after = before.withRace(ELF_ID, ELF, Optional.of(DWARF));

        assertEquals(2, after.level());
        assertEquals(300, after.experience());
    }
}
