package com.ddc.spell;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Which spell each caster's wand is pointed at.
 *
 * <p>The same shape as the Game Master's wand selection, and for the same reasons: held by player
 * rather than on the item, so two wizards sharing a wand from a chest each keep their own, and not
 * saved, because what a wizard is about to cast is not part of who their character is.
 *
 * <p>A selection that no longer exists -- the pack that defined the spell is gone, or the wizard
 * un-prepared it -- falls back to the first thing they can cast rather than failing. A wand that
 * stopped working because of a reload would look broken rather than reloaded.
 */
public final class SpellSelection {

    private static final Map<UUID, ResourceLocation> SELECTED = new ConcurrentHashMap<>();

    private SpellSelection() {
    }

    /** The spell this caster's focus is pointed at, defaulting to the first they can cast. */
    public static ResourceLocation current(ServerPlayer caster, List<ResourceLocation> castable) {
        ResourceLocation selected = SELECTED.get(caster.getUUID());
        if (selected == null || !castable.contains(selected)) {
            selected = castable.getFirst();
            SELECTED.put(caster.getUUID(), selected);
        }
        return selected;
    }

    /** Steps to the next spell and returns it, wrapping at the end. */
    public static ResourceLocation next(ServerPlayer caster, List<ResourceLocation> castable) {
        int index = castable.indexOf(current(caster, castable));
        ResourceLocation next = castable.get((index + 1) % castable.size());
        SELECTED.put(caster.getUUID(), next);
        return next;
    }

    /** Forgets a caster's choice. Called when they leave, so the map cannot grow forever. */
    public static void forget(UUID caster) {
        SELECTED.remove(caster);
    }
}
