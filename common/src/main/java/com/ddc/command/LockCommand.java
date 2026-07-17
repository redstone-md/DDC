package com.ddc.command;

import com.ddc.core.character.Ability;
import com.ddc.gm.GameMasters;
import com.ddc.gm.GmLocks;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /ddc lock <ability> <dc> [pos]} and {@code /ddc unlock [pos]}: PRD 3.2's sealed door.
 *
 * <p>A data pack can say every iron door is hard to open. Only a Game Master can say that *this* door
 * is the one with the wizard's seal on it, and that is what a table actually needs -- so the lock is
 * on a place, not on a kind of block.
 *
 * <p>The position defaults to whatever the GM is looking at, because a GM standing in front of a door
 * should not have to read its coordinates off the debug screen to seal it.
 */
public final class LockCommand {

    private static final String ARG_ABILITY = "ability";
    private static final String ARG_DC = "dc";
    private static final String ARG_POS = "pos";

    /** The SRD's own range: 5 is very easy, 30 is nearly impossible. */
    private static final int MIN_DC = 1;
    private static final int MAX_DC = 30;

    /** How far a Game Master can reach to seal something they are looking at. */
    private static final double REACH = 32;

    private static final DynamicCommandExceptionType UNKNOWN_ABILITY = new DynamicCommandExceptionType(
            key -> Component.translatable("ddc.error.unknown_ability", key));

    /** The {@code lock} branch, to hang under {@code /ddc}. */
    public ArgumentBuilder<CommandSourceStack, ?> lockBranch() {
        return Commands.literal("lock")
                .requires(GameMasters.requirement())
                .then(Commands.argument(ARG_ABILITY, StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(Ability.values()).map(Ability::id), builder))
                        .then(Commands.argument(ARG_DC, IntegerArgumentType.integer(MIN_DC, MAX_DC))
                                .executes(context -> lock(context, lookedAt(context)))
                                .then(Commands.argument(ARG_POS, BlockPosArgument.blockPos())
                                        .executes(context -> lock(context,
                                                Optional.of(BlockPosArgument.getBlockPos(context, ARG_POS)))))));
    }

    /** The {@code unlock} branch. */
    public ArgumentBuilder<CommandSourceStack, ?> unlockBranch() {
        return Commands.literal("unlock")
                .requires(GameMasters.requirement())
                .executes(context -> unlock(context, lookedAt(context)))
                .then(Commands.argument(ARG_POS, BlockPosArgument.blockPos())
                        .executes(context -> unlock(context,
                                Optional.of(BlockPosArgument.getBlockPos(context, ARG_POS)))));
    }

    private int lock(CommandContext<CommandSourceStack> context, Optional<BlockPos> pos)
            throws CommandSyntaxException {
        ServerPlayer gameMaster = context.getSource().getPlayerOrException();
        String key = StringArgumentType.getString(context, ARG_ABILITY);
        Ability ability = Ability.byId(key).orElseThrow(() -> UNKNOWN_ABILITY.create(key));
        int dc = IntegerArgumentType.getInteger(context, ARG_DC);

        if (pos.isEmpty() || !(gameMaster.level() instanceof ServerLevel level)) {
            context.getSource().sendFailure(Component.translatable("ddc.error.no_block"));
            return 0;
        }
        GmLocks.of(level).lock(level, pos.get(), new GmLocks.Lock(ability, dc, Optional.empty()));
        context.getSource().sendSuccess(() -> Component.translatable("ddc.gm.locked",
                ability.abbreviation(), dc), false);
        return 1;
    }

    private int unlock(CommandContext<CommandSourceStack> context, Optional<BlockPos> pos)
            throws CommandSyntaxException {
        ServerPlayer gameMaster = context.getSource().getPlayerOrException();
        if (pos.isEmpty() || !(gameMaster.level() instanceof ServerLevel level)) {
            context.getSource().sendFailure(Component.translatable("ddc.error.no_block"));
            return 0;
        }
        if (!GmLocks.of(level).unlock(level, pos.get())) {
            context.getSource().sendFailure(Component.translatable("ddc.error.not_locked"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("ddc.gm.unlocked"), false);
        return 1;
    }

    /** The block the Game Master is looking at, if they are looking at one. */
    private static Optional<BlockPos> lookedAt(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        net.minecraft.world.phys.HitResult hit = player.pick(REACH, 0, false);
        if (hit instanceof net.minecraft.world.phys.BlockHitResult block
                && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return Optional.of(block.getBlockPos());
        }
        return Optional.empty();
    }
}
