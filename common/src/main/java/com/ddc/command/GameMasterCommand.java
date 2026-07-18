package com.ddc.command;

import com.ddc.gm.GameMasters;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * {@code /ddc gm}: PRD 3.2's Game Master, who is invisible, flies, and passes through blocks.
 *
 * <p>All three of those are one word in Minecraft -- spectator -- and the mod never said it. A Game
 * Master logged in as an ordinary survival player and had to know to change their own game mode
 * before they could run anything, which is a thing you only know if you already knew.
 *
 * <p>It is a toggle rather than a state, and the previous mode is remembered, because a GM who plays
 * a character between scenes should get their body back exactly as they left it -- the same bargain
 * possession strikes, for the same reason.
 *
 * <p>Being a Game Master is still the server's permission and nothing to do with this: this changes
 * what a GM can walk through, not what they are allowed to do. A player without the permission gets
 * neither.
 */
public final class GameMasterCommand {

    /** What each GM was before they went spectating. */
    private final Map<UUID, GameType> previous = new HashMap<>();

    /** The {@code gm} branch, to hang under {@code /ddc}. */
    public ArgumentBuilder<CommandSourceStack, ?> branch() {
        return Commands.literal("gm")
                .requires(GameMasters.requirement())
                .executes(this::toggle);
    }

    private int toggle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (previous.containsKey(player.getUUID())) {
            return leave(context, player);
        }
        return enter(context, player);
    }

    private int enter(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        previous.put(player.getUUID(), player.gameMode.getGameModeForPlayer());
        player.setGameMode(GameType.SPECTATOR);
        context.getSource().sendSuccess(() -> Component.translatable("ddc.gm.mode_on")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private int leave(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        // Back to whatever they were, not to survival: a GM who was in creative building the dungeon
        // an hour ago should not be dropped into it with no blocks.
        player.setGameMode(previous.remove(player.getUUID()));
        context.getSource().sendSuccess(() -> Component.translatable("ddc.gm.mode_off")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /** Whether this player is currently running the world rather than living in it. */
    public boolean isSpectating(ServerPlayer player) {
        return previous.containsKey(player.getUUID());
    }
}
