package com.ddc.command;

import com.ddc.gm.GameMasters;
import com.ddc.gm.WorldControlService;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /ddc world <change>}: PRD 3.2's world control, as a command until the panel exists.
 *
 * <p>Each change is its own literal rather than an enum argument, so the completions read as the list
 * of things a GM can do to the world: {@code day}, {@code night}, {@code storm}, {@code freeze}.
 */
public final class WorldCommand {

    private final WorldControlService world;

    public WorldCommand(WorldControlService world) {
        this.world = world;
    }

    /** The {@code world} branch, to hang under {@code /ddc}. */
    public ArgumentBuilder<CommandSourceStack, ?> branch() {
        var branch = Commands.literal("world").requires(GameMasters.requirement());
        for (WorldControlService.Change change : WorldControlService.Change.values()) {
            branch = branch.then(Commands.literal(nameOf(change))
                    .executes(context -> apply(context, change)));
        }
        return branch;
    }

    /** {@code PAUSE_TIME} reads as {@code pause-time} on the command line. */
    private static String nameOf(WorldControlService.Change change) {
        return change.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private int apply(CommandContext<CommandSourceStack> context, WorldControlService.Change change)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        return world.apply(player, change)
                .map(failure -> {
                    context.getSource().sendFailure(Component.literal(failure));
                    return 0;
                })
                .orElseGet(() -> {
                    context.getSource().sendSuccess(() -> Component.literal(change.narration())
                            .withStyle(ChatFormatting.GOLD), true);
                    return 1;
                });
    }
}
