package com.ddc.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.character.FeatureService;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The cleric's channel, which could only ever turn undead.
 *
 * <p>PRD 3.1 asks it to heal and to buff as well, and a cleric who could do neither was a healer with
 * no healing in the one hobby where that is the whole point of the class.
 */
class ClericChannelTest {

    @Test
    @DisplayName("a pack says what mending is worth")
    void healingIsDataDriven() {
        ClassFeature.ChannelDivinity channel = parse("""
                {"type": "ddc:channel_divinity", "radius": 6.0, "seconds": 30, "heal": "3d8+2"}""");

        assertEquals("3d8+2", channel.heal().toString());
        assertEquals(6.0, channel.radius());
        assertEquals(30, channel.seconds());
    }

    @Test
    @DisplayName("a pack that says nothing about healing still heals")
    void healingHasADefault() {
        assertEquals("2d8", parse("""
                {"type": "ddc:channel_divinity"}""").heal().toString());
    }

    @Test
    @DisplayName("the channel has three uses, and they are named")
    void everyUseIsNamed() {
        assertEquals(Optional.of(FeatureService.Divinity.TURN), FeatureService.Divinity.byId("turn"));
        assertEquals(Optional.of(FeatureService.Divinity.HEAL), FeatureService.Divinity.byId("heal"));
        assertEquals(Optional.of(FeatureService.Divinity.BLESS), FeatureService.Divinity.byId("BLESS"));
        assertTrue(FeatureService.Divinity.byId("smite").isEmpty(), "an unknown use is not a guess");
    }

    private static ClassFeature.ChannelDivinity parse(String json) {
        ClassFeature feature = ClassFeature.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(message -> new AssertionError(message));
        return (ClassFeature.ChannelDivinity) feature;
    }
}
