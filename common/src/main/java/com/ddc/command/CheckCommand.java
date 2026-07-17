package com.ddc.command;

import com.ddc.check.CheckService;
import com.ddc.core.character.Ability;
import com.ddc.gm.GameMasters;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /ddc check <ability> <dc>}: the ability check PRD 3.1 asks for when a player picks a lock,
 * jumps a gap, or shoulders a door.
 *
 * <p>The check is what a table does out loud, so the result is public: the roll goes to everyone
 * nearby, and the pass or fail is said plainly. This is the opposite of an attack roll, which is
 * hidden, and the difference is deliberate -- a hidden lockpick tells the player nothing.
 *
 * <p>A Game Master can call for one from someone else with {@code /ddc check <ability> <dc> <player>},
 * which is the GM prompting a roll rather than asking a player to remember to make it.
 */
public final class CheckCommand {

    private static final String ARG_ABILITY = "ability";
    private static final String ARG_DC = "dc";
    private static final String ARG_PLAYER = "player";

    /** The SRD's own range: 5 is very easy, 30 is nearly impossible. */
    private static final int MIN_DC = 1;
    private static final int MAX_DC = 30;

    private static final DynamicCommandExceptionType UNKNOWN_ABILITY = new DynamicCommandExceptionType(
            key -> Component.translatable("ddc.error.unknown_ability", key));

    private final CheckService checks;

    public CheckCommand(CheckService checks) {
        this.checks = checks;
    }

    /** The {@code check} branch, to hang under {@code /ddc}. */
    public ArgumentBuilder<CommandSourceStack, ?> branch() {
        return Commands.literal("check")
                .then(Commands.argument(ARG_ABILITY, StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(Ability.values()).map(Ability::id), builder))
                        .then(Commands.argument(ARG_DC, IntegerArgumentType.integer(MIN_DC, MAX_DC))
                                .executes(context -> check(context, context.getSource().getPlayerOrException()))
                                // Only a GM may call for someone else's roll: a player who could would
                                // be rolling other people's dice.
                                .then(Commands.argument(ARG_PLAYER, EntityArgument.player())
                                        .requires(GameMasters.requirement())
                                        .executes(context -> check(context,
                                                EntityArgument.getPlayer(context, ARG_PLAYER))))));
    }

    private int check(CommandContext<CommandSourceStack> context, ServerPlayer subject)
            throws CommandSyntaxException {
        String key = StringArgumentType.getString(context, ARG_ABILITY);
        Ability ability = Ability.byId(key).orElseThrow(() -> UNKNOWN_ABILITY.create(key));
        int dc = IntegerArgumentType.getInteger(context, ARG_DC);

        return checks.rollAndAnnounce(subject, ability, dc).isSuccess() ? 1 : 0;
    }
}
