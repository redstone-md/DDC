package com.ddc.core.dice;

/**
 * The polyhedral dice supported by the rules engine.
 *
 * <p>Restricting dice to a closed set keeps the 3D renderer honest: every value here has a model,
 * and a data pack cannot ask the client to render a d7.
 */
public enum Die {
    D4(4),
    D6(6),
    D8(8),
    D10(10),
    D12(12),
    D20(20),
    D100(100);

    private final int sides;

    Die(int sides) {
        this.sides = sides;
    }

    public int sides() {
        return sides;
    }

    /** Lowest face value, always 1. Present so callers never hardcode the assumption. */
    public int minValue() {
        return 1;
    }

    public int maxValue() {
        return sides;
    }

    /**
     * @throws IllegalArgumentException if no supported die has that many sides
     */
    public static Die ofSides(int sides) {
        for (Die die : values()) {
            if (die.sides == sides) {
                return die;
            }
        }
        throw new IllegalArgumentException("Unsupported die: d" + sides);
    }

    @Override
    public String toString() {
        return "d" + sides;
    }
}
