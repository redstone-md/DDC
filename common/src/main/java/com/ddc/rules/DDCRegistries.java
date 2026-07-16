package com.ddc.rules;

import com.ddc.DDC;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.server.packs.PackType;

/**
 * The rules the loaded data packs define.
 *
 * <p>Three directories, one shape: {@code ddc_classes}, {@code ddc_races} and {@code ddc_spells} are
 * all scanned across every namespace, so an addon adds any of them by dropping in JSON. All three
 * reload with {@code /reload}.
 */
public final class DDCRegistries {

    public static final DataRegistry<CharacterClass> CLASSES =
            new DataRegistry<>("ddc_classes", "character classes", CharacterClass.CODEC);

    public static final DataRegistry<Race> RACES =
            new DataRegistry<>("ddc_races", "races", Race.CODEC);

    public static final DataRegistry<Spell> SPELLS =
            new DataRegistry<>("ddc_spells", "spells", Spell.CODEC);

    private DDCRegistries() {
    }

    /** Hooks every registry up to data pack reloads. Called once from the shared bootstrap. */
    public static void register() {
        for (DataRegistry<?> registry : new DataRegistry<?>[] {CLASSES, RACES, SPELLS}) {
            ReloadListenerRegistry.register(PackType.SERVER_DATA, registry, DDC.id(registry.directory()));
        }
    }
}
