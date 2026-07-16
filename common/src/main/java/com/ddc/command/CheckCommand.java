package com.ddc.command;

import com.ddc.character.CharacterService;
import com.ddc.character.CharacterSheet;
import com.ddc.core.character.Ability;
import com.ddc.core.check.CheckOutcome;
import com.ddc.core.check.D20Check;
import com.ddc.core.dice.RollMode;
import com.ddc.dice.DiceRollService;
import com.ddc.gm.GameMasters;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.ChatFormatting;
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
            key -> Component.literal("No such ability: '" + key + "'. Try strength, or DEX."));

    private final CharacterService characters;
    private final DiceRollService rolls;

    public CheckCommand(CharacterService characters, DiceRollService rolls) {
        this.characters = characters;
        this.rolls = rolls;
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

        CheckOutcome outcome = resolve(subject, ability, dc);
        announce(subject, ability, outcome);
        return outcome.isSuccess() ? 1 : 0;
    }

    /**
     * Rolls the check and shows the dice to everyone nearby.
     *
     * <p>The roll is made through the dice service so it lands in the same roll log as a {@code /roll},
     * because to the table it is the same thing: someone rolled a d20 and everyone saw it.
     */
    private CheckOutcome resolve(ServerPlayer subject, Ability ability, int dc) {
        CharacterSheet sheet = characters.get(subject);
        D20Check check = D20Check.ability(sheet.abilities(), ability, dc);
        var roll = rolls.rollPublic(subject, check.expression(), RollMode.NORMAL);
        return new CheckOutcome(roll, dc, degreeOf(roll, dc));
    }

    /** The same reading {@link D20Check} makes, against a roll that has already been thrown. */
    private static CheckOutcome.Degree degreeOf(com.ddc.core.dice.RollResult roll, int dc) {
        if (roll.isNatural20()) {
            return CheckOutcome.Degree.CRITICAL_SUCCESS;
        }
        if (roll.isNatural1()) {
            return CheckOutcome.Degree.CRITICAL_FAILURE;
        }
        return roll.total() >= dc ? CheckOutcome.Degree.SUCCESS : CheckOutcome.Degree.FAILURE;
    }

    private void announce(ServerPlayer subject, Ability ability, CheckOutcome outcome) {
        String verdict = switch (outcome.degree()) {
            case CRITICAL_SUCCESS -> "critical success";
            case SUCCESS -> "success";
            case FAILURE -> "failure";
            case CRITICAL_FAILURE -> "critical failure";
        };
        Component message = Component.literal(subject.getGameProfile().name() + "'s "
                        + ability.abbreviation() + " check vs DC " + outcome.difficultyClass()
                        + ": " + verdict)
                .withStyle(outcome.isSuccess() ? ChatFormatting.GREEN : ChatFormatting.RED);

        // Everyone who saw the dice should hear the verdict, so the audience is the roll's audience.
        for (ServerPlayer viewer : subject.level().players()) {
            if (viewer.distanceToSqr(subject) <= DiceRollService.BROADCAST_RADIUS
                    * DiceRollService.BROADCAST_RADIUS) {
                viewer.sendSystemMessage(message);
            }
        }
    }
}
