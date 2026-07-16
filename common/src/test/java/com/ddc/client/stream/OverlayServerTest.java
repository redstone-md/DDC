package com.ddc.client.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.MinecraftBootstrapExtension;
import com.ddc.core.dice.DiceRoller;
import com.ddc.core.dice.RollMode;
import com.ddc.core.dice.RollResult;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The overlay is a promise to software DDC does not own, so these connect a real WebSocket client to
 * a real server and read what comes out, rather than trusting the JSON by inspection.
 */
class OverlayServerTest {

    private final OverlayServer server = new OverlayServer();

    @BeforeAll
    static void setUp() {
        MinecraftBootstrapExtension.bootstrap();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    /** A free port, so a developer's own OBS on 8082 cannot fail this. */
    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static RollResult natural20() {
        for (long seed = 0; seed < 10_000; seed++) {
            RollResult result = DiceRoller.replaying(seed).roll("1d20+4", RollMode.NORMAL);
            if (result.isNatural20()) {
                return result;
            }
        }
        throw new AssertionError("no seed under 10000 rolled a natural 20");
    }

    @Test
    void startsAndStops() throws IOException {
        assertFalse(server.isRunning());

        assertTrue(server.start(freePort()).started());
        assertTrue(server.isRunning());

        server.stop();
        assertFalse(server.isRunning());
    }

    @Test
    void refusesToStartTwice() throws IOException {
        server.start(freePort());

        assertThrows(IllegalStateException.class, () -> server.start(freePort()));
    }

    @Test
    @DisplayName("a port already in use is reported, not thrown at the player")
    void reportsAPortItCannotOpen() throws IOException {
        try (ServerSocket taken = new ServerSocket(0)) {
            OverlayServer.Result result = server.start(taken.getLocalPort());

            assertFalse(result.started());
            assertTrue(result.message().contains(String.valueOf(taken.getLocalPort())));
            assertFalse(server.isRunning());
        }
    }

    @Test
    @DisplayName("a widget connects and is sent the roll, exactly as ARCHITECTURE.md promises")
    void aWidgetReceivesARoll() throws Exception {
        int port = freePort();
        assertTrue(server.start(port).started());

        CompletableFuture<String> received = new CompletableFuture<>();
        CountDownLatch open = new CountDownLatch(1);
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/"), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(1);
                        open.countDown();
                    }

                    @Override
                    public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        received.complete(data.toString());
                        return null;
                    }
                })
                .get(10, TimeUnit.SECONDS);
        assertTrue(open.await(10, TimeUnit.SECONDS), "the widget never connected");

        server.broadcast(OverlayEvents.diceRoll("StreamerName", natural20()));

        JsonObject message = JsonParser.parseString(received.get(10, TimeUnit.SECONDS)).getAsJsonObject();
        JsonObject data = message.getAsJsonObject("data");

        assertEquals("dice_roll", message.get("event").getAsString());
        assertEquals("StreamerName", data.get("player").getAsString());
        assertEquals("d20", data.get("die_type").getAsString());
        assertEquals(20, data.get("natural_roll").getAsInt());
        assertEquals(4, data.get("modifier").getAsInt());
        assertEquals(24, data.get("total").getAsInt());
        assertTrue(data.get("is_critical_success").getAsBoolean());
        assertFalse(data.get("is_critical_failure").getAsBoolean());
        assertEquals(1, data.getAsJsonArray("dice").size());

        socket.abort();
    }

    @Test
    void broadcastingWithNoWidgetsIsHarmless() throws IOException {
        server.start(freePort());

        server.broadcast(OverlayEvents.diceRoll("Nobody", natural20()));
    }

    @Test
    void aSheetGoesOutInTheDocumentsShape() {
        JsonObject message = JsonParser
                .parseString(OverlayEvents.characterSheet("Streamer", "Wizard", 5, 17, 32))
                .getAsJsonObject();

        assertEquals("character_sheet", message.get("event").getAsString());
        assertEquals("Wizard", message.getAsJsonObject("data").get("class").getAsString());
        assertEquals(17, message.getAsJsonObject("data").get("hit_points").getAsInt());
        assertEquals(32, message.getAsJsonObject("data").get("max_hit_points").getAsInt());
    }

    @Test
    void advantageTellsTheWidgetWhichDieCounted() {
        RollResult advantage = DiceRoller.replaying(5L).roll("1d20+3", RollMode.ADVANTAGE);

        JsonObject data = JsonParser.parseString(OverlayEvents.diceRoll("Streamer", advantage))
                .getAsJsonObject().getAsJsonObject("data");

        assertEquals("advantage", data.get("mode").getAsString());
        assertEquals(2, data.getAsJsonArray("dice").size());
        long counted = data.getAsJsonArray("dice").asList().stream()
                .filter(die -> die.getAsJsonObject().get("counted").getAsBoolean())
                .count();
        assertEquals(1, counted, "advantage keeps one die and the widget must be able to say which");
    }
}
