package com.ddc.core.character;

import com.ddc.core.dice.Die;
import java.util.Objects;

/**
 * The die a class rolls for hit points, and the hit point maths that follows from it.
 *
 * <p>The SRD builds a character's maximum hit points from three things: the class's hit die, the
 * Constitution modifier, and the level. All three come together here so that no caller has to
 * remember that first level is maximised while later levels take the die's average.
 */
public record HitDie(Die die) {

    public HitDie {
        Objects.requireNonNull(die, "die");
    }

    /**
     * Hit points gained at first level: the full die, plus Constitution.
     *
     * <p>Never below 1, because a Constitution penalty large enough to zero out a level must not
     * produce a character who is dead on creation.
     */
    public int firstLevelHitPoints(int constitutionModifier) {
        return Math.max(1, die.maxValue() + constitutionModifier);
    }

    /**
     * Hit points gained at each level after the first, using the SRD's fixed average rather than a
     * roll: {@code die/2 + 1}, plus Constitution. Taking the average keeps a party's durability
     * predictable for the GM, which matters more here than the drama of one unlucky level-up roll.
     */
    public int laterLevelHitPoints(int constitutionModifier) {
        return Math.max(1, die.sides() / 2 + 1 + constitutionModifier);
    }

    /** Maximum hit points for a character of this class at a level. */
    public int maxHitPoints(int level, int constitutionModifier) {
        Proficiency.validateLevel(level);
        return firstLevelHitPoints(constitutionModifier)
                + (level - 1) * laterLevelHitPoints(constitutionModifier);
    }
}
