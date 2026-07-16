package com.ddc.client.dice;

import com.ddc.core.dice.RollResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * The rolls this client has been told about, so the dice standing in the world can find their faces.
 *
 * <p>The dice entity carries only a seed; the faces came separately, in the payload that also fed the
 * roll log. Keeping one copy and looking it up here is what stops the dice and the log from ever
 * disagreeing about what was rolled.
 *
 * <p>Bounded and oldest-first: a session's rolls must not accumulate forever, and a roll older than
 * the last few is one whose dice are long gone.
 */
@Environment(EnvType.CLIENT)
public final class RollCache {

    /** Enough for every roll whose dice could still be on screen, several times over. */
    private static final int MAX_ROLLS = 32;

    private static final Map<Long, RollResult> BY_SEED = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, RollResult> eldest) {
            return size() > MAX_ROLLS;
        }
    };

    private RollCache() {
    }

    /** Remembers a roll the server sent. Called from the client thread. */
    public static synchronized void put(RollResult result) {
        BY_SEED.put(result.seed(), result);
    }

    /** The roll with this seed, if this client saw it. Empty when it was out of range at the time. */
    public static synchronized Optional<RollResult> get(long seed) {
        return Optional.ofNullable(BY_SEED.get(seed));
    }

    /** Forgets everything. Called when leaving a world, so one session cannot leak into the next. */
    public static synchronized void clear() {
        BY_SEED.clear();
    }
}
