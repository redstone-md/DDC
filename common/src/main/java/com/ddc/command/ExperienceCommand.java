package com.ddc.command;

import com.ddc.character.ExperienceService;
import com.ddc.gm.GameMasters;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /ddc xp <amount> [players]}: the Game Master handing out experience.
 *
 * <p>Killing things earns it, but a table does not only kill things. Talking a guard captain around
 * is worth a level in any campaign worth running, and the GM is the only one who can say so -- which
 * is why this is theirs and no one else's.
 *
 * <p>Awards to a whole selector at once, because a session's experience is usually the party's rather
 * than one player's.
 */
public final class ExperienceCommand {

    private static final String ARG_AMOUNT = "amount";
    private static final String ARG_PLAYERS = "players";

    /** Enough to reach the SRD's last level in one go, for a GM who wants to. */
    private static final int MAX_AWARD = 355_000;

    private final ExperienceService experience;

    public ExperienceCommand(ExperienceService experience) {
        this.experience = experience;
    }

    /** The {@code xp} branch, to hang under {@code /ddc}. */
    public ArgumentBuilder<CommandSourceStack, ?> branch() {
        return Commands.literal("xp")
                .requires(GameMasters.requirement())
                .then(Commands.argument(ARG_AMOUNT, IntegerArgumentType.integer(1, MAX_AWARD))
                        .executes(context -> award(context,
                                java.util.List.of(context.getSource().getPlayerOrException())))
                        .then(Commands.argument(ARG_PLAYERS, EntityArgument.players())
                                .executes(context -> award(context,
                                        EntityArgument.getPlayers(context, ARG_PLAYERS)))));
    }

    private int award(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players)
            throws CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(context, ARG_AMOUNT);
        int awarded = 0;
        for (ServerPlayer player : players) {
            // A player with no class has no table to measure against, so they are skipped rather than
            // silently banked: the count tells the GM how many actually got it.
            if (experience.award(player, amount)) {
                awarded++;
            }
        }
        int count = awarded;
        context.getSource().sendSuccess(
                () -> Component.translatable("ddc.xp.awarded", amount, count), false);
        return count;
    }
}
