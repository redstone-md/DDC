package com.ddc.character;

import java.util.Optional;

/**
 * The manoeuvres a fighter can spend a superiority die on.
 *
 * <p>The SRD's list is long; these are the three PRD 3.1 names and the three that mean something in
 * a game with no turns. Each is code rather than data because each is behaviour -- what a trip does
 * to a target is not a number a pack could write.
 */
public enum Maneuver {

    /**
     * Tripping Attack: the SRD knocks the target prone. Minecraft has no prone, so the target is put
     * on the floor the way this game says it -- slowed hard, unable to get anywhere, briefly.
     */
    TRIP("trip"),

    /**
     * Parry: the SRD reduces the damage of a blow that already landed. Nothing here can reach back
     * into a blow that has happened, so this braces for the next one instead: resistance, for a
     * moment. Same bargain, spent forward rather than back.
     */
    PARRY("parry"),

    /** Pushing Attack: shoves the target away, which is one thing this game does exactly. */
    PUSH("push");

    private final String id;

    Maneuver(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** The manoeuvre a command named, if it named one. */
    public static Optional<Maneuver> byId(String id) {
        for (Maneuver maneuver : values()) {
            if (maneuver.id.equalsIgnoreCase(id)) {
                return Optional.of(maneuver);
            }
        }
        return Optional.empty();
    }
}
