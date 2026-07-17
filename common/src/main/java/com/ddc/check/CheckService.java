package com.ddc.check;

import com.ddc.character.CharacterService;
import com.ddc.character.CharacterSheet;
import com.ddc.core.character.Ability;
import com.ddc.core.check.CheckOutcome;
import com.ddc.core.check.D20Check;
import com.ddc.core.dice.RollMode;
import com.ddc.core.dice.RollResult;
import com.ddc.dice.DiceRollService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Rolling an ability check, wherever the asking came from.
 *
 * <p>{@code /ddc check} asked for one, and now a locked door asks for one too. The rolling, the
 * reading and the telling are the same either way, so they are here rather than twice: a door that
 * announced its result differently from the command would look like a different rule.
 */
public final class CheckService {

    private final CharacterService characters;
    private final DiceRollService rolls;

    public CheckService(CharacterService characters, DiceRollService rolls) {
        this.characters = characters;
        this.rolls = rolls;
    }

    /**
     * Rolls a check and shows the dice to everyone nearby.
     *
     * <p>The roll goes through the dice service, so it lands in the same log as a {@code /roll}: to
     * the table it is the same thing, someone rolled a d20 and everyone saw it.
     */
    public CheckOutcome roll(ServerPlayer subject, Ability ability, int dc) {
        CharacterSheet sheet = characters.get(subject);
        D20Check check = D20Check.ability(sheet.abilities(), ability, dc);
        RollResult roll = rolls.rollPublic(subject, check.expression(), RollMode.NORMAL);
        return new CheckOutcome(roll, dc, degreeOf(roll, dc));
    }

    /** Rolls, and says out loud what happened. */
    public CheckOutcome rollAndAnnounce(ServerPlayer subject, Ability ability, int dc) {
        CheckOutcome outcome = roll(subject, ability, dc);
        announce(subject, ability, outcome);
        return outcome;
    }

    /** The same reading {@link D20Check} makes, against a roll that has already been thrown. */
    private static CheckOutcome.Degree degreeOf(RollResult roll, int dc) {
        if (roll.isNatural20()) {
            return CheckOutcome.Degree.CRITICAL_SUCCESS;
        }
        if (roll.isNatural1()) {
            return CheckOutcome.Degree.CRITICAL_FAILURE;
        }
        return roll.total() >= dc ? CheckOutcome.Degree.SUCCESS : CheckOutcome.Degree.FAILURE;
    }

    /** Tells everyone who saw the dice what they meant. */
    public void announce(ServerPlayer subject, Ability ability, CheckOutcome outcome) {
        Component verdict = Component.translatable(switch (outcome.degree()) {
            case CRITICAL_SUCCESS -> "ddc.check.critical_success";
            case SUCCESS -> "ddc.check.success";
            case FAILURE -> "ddc.check.failure";
            case CRITICAL_FAILURE -> "ddc.check.critical_failure";
        });
        Component message = Component.translatable("ddc.check.result", subject.getGameProfile().name(),
                        ability.abbreviation(), outcome.difficultyClass(), verdict)
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
