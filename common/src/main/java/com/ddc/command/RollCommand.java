package com.ddc.command;

import com.ddc.core.dice.DiceExpression;
import com.ddc.core.dice.RollMode;
import com.ddc.dice.DiceRollService;
import com.ddc.gm.GameMasters;
import com.mojang.brigadier.CommandDispatcher;
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
 * {@code /roll <expression> [advantage|disadvantage] [hidden]}.
 *
 * <p>Rolling is deliberately open to everyone; only the {@code hidden} branch is gated behind
 * {@link GameMasters}, because a hidden roll is a GM tool and a player who could hide their own rolls
 * could quietly reroll every failure.
 */
public final class RollCommand {

    private static final String ARG_EXPRESSION = "expression";

    private static final SimpleCommandExceptionType INVALID_EXPRESSION =
            new SimpleCommandExceptionType(Component.literal(
                    "Not a dice expression. Try something like 1d20+3, 2d6 or 8d6."));

    private final DiceRollService rolls;

    public RollCommand(DiceRollService rolls) {
        this.rolls = rolls;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // The expression is a single word, not a greedy string: a greedy string would swallow the
        // `advantage` and `hidden` literals as part of the notation, making those branches
        // unreachable and the GM gate on hidden rolls trivially bypassable.
        dispatcher.register(Commands.literal("roll")
                .then(Commands.argument(ARG_EXPRESSION, StringArgumentType.word())
                        .executes(context -> roll(context, RollMode.NORMAL, false))
                        .then(modeBranch("advantage", RollMode.ADVANTAGE))
                        .then(modeBranch("disadvantage", RollMode.DISADVANTAGE))
                        .then(hiddenBranch(RollMode.NORMAL))));
    }

    /** A mode branch, which itself may end in a hidden roll: {@code /roll 1d20 advantage hidden}. */
    private ArgumentBuilder<CommandSourceStack, ?> modeBranch(String name, RollMode mode) {
        return Commands.literal(name)
                .executes(context -> roll(context, mode, false))
                .then(hiddenBranch(mode));
    }

    private ArgumentBuilder<CommandSourceStack, ?> hiddenBranch(RollMode mode) {
        return Commands.literal("hidden")
                .requires(GameMasters.requirement())
                .executes(context -> roll(context, mode, true));
    }

    private int roll(CommandContext<CommandSourceStack> context, RollMode mode, boolean hidden)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        DiceExpression expression = parse(StringArgumentType.getString(context, ARG_EXPRESSION), mode);

        // Re-check rather than trusting the branch requirement alone: `requires` also controls
        // completions, and a command can be executed through a source that never saw them.
        boolean reallyHidden = hidden && GameMasters.isGameMaster(player);
        return (reallyHidden
                ? rolls.rollHidden(player, expression, mode)
                : rolls.rollPublic(player, expression, mode)).total();
    }

    /**
     * Turns player input into an expression, reporting both malformed notation and a mode the
     * expression cannot take (advantage on damage dice) as ordinary command errors.
     */
    private static DiceExpression parse(String raw, RollMode mode) throws CommandSyntaxException {
        DiceExpression expression;
        try {
            expression = DiceExpression.parse(raw);
        } catch (IllegalArgumentException e) {
            throw INVALID_EXPRESSION.create();
        }
        if (!mode.isNormal() && !expression.isSingleD20()) {
            throw new SimpleCommandExceptionType(Component.literal(
                    mode.name().toLowerCase(java.util.Locale.ROOT)
                            + " applies only to a single d20 roll, not to " + expression)).create();
        }
        return expression;
    }
}
