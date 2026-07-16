package com.ddc.core.dice;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed dice notation expression such as {@code 1d20+3} or {@code 8d6}.
 *
 * <p>This is the single parser for dice notation in DDC. Chat commands, data pack spell
 * definitions, and network payloads all go through {@link #parse(String)} so that a malformed
 * {@code "damage_dice"} in an addon fails at load time with the same message a player sees in chat.
 *
 * @param pools    the dice to throw, in the order they were written
 * @param modifier the flat bonus or penalty summed after the dice
 */
public record DiceExpression(List<DicePool> pools, int modifier) {

    /**
     * Matches one term: either {@code [count]d<sides>} or a bare integer, with an optional
     * leading sign. Negative dice pools are rejected later; the sign is captured so that
     * {@code "1d20-1"} parses without the modifier swallowing the minus.
     */
    private static final Pattern TERM = Pattern.compile("([+-])?(?:(\\d*)[dD](\\d+)|(\\d+))");

    private static final int MAX_LENGTH = 64;

    public DiceExpression {
        Objects.requireNonNull(pools, "pools");
        if (pools.isEmpty()) {
            throw new IllegalArgumentException("A dice expression needs at least one dice pool");
        }
        pools = List.copyOf(pools);
    }

    public static DiceExpression of(DicePool pool, int modifier) {
        return new DiceExpression(List.of(pool), modifier);
    }

    public static DiceExpression of(int count, Die die, int modifier) {
        return of(DicePool.of(count, die), modifier);
    }

    /**
     * Parses standard dice notation: {@code 2d6}, {@code 1d20+5}, {@code 4d8-2}, {@code 1d6+1d4+3}.
     * Whitespace and case are insignificant, and an omitted count means one die.
     *
     * @throws IllegalArgumentException if the notation is malformed, empty, or uses an unsupported die
     */
    public static DiceExpression parse(String notation) {
        Objects.requireNonNull(notation, "notation");
        String normalized = notation.replaceAll("\\s+", "");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Empty dice expression");
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Dice expression exceeds " + MAX_LENGTH + " characters");
        }

        List<DicePool> pools = new ArrayList<>();
        int modifier = 0;
        Matcher matcher = TERM.matcher(normalized);
        int consumed = 0;

        while (matcher.find()) {
            if (matcher.start() != consumed) {
                throw new IllegalArgumentException(
                        "Malformed dice expression '" + notation + "' at index " + consumed);
            }
            consumed = matcher.end();

            boolean negative = "-".equals(matcher.group(1));
            String diceSides = matcher.group(3);
            if (diceSides == null) {
                int flat = parsePositiveInt(matcher.group(4), notation);
                modifier += negative ? -flat : flat;
                continue;
            }
            if (negative) {
                throw new IllegalArgumentException("Dice pools cannot be subtracted: " + notation);
            }
            String rawCount = matcher.group(2);
            int count = rawCount.isEmpty() ? 1 : parsePositiveInt(rawCount, notation);
            pools.add(new DicePool(count, Die.ofSides(parsePositiveInt(diceSides, notation))));
        }

        if (consumed != normalized.length()) {
            throw new IllegalArgumentException(
                    "Malformed dice expression '" + notation + "' at index " + consumed);
        }
        if (pools.isEmpty()) {
            throw new IllegalArgumentException("Dice expression '" + notation + "' contains no dice");
        }
        return new DiceExpression(pools, modifier);
    }

    private static int parsePositiveInt(String raw, String notation) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Number out of range in dice expression: " + notation, e);
        }
    }

    /** Total number of individual dice thrown, used to bound roll work and packet size. */
    public int diceCount() {
        return pools.stream().mapToInt(DicePool::count).sum();
    }

    public int minTotal() {
        return pools.stream().mapToInt(DicePool::minTotal).sum() + modifier;
    }

    public int maxTotal() {
        return pools.stream().mapToInt(DicePool::maxTotal).sum() + modifier;
    }

    /**
     * Whether advantage and disadvantage are meaningful here. In the SRD rules those only apply to a
     * single d20 test, never to damage dice.
     */
    public boolean isSingleD20() {
        return pools.size() == 1 && pools.getFirst().count() == 1 && pools.getFirst().die() == Die.D20;
    }

    /** Renders back to canonical notation, so a round trip through {@link #parse} is stable. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (DicePool pool : pools) {
            if (!sb.isEmpty()) {
                sb.append('+');
            }
            sb.append(pool);
        }
        if (modifier > 0) {
            sb.append('+').append(modifier);
        } else if (modifier < 0) {
            sb.append(modifier);
        }
        return sb.toString();
    }
}
