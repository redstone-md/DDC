package com.ddc.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.MinecraftBootstrapExtension;
import com.ddc.core.dice.DiceExpression;
import com.ddc.core.dice.DiceRoller;
import com.ddc.core.dice.Die;
import com.ddc.core.dice.DieRoll;
import com.ddc.core.dice.RollMode;
import com.ddc.core.dice.RollResult;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Rolls go over the wire as numbers the server decided, so these tests push a payload through a real
 * buffer and check that what comes out is what went in, and that a malformed one is refused.
 */
class DiceResultPayloadTest {

    private static final UUID ROLLER = UUID.fromString("00000000-0000-0000-0000-0000000000d2");

    @BeforeAll
    static void setUp() {
        MinecraftBootstrapExtension.bootstrap();
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static DiceResultPayload roundTrip(DiceResultPayload payload) {
        RegistryFriendlyByteBuf buf = buffer();
        DiceResultPayload.STREAM_CODEC.encode(buf, payload);
        return DiceResultPayload.STREAM_CODEC.decode(buf);
    }

    private static DiceResultPayload payloadFor(String notation, RollMode mode) {
        RollResult result = DiceRoller.replaying(4242L).roll(DiceExpression.parse(notation), mode);
        return DiceResultPayload.of(ROLLER, "Tester", result);
    }

    @Test
    @DisplayName("a roll survives the wire exactly, faces and all")
    void roundTripsARoll() {
        DiceResultPayload sent = payloadFor("1d20+3", RollMode.NORMAL);

        DiceResultPayload received = roundTrip(sent);

        assertEquals(sent, received);
        assertEquals(sent.result().total(), received.result().total());
        assertEquals(sent.result().describe(), received.result().describe());
    }

    @Test
    void roundTripsManyDice() {
        DiceResultPayload received = roundTrip(payloadFor("8d6", RollMode.NORMAL));

        assertEquals(8, received.result().rolls().size());
        assertEquals(Die.D6, received.result().rolls().getFirst().die());
    }

    @Test
    void roundTripsMixedPoolsAndANegativeModifier() {
        DiceResultPayload received = roundTrip(payloadFor("1d6+1d4-2", RollMode.NORMAL));

        assertEquals("1d6+1d4-2", received.result().expression().toString());
        assertEquals(-2, received.result().modifier());
    }

    @Test
    @DisplayName("a discarded advantage die keeps its flag across the wire")
    void roundTripsAdvantage() {
        DiceResultPayload received = roundTrip(payloadFor("1d20+3", RollMode.ADVANTAGE));

        assertEquals(RollMode.ADVANTAGE, received.result().mode());
        assertEquals(2, received.result().rolls().size());
        assertEquals(1, received.result().keptRolls().size());
        assertTrue(received.result().rolls().stream().anyMatch(DieRoll::discarded));
    }

    @Test
    @DisplayName("the seed travels, for the dice physics rather than for the number")
    void roundTripsTheSeed() {
        assertEquals(4242L, roundTrip(payloadFor("1d20", RollMode.NORMAL)).result().seed());
    }

    /**
     * The client is told the faces rather than recomputing them, so a peer sending a result that its
     * own expression cannot account for must be refused rather than displayed.
     */
    @Test
    void refusesAResultThatDoesNotMatchItsExpression() {
        assertThrows(IllegalArgumentException.class, () -> new RollResult(
                DiceExpression.parse("2d6"),
                List.of(DieRoll.kept(Die.D6, 4)),
                RollMode.NORMAL,
                1L));
    }

    @Test
    void refusesAFaceTheDieDoesNotHave() {
        assertThrows(IllegalArgumentException.class, () -> DieRoll.kept(Die.D6, 7));
    }

    @Test
    void refusesAnUnsupportedDieFromTheWire() {
        RegistryFriendlyByteBuf buf = buffer();
        buf.writeVarInt(7); // a d7: no such die

        assertThrows(IllegalArgumentException.class, () -> DiceCodecs.DIE.decode(buf));
    }

    @Test
    void refusesAnUnknownRollMode() {
        RegistryFriendlyByteBuf buf = buffer();
        buf.writeVarInt(99);

        assertThrows(IllegalArgumentException.class, () -> DiceCodecs.ROLL_MODE.decode(buf));
    }
}
