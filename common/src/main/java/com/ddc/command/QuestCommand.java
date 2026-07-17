package com.ddc.command;

import com.ddc.gm.GameMasters;
import com.ddc.network.QuestPayload;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.architectury.networking.NetworkManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /ddc quest <text>}: the line on the whiteboard.
 *
 * <p>Sent to everyone, because a quest nobody but the streamer can see is a note, not a quest. Cleared
 * with {@code /ddc quest clear}, since a party that finished a thing should stop being asked about it.
 */
public final class QuestCommand {

    private static final String ARG_TEXT = "text";

    /** The {@code quest} branch, to hang under {@code /ddc}. */
    public ArgumentBuilder<CommandSourceStack, ?> branch() {
        return Commands.literal("quest")
                .requires(GameMasters.requirement())
                .then(Commands.literal("clear").executes(context -> set(context, "")))
                .then(Commands.argument(ARG_TEXT, StringArgumentType.greedyString())
                        .executes(context -> set(context,
                                StringArgumentType.getString(context, ARG_TEXT))));
    }

    private int set(CommandContext<CommandSourceStack> context, String text)
            throws CommandSyntaxException {
        ServerPlayer gameMaster = context.getSource().getPlayerOrException();
        if (text.length() > QuestPayload.MAX_LENGTH) {
            context.getSource().sendFailure(Component.translatable("ddc.error.quest_too_long",
                    QuestPayload.MAX_LENGTH));
            return 0;
        }
        NetworkManager.sendToPlayers(
                gameMaster.level().getServer().getPlayerList().getPlayers(), new QuestPayload(text));
        context.getSource().sendSuccess(() -> text.isEmpty()
                ? Component.translatable("ddc.gm.quest_cleared")
                : Component.translatable("ddc.gm.quest_set", text), false);
        return 1;
    }
}
