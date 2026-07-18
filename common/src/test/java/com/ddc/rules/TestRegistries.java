package com.ddc.rules;

import com.ddc.core.character.Ability;
import com.ddc.core.character.HitDie;
import com.ddc.core.character.LevelTable;
import com.ddc.core.dice.Die;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Registries with something in them, for tests that need a data pack without a game to load one.
 *
 * <p>{@link DataRegistry} fills itself from a resource reload, which a unit test has none of, so its
 * every test until now ran against an empty registry -- and an empty registry agrees with any bug that
 * only appears once there is an entry to describe. Reload is what these fake: nothing else about the
 * registry is replaced, so what the tests exercise is the real class.
 */
public final class TestRegistries {

    private TestRegistries() {
    }

    public static DataRegistry<CharacterClass> classes(Map<ResourceLocation, CharacterClass> entries) {
        return loaded(new DataRegistry<>("ddc_classes", "character classes", CharacterClass.CODEC), entries);
    }

    public static DataRegistry<Race> races(Map<ResourceLocation, Race> entries) {
        return loaded(new DataRegistry<>("ddc_races", "races", Race.CODEC), entries);
    }

    public static DataRegistry<Spell> spells(Map<ResourceLocation, Spell> entries) {
        return loaded(new DataRegistry<>("ddc_spells", "spells", Spell.CODEC), entries);
    }

    public static DataRegistry<Encounter> encounters(Map<ResourceLocation, Encounter> entries) {
        return loaded(new DataRegistry<>("ddc_encounters", "encounters", Encounter.CODEC), entries);
    }

    /**
     * Puts entries into a registry the way a reload would.
     *
     * <p>This class lives in the registry's own package for exactly this: {@code apply} is protected,
     * which is right -- a registry anything could write to would be a registry no data pack owned --
     * and a test in the package is the one caller that has business standing in for a reload.
     */
    private static <T> DataRegistry<T> loaded(DataRegistry<T> registry, Map<ResourceLocation, T> entries) {
        registry.apply(entries, null, null);
        return registry;
    }

    public static CharacterClass characterClass(String name) {
        return new CharacterClass(name, new HitDie(Die.D10), Ability.STRENGTH, Set.of(),
                Optional.empty(), List.of(), LevelTable.SRD);
    }

    public static Race race(String name) {
        return new Race(name, Map.of(), 30, List.of(), List.of());
    }

    public static Spell spell(String name, int level) {
        return new Spell(name, level, "evocation", 60, Optional.empty(), Optional.empty(),
                0, List.of(), 0);
    }

    public static Encounter encounter(String name) {
        return new Encounter(name, List.of(new Encounter.Member(ResourceLocation.parse("minecraft:zombie"), 2)));
    }
}
