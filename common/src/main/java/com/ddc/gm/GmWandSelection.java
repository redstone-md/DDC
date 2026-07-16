package com.ddc.gm;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Which encounter each Game Master has their wand set to.
 *
 * <p>Deliberately not saved. A wand selection is what a GM is about to do, not part of their
 * character, and a session that outlives a restart is not worth a migration later. It also means a
 * data pack reload cannot leave a saved selection pointing at an encounter that no longer exists.
 *
 * <p>Selections are held by player id rather than on the item, so two GMs sharing a wand from a chest
 * each keep their own.
 */
final class GmWandSelection {

    private static final Map<UUID, Identifier> SELECTED = new ConcurrentHashMap<>();

    private GmWandSelection() {
    }

    /**
     * The encounter this player has selected, defaulting to the first available.
     *
     * <p>Falls back to the first when the selection is gone, which is what happens when a data pack
     * reload removes the encounter a GM had chosen.
     */
    static Identifier current(ServerPlayer player, List<Identifier> available) {
        Identifier selected = SELECTED.get(player.getUUID());
        if (selected == null || !available.contains(selected)) {
            selected = available.getFirst();
            SELECTED.put(player.getUUID(), selected);
        }
        return selected;
    }

    /** Steps to the next encounter and returns it, wrapping at the end. */
    static Identifier next(ServerPlayer player, List<Identifier> available) {
        int index = available.indexOf(current(player, available));
        Identifier next = available.get((index + 1) % available.size());
        SELECTED.put(player.getUUID(), next);
        return next;
    }

    /** Forgets a player's selection. Called when they leave, so the map cannot grow forever. */
    static void forget(UUID player) {
        SELECTED.remove(player);
    }
}
