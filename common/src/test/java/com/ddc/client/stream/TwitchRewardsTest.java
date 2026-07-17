package com.ddc.client.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Channel points: reading what Twitch says, and deciding what it buys.
 *
 * <p>Nothing here talks to Twitch. What can be tested without an account is the part that is easy to
 * get wrong -- which message carries the session, which carries a redemption, what the subscription
 * asks for, and what a reward is allowed to do -- and that is what these hold.
 */
class TwitchRewardsTest {

    private static final String WELCOME = """
            {"metadata": {"message_type": "session_welcome"},
             "payload": {"session": {"id": "AgoQm-Ii", "status": "connected"}}}""";

    private static final String REDEEMED = """
            {"metadata": {"message_type": "notification"},
             "payload": {"event": {"user_name": "SomeViewer", "user_id": "1234",
                                   "reward": {"id": "abc", "title": "Chaos Spawn", "cost": 500}}}}""";

    private static final String KEEPALIVE = """
            {"metadata": {"message_type": "session_keepalive"}, "payload": {}}""";

    @Test
    @DisplayName("the welcome carries the session a subscription needs")
    void theWelcomeCarriesTheSession() {
        assertEquals(Optional.of("AgoQm-Ii"), TwitchRewards.sessionIn(WELCOME));
        assertTrue(TwitchRewards.sessionIn(REDEEMED).isEmpty());
    }

    @Test
    @DisplayName("a notification carries who redeemed what")
    void aNotificationCarriesTheRedemption() {
        TwitchRewards.Redemption redemption = TwitchRewards.redemptionIn(REDEEMED).orElseThrow();

        assertEquals("SomeViewer", redemption.viewer());
        assertEquals("Chaos Spawn", redemption.reward());
    }

    @Test
    @DisplayName("a keepalive is not a redemption, and is not an error either")
    void keepalivesAreIgnored() {
        // Twitch sends these every ten seconds, and will send message types this code has never heard
        // of. Neither is a fault.
        assertTrue(TwitchRewards.redemptionIn(KEEPALIVE).isEmpty());
        assertTrue(TwitchRewards.sessionIn(KEEPALIVE).isEmpty());
    }

    @Test
    @DisplayName("the subscription asks for redemptions on the streamer's own channel, and nothing else")
    void theSubscriptionIsWhatTwitchWants() {
        var body = JsonParser.parseString(TwitchRewards.subscriptionBody("55555", "AgoQm-Ii"))
                .getAsJsonObject();

        assertEquals(TwitchRewards.REDEMPTION, body.get("type").getAsString());
        assertEquals("1", body.get("version").getAsString());
        assertEquals("55555",
                body.getAsJsonObject("condition").get("broadcaster_user_id").getAsString());
        assertEquals("websocket", body.getAsJsonObject("transport").get("method").getAsString());
        assertEquals("AgoQm-Ii", body.getAsJsonObject("transport").get("session_id").getAsString());
    }

    @Test
    @DisplayName("a reward runs the command the streamer mapped to it")
    void aRewardRunsItsCommand() {
        RewardActions actions = new RewardActions();
        actions.map("Chaos Spawn", "ddc spawn ddc:zombie_patrol");

        assertEquals(Optional.of("ddc spawn ddc:zombie_patrol"),
                actions.commandFor(new TwitchRewards.Redemption("SomeViewer", "Chaos Spawn")));
    }

    @Test
    @DisplayName("a title is matched the way a streamer types it, twice, by hand")
    void titlesAreMatchedLoosely() {
        RewardActions actions = new RewardActions();
        actions.map("Chaos Spawn", "ddc spawn ddc:zombie_patrol");

        assertTrue(actions.commandFor(new TwitchRewards.Redemption("V", "chaos spawn")).isPresent());
        assertTrue(actions.commandFor(new TwitchRewards.Redemption("V", "  CHAOS SPAWN ")).isPresent());
    }

    @Test
    @DisplayName("a reward nobody mapped does nothing at all")
    void anUnmappedRewardDoesNothing() {
        RewardActions actions = new RewardActions();
        actions.map("Chaos Spawn", "ddc spawn ddc:zombie_patrol");

        // The point: a viewer cannot invent an action by naming a reward. Only the streamer's own
        // mapping decides, and an unmapped one is silence.
        assertTrue(actions.commandFor(new TwitchRewards.Redemption("V", "Delete The World")).isEmpty());
    }

    @Test
    @DisplayName("mapping a reward twice replaces it rather than stacking")
    void mappingReplaces() {
        RewardActions actions = new RewardActions();
        actions.map("Chaos Spawn", "ddc spawn ddc:zombie_patrol");
        actions.map("Chaos Spawn", "ddc spawn ddc:cave_lurkers");

        assertEquals(Optional.of("ddc spawn ddc:cave_lurkers"),
                actions.commandFor(new TwitchRewards.Redemption("V", "Chaos Spawn")));
        assertEquals(1, actions.all().size());
    }

    @Test
    void forgettingARewardSaysWhetherThereWasOne() {
        RewardActions actions = new RewardActions();
        actions.map("Chaos Spawn", "ddc spawn ddc:zombie_patrol");

        assertTrue(actions.forget("chaos spawn"));
        assertFalse(actions.forget("chaos spawn"));
        assertTrue(actions.isEmpty());
    }

    @Test
    @DisplayName("no credentials in the environment means no connection is attempted")
    void withoutCredentialsNothingConnects() {
        // The test JVM has none set, which is the case that matters: the mod must not dial out, and
        // must say why rather than failing quietly.
        Optional<String> failure = new TwitchRewards(redemption -> { }).start();

        assertEquals(Optional.of("ddc.stream.rewards_unconfigured"), failure);
        assertFalse(TwitchRewards.isConfigured());
    }
}
