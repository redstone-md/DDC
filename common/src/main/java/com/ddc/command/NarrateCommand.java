package com.ddc.command;

import com.ddc.gm.GameMasters;
import com.ddc.gm.NarrationService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /ddc narrate <text>}: the GM speaks, and the words land letterboxed on every screen.
 *
 * <p>Gated twice, on purpose. The {@code requires} keeps the branch out of an ordinary player's
 * command tree, and the handler checks again before sending, because ADR-0003 makes the server the
 * authority and a command tree is not a security boundary.
 */
public final class NarrateCommand {

    private static final String ARG_TEXT = "text";

    private static final SimpleCommandExceptionType NOT_A_GAME_MASTER =
            new SimpleCommandExceptionType(Component.literal("Only a Game Master can narrate."));

    private static final SimpleCommandExceptionType TOO_LONG = new SimpleCommandExceptionType(
            Component.literal("That narration is too long."));

    private final NarrationService narration;

    public NarrateCommand(NarrationService narration) {
        this.narration = narration;
    }

    /** The {@code narrate} branch, to hang under {@code /ddc}. */
    public ArgumentBuilder<CommandSourceStack, ?> branch() {
        return Commands.literal("narrate")
                .requires(GameMasters.requirement())
                .then(Commands.argument(ARG_TEXT, StringArgumentType.greedyString())
                        .executes(this::narrate));
    }

    private int narrate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!GameMasters.isGameMaster(player)) {
            throw NOT_A_GAME_MASTER.create();
        }
        String text = StringArgumentType.getString(context, ARG_TEXT);
        if (!narration.isWithinLimit(text)) {
            throw TOO_LONG.create();
        }
        int audience = narration.narrate(player, text);
        context.getSource().sendSuccess(
                () -> Component.literal("Narrated to " + audience + " player(s)."), true);
        return audience;
    }
}
