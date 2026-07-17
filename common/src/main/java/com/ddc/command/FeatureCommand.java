package com.ddc.command;

import com.ddc.character.FeatureService;
import com.ddc.character.Maneuver;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.arguments.EntityArgument;
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

    private static final String ARG_MANEUVER = "maneuver";
    private static final String ARG_TARGET = "target";

    private static final com.mojang.brigadier.exceptions.DynamicCommandExceptionType UNKNOWN_MANEUVER =
            new com.mojang.brigadier.exceptions.DynamicCommandExceptionType(
                    key -> Component.translatable("ddc.error.unknown_maneuver", key));

    private final FeatureService features;

    public FeatureCommand(FeatureService features) {
        this.features = features;
    }

    /** The fighter's branch. */
    public ArgumentBuilder<CommandSourceStack, ?> secondWindBranch() {
        return Commands.literal("second-wind").executes(context -> use(context, features::secondWind));
    }

    /** The fighter's other branch: a burst of speed rather than a breath back. */
    public ArgumentBuilder<CommandSourceStack, ?> actionSurgeBranch() {
        return Commands.literal("action-surge").executes(context -> use(context, features::actionSurge));
    }

    /**
     * {@code /ddc maneuver <name> <target>}: spend a superiority die on someone.
     *
     * <p>Takes a target, because every manoeuvre is done to somebody. The wheel fills it in from
     * whatever the fighter is looking at, so nobody types a UUID.
     */
    public ArgumentBuilder<CommandSourceStack, ?> maneuverBranch() {
        return Commands.literal("maneuver")
                .then(Commands.argument(ARG_MANEUVER, StringArgumentType.word())
                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(Maneuver.values()).map(Maneuver::id), builder))
                        .then(Commands.argument(ARG_TARGET, EntityArgument.entity())
                                .executes(this::maneuver)));
    }

    private int maneuver(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String key = StringArgumentType.getString(context, ARG_MANEUVER);
        Maneuver maneuver = Maneuver.byId(key).orElseThrow(() -> UNKNOWN_MANEUVER.create(key));
        net.minecraft.world.entity.Entity target = EntityArgument.getEntity(context, ARG_TARGET);
        if (!(target instanceof net.minecraft.world.entity.LivingEntity living)) {
            context.getSource().sendFailure(Component.translatable("ddc.error.no_target"));
            return 0;
        }
        return use(context, subject -> features.maneuver(subject, maneuver, living));
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
                context.getSource().sendFailure(left.value().message());
                yield 0;
            }
            case FeatureService.Either.Right<FeatureService.Failure, FeatureService.Used> right -> {
                context.getSource().sendSuccess(() -> right.value().message(), false);
                yield 1;
            }
        };
    }
}
