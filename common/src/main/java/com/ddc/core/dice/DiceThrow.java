package com.ddc.core.dice;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Where a thrown die is, and which way up, at any moment of its throw.
 *
 * <p>PRD 4.1 wants the die to tumble and settle on the number the roll produced, and ARCHITECTURE.md
 * wants every client to see the same tumble. Both fall out of the seed: the whole flight is a
 * function of {@code (seed, index, time)}, so nothing about it is sent, stored, or ticked. A client
 * that joins late and asks for the die's position at t=0.7s gets the same answer as everyone else.
 *
 * <p>The physics is deliberately not a rigid-body solver. A die needs to look like it fell and land
 * on a face the roll already decided; a solver would do neither, since its resting face is whatever
 * the maths produces rather than what was rolled. This is a ballistic arc with bounces, and an
 * orientation that spins fast and eases into the face that was rolled.
 */
public final class DiceThrow {

    /** How long a die is in the air, in seconds, before it is done. */
    public static final double FLIGHT_SECONDS = 1.6;

    /** How long the die then sits there being read. */
    public static final double LINGER_SECONDS = 2.4;

    /** Gravity, in blocks per second squared. Heavier than the world's, so the throw reads quickly. */
    private static final double GRAVITY = 13.0;

    /** How much of its speed a die keeps through a bounce. */
    private static final double BOUNCINESS = 0.42;

    /** Where the die starts, relative to the thrower: in front, at chest height. */
    private static final double START_HEIGHT = 1.35;

    /** How far apart several dice of one roll are thrown. */
    private static final double SPREAD = 0.28;

    private final double startX;
    private final double startZ;
    private final double velocityX;
    private final double velocityY;
    private final double velocityZ;
    private final double spinX;
    private final double spinY;
    private final double spinZ;
    private final double restingSpin;

    private DiceThrow(Random random, int index) {
        this.startX = (index % 3 - 1) * SPREAD;
        this.startZ = (index / 3 - 1) * SPREAD;
        this.velocityX = random.nextDouble() * 1.2 - 0.6;
        this.velocityY = 3.0 + random.nextDouble() * 1.4;
        this.velocityZ = 1.8 + random.nextDouble() * 1.2;
        this.spinX = 8 + random.nextDouble() * 14;
        this.spinY = 8 + random.nextDouble() * 14;
        this.spinZ = 8 + random.nextDouble() * 14;
        this.restingSpin = random.nextDouble() * Math.TAU;
    }

    /**
     * The flights of every die in a roll.
     *
     * <p>Built from the roll's own seed, so the tumble a client draws belongs to the roll the server
     * made. The generator is separate from the one that produced the faces: physics must never be
     * able to change what was rolled by taking a different number of samples.
     *
     * @return one throw per die, in the roll's own order
     */
    public static List<DiceThrow> forRoll(RollResult result) {
        Objects.requireNonNull(result, "result");
        Random random = new Random(result.seed() ^ 0x5DEECE66DL);
        return java.util.stream.IntStream.range(0, result.rolls().size())
                .mapToObj(index -> new DiceThrow(random, index))
                .toList();
    }

    /** How far through its life the die is, from 0 at the throw to 1 when it vanishes. */
    public static double progress(double seconds) {
        return Math.clamp(seconds / (FLIGHT_SECONDS + LINGER_SECONDS), 0.0, 1.0);
    }

    /** Whether the die is still worth drawing. */
    public static boolean isDone(double seconds) {
        return seconds >= FLIGHT_SECONDS + LINGER_SECONDS;
    }

    /** Sideways offset from the thrower, in blocks. */
    public double x(double seconds) {
        return startX + velocityX * flightTime(seconds);
    }

    /** Forward offset from the thrower, in blocks. */
    public double z(double seconds) {
        return startZ + velocityZ * flightTime(seconds);
    }

    /**
     * Height above the ground, in blocks.
     *
     * <p>The arc bounces: each time it would go under the floor it comes back up with less of its
     * speed, until it has too little to leave the ground at all.
     */
    public double y(double seconds) {
        double time = flightTime(seconds);
        double speed = velocityY;
        double elapsed = 0;
        // Walk the bounces rather than solving them: at most a handful, and this way the arc is
        // exactly what it looks like rather than an approximation of it.
        for (int bounce = 0; bounce < 8; bounce++) {
            double airborne = 2 * speed / GRAVITY;
            if (elapsed + airborne >= time || speed < 0.05) {
                double t = time - elapsed;
                double height = speed * t - 0.5 * GRAVITY * t * t;
                return Math.max(0, height + (bounce == 0 ? START_HEIGHT * (1 - t / airborne) : 0));
            }
            elapsed += airborne;
            speed *= BOUNCINESS;
        }
        return 0;
    }

    /**
     * How far the die has turned about each axis, in radians.
     *
     * <p>It spins fast while it flies and eases to a stop as it lands, so the last thing the eye does
     * is read a face rather than chase a blur. The easing is tied to when this die actually lands
     * rather than to the window: a die that has stopped moving but is still spinning on the floor
     * reads as broken, and each die lands at its own moment.
     */
    public double[] rotation(double seconds) {
        double time = Math.min(seconds, landingTime());
        double ease = tumbleEase(seconds);
        return new double[] {
                spinX * time * ease,
                spinY * time * ease + restingSpin,
                spinZ * time * ease,
        };
    }

    /**
     * How much of the spin is left: all of it at the throw, none once this die has landed.
     *
     * <p>Package-visible so the easing can be tested without a screen.
     */
    public double tumbleEase(double seconds) {
        double landing = landingTime();
        if (seconds >= landing) {
            return 0.0;
        }
        double remaining = 1 - seconds / landing;
        return remaining * remaining;
    }

    /**
     * When this die stops bouncing, in seconds.
     *
     * <p>The bounce series is finite -- each one keeps {@link #BOUNCINESS} of the last -- so this is
     * the sum of it rather than a guess, and it is always inside {@link #FLIGHT_SECONDS}.
     */
    public double landingTime() {
        double speed = velocityY;
        double elapsed = 0;
        for (int bounce = 0; bounce < 8 && speed >= 0.05; bounce++) {
            elapsed += 2 * speed / GRAVITY;
            speed *= BOUNCINESS;
        }
        return Math.min(elapsed, FLIGHT_SECONDS);
    }

    /** How opaque the die is: solid, then fading out over the last half second of its linger. */
    public static double alpha(double seconds) {
        double fadeStart = FLIGHT_SECONDS + LINGER_SECONDS - 0.5;
        if (seconds <= fadeStart) {
            return 1.0;
        }
        return Math.clamp((FLIGHT_SECONDS + LINGER_SECONDS - seconds) / 0.5, 0.0, 1.0);
    }

    private static double flightTime(double seconds) {
        return Math.min(seconds, FLIGHT_SECONDS);
    }
}
