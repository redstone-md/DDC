package com.ddc.command;

import com.ddc.character.FeatureService;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /ddc second-wind} and {@code /ddc channel-divinity}: the class features a player triggers.
 *
 * <p>Each is its own branch rather than {@code /ddc feature <name>}, so a fighter's completions offer
 * what a fighter can do and nothing else once the tree knows their class. Today both branches are
 * offered to everyone and the service refuses what is not theirs; the sheet is server state, and a
 * command tree is built before it is known.
 */
public final class FeatureCommand {

    private final FeatureService features;

    public FeatureCommand(FeatureService features) {
        this.features = features;
    }

    /** The fighter's branch. */
    public ArgumentBuilder<CommandSourceStack, ?> secondWindBranch() {
        return Commands.literal("second-wind").executes(context -> use(context, features::secondWind));
    }

    /** The cleric's branch. */
    public ArgumentBuilder<CommandSourceStack, ?> channelDivinityBranch() {
        return Commands.literal("channel-divinity")
                .executes(context -> use(context, features::channelDivinity));
    }

    private int use(CommandContext<CommandSourceStack> context,
            Function<ServerPlayer, FeatureService.Either<FeatureService.Failure, FeatureService.Used>> action)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        return switch (action.apply(player)) {
            case FeatureService.Either.Left<FeatureService.Failure, FeatureService.Used> left -> {
                context.getSource().sendFailure(Component.literal(left.value().message()));
                yield 0;
            }
            case FeatureService.Either.Right<FeatureService.Failure, FeatureService.Used> right -> {
                FeatureService.Used used = right.value();
                context.getSource().sendSuccess(
                        () -> Component.literal(used.name() + ": " + used.detail()), false);
                yield 1;
            }
        };
    }
}
