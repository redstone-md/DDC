package com.ddc.rules;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * What an addon can listen to: ARCHITECTURE 3's registry callbacks, which never existed.
 *
 * <p>ADR-0002 says an addon adds classes and spells with no code, and that is true and is the point.
 * But an addon that wants to <em>react</em> -- to give its paladin an advancement when a pack defines
 * one, to index every spell for its own book -- had nothing to hook, because the registry swapped its
 * map in silence.
 *
 * <p>Fired after each reload, with everything that loaded, rather than once per entry. A listener
 * usually wants the set: an addon told about entries one at a time has to work out for itself when
 * the last one arrived, and would rebuild its index once per file for nothing.
 */
public final class DDCRegistryEvents {

    /**
     * Anything DDC loaded from data packs has just been reloaded.
     *
     * <p>The map is what the registry now holds and is unmodifiable: a listener that could write into
     * it would be a data pack nobody could see.
     */
    public interface Reloaded<T> {
        void reloaded(DataRegistry<T> registry, Map<Identifier, T> entries);
    }

    public static final Event<Reloaded<CharacterClass>> CLASSES_RELOADED = create();
    public static final Event<Reloaded<Race>> RACES_RELOADED = create();
    public static final Event<Reloaded<Spell>> SPELLS_RELOADED = create();
    public static final Event<Reloaded<Encounter>> ENCOUNTERS_RELOADED = create();
    public static final Event<Reloaded<BlockCheck>> BLOCK_CHECKS_RELOADED = create();

    private DDCRegistryEvents() {
    }

    private static <T> Event<Reloaded<T>> create() {
        return EventFactory.createLoop();
    }

    /**
     * Announces a reload to whoever is listening.
     *
     * <p>The registry hands itself in, so this class needs no table of which event belongs to which
     * registry -- a table that would be one more thing to forget to update when a fifth registry
     * arrives.
     */
    @SuppressWarnings("unchecked")
    static <T> void announce(DataRegistry<T> registry, Map<Identifier, T> entries) {
        Event<Reloaded<T>> event = (Event<Reloaded<T>>) eventFor(registry);
        if (event != null) {
            event.invoker().reloaded(registry, entries);
        }
    }

    private static Event<? extends Reloaded<?>> eventFor(DataRegistry<?> registry) {
        if (registry == DDCRegistries.CLASSES) {
            return CLASSES_RELOADED;
        }
        if (registry == DDCRegistries.RACES) {
            return RACES_RELOADED;
        }
        if (registry == DDCRegistries.SPELLS) {
            return SPELLS_RELOADED;
        }
        if (registry == DDCRegistries.ENCOUNTERS) {
            return ENCOUNTERS_RELOADED;
        }
        if (registry == DDCRegistries.BLOCK_CHECKS) {
            return BLOCK_CHECKS_RELOADED;
        }
        // A registry an addon made for itself. It has no event here, and does not need one: it can
        // announce whatever it likes to its own listeners.
        return null;
    }
}
