package com.ddc.character;

import com.ddc.rules.Race;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What a race puts in a character's hands when they pick it.
 *
 * <p>An elf starts with a bow. That is not a rule DDC invents -- it is the pack's story about what an
 * elf is, so the list lives in the race's own file and a campaign with a different idea of elves
 * writes a different list.
 *
 * <p>Given once, when the race is chosen, and not re-given on join: a starting kit that arrived every
 * morning would be a duplication machine rather than a character.
 */
public final class RaceItems {

    private RaceItems() {
    }

    /**
     * Hands over the race's items, dropping what will not fit.
     *
     * <p>A full inventory is not a reason to lose your bow, so anything that does not fit lands at
     * the player's feet the way vanilla does it.
     */
    public static void give(ServerPlayer player, Race race) {
        for (net.minecraft.resources.ResourceLocation id : race.items()) {
            BuiltInRegistries.ITEM.getOptional(id).ifPresentOrElse(
                    item -> give(player, item),
                    () -> warn(player, id));
        }
    }

    private static void give(ServerPlayer player, Item item) {
        ItemStack stack = new ItemStack(item);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * Says so when a pack asks for an item that is not installed.
     *
     * <p>Told to the player rather than only the log: a pack written for a modpack they do not have
     * should say why their elf came empty-handed, not simply fail to arm them.
     */
    private static void warn(ServerPlayer player, net.minecraft.resources.ResourceLocation id) {
        player.sendSystemMessage(Component.translatable("ddc.race.missing_item", id.toString()));
    }
}
