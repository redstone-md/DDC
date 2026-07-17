package com.ddc.client.screen;

import com.ddc.client.ClientRules;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * What a Game Master can do to the block they just clicked: PRD 3.2's context menu.
 *
 * <p>The wand used to place an encounter the instant it touched a block, which is one of the four
 * things the PRD says that click should offer and the only one it did. Placing is still a click away
 * -- it is the first thing on the menu -- but sealing a door, unsealing it, and walling a corridor off
 * are now where a GM would look for them: on the block, at the moment they are pointing at it.
 *
 * <p>Every option is a command carrying the block's own coordinates, so the server checks the same
 * permission it would check had the GM typed it. The wheel is a faster way to type, here as anywhere.
 */
@Environment(EnvType.CLIENT)
public final class BlockWheel {

    private BlockWheel() {
    }

    /** The menu for a block, or nothing when this player has no encounters and no business here. */
    public static WheelScreen forBlock(BlockPos pos) {
        return new WheelScreen(Component.translatable("ddc.wheel.block"), options(pos));
    }

    private static List<WheelOption> options(BlockPos pos) {
        String at = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        List<WheelOption> options = new ArrayList<>();

        // The encounters first: it is what the wand was for before it had a menu, and a GM who liked
        // the old behaviour should find it in the same place their hand already goes.
        ClientRules.encounters().forEach(entry -> options.add(new WheelOption(
                Component.literal(entry.name()),
                Component.translatable("ddc.wheel.here"),
                "ddc spawn " + entry.id(), Icon.ENCOUNTER)));

        options.add(new WheelOption(Component.translatable("ddc.wheel.lock"),
                Component.translatable("ddc.wheel.lock.detail"),
                "ddc lock dexterity 15 " + at, Icon.CHECK));
        options.add(new WheelOption(Component.translatable("ddc.wheel.lock_hard"),
                Component.translatable("ddc.wheel.lock_hard.detail"),
                "ddc lock strength 20 " + at, Icon.CHECK));
        options.add(new WheelOption(Component.translatable("ddc.wheel.unlock"),
                Component.translatable("ddc.wheel.unlock.detail"),
                "ddc unlock " + at, Icon.CHECK));
        options.add(new WheelOption(Component.translatable("ddc.wheel.gm"),
                Component.translatable("ddc.wheel.gm.detail"), PlayerWheel.Wheels.GM_PANEL, Icon.GM));
        return options;
    }
}
