package com.ddc.gm;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;

/**
 * One Game Master, in one mob.
 *
 * <p>Holds what has to be given back when the possession ends: the mode the GM was playing in, and
 * whether the mob thought for itself before the GM took over. Losing either would leave a GM stuck in
 * spectator, or a boss standing still forever after being let go.
 *
 * @param gameMaster      who is driving
 * @param mob             what they are driving
 * @param previousMode    the game mode to give back
 * @param mobHadAi        whether the mob's own AI was running before this
 */
record Possession(UUID gameMaster, Mob mob, GameType previousMode, boolean mobHadAi) {

    Possession {
        Objects.requireNonNull(gameMaster, "gameMaster");
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(previousMode, "previousMode");
    }

    boolean isAlive() {
        return mob.isAlive();
    }
}
