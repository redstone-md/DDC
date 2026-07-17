package com.ddc.client.stream;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * What each channel-point reward does: PRD 4.5's "chaos spawns", and whatever else a streamer wants.
 *
 * <p>A reward is mapped to a command the streamer configured, and the command is run as them. That is
 * the whole security model and it is deliberately boring: a viewer spending points can cause exactly
 * what the streamer could have typed, so a reward cannot reach past whatever the server already lets
 * that player do. A Game Master's chat can spawn an encounter; an ordinary player's chat cannot,
 * because the server refuses the command either way.
 *
 * <p>Matched on the reward's title, case-insensitively, because that is the only thing a streamer
 * sees when they make a reward on Twitch. Ids exist, but nobody knows their own reward ids.
 */
@Environment(EnvType.CLIENT)
public final class RewardActions {

    /** Kept in insertion order, so listing them back reads as the streamer wrote them. */
    private final Map<String, String> actions = new LinkedHashMap<>();

    /** Maps a reward title to the command it runs, replacing any mapping it had. */
    public void map(String reward, String command) {
        actions.put(key(reward), command);
    }

    /** Forgets a reward's command, and says whether there was one. */
    public boolean forget(String reward) {
        return actions.remove(key(reward)) != null;
    }

    /** The command a redemption should run, if the streamer mapped that reward to one. */
    public Optional<String> commandFor(TwitchRewards.Redemption redemption) {
        return Optional.ofNullable(actions.get(key(redemption.reward())));
    }

    /** Every mapping, for the command that lists them. */
    public Map<String, String> all() {
        return Map.copyOf(actions);
    }

    public boolean isEmpty() {
        return actions.isEmpty();
    }

    /** Titles are compared without case or edge whitespace: a streamer types them twice, by hand. */
    private static String key(String reward) {
        return reward.trim().toLowerCase(Locale.ROOT);
    }
}
