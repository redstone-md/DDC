package com.ddc.client.stream;

import com.ddc.DDC;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * PRD 4.5's channel points: a viewer spends theirs, and something happens in the world.
 *
 * <p>Chat votes need no account, which is why they came first. Channel points cannot work that way:
 * only the broadcaster can be told about a redemption, and Twitch will only tell someone holding that
 * broadcaster's own token. There is no anonymous road to this one.
 *
 * <p><strong>The token is never asked for in chat and never written down.</strong> It is read from the
 * environment -- {@code DDC_TWITCH_CLIENT_ID} and {@code DDC_TWITCH_TOKEN} -- for two reasons. A
 * command typed into a chat box puts an account's credentials in a screenshot, a log file, and
 * whatever the streamer was recording at the time; and a mod that saved a token would be a mod
 * promising to keep one safe, which is a promise it has no business making. Nothing here outlives the
 * session it was started in.
 *
 * <p>What a redemption does is a command the streamer configured -- so it can do exactly what they
 * could type, and no more. A viewer cannot spend points to make the mod do something the streamer
 * could not.
 */
@Environment(EnvType.CLIENT)
public final class TwitchRewards implements AutoCloseable {

    /** Twitch's EventSub socket. Where the redemptions arrive. */
    static final URI SOCKET = URI.create("wss://eventsub.wss.twitch.tv/ws");

    /** Where a subscription is asked for, and where the broadcaster's own id is looked up. */
    static final String SUBSCRIPTIONS = "https://api.twitch.tv/helix/eventsub/subscriptions";
    static final String USERS = "https://api.twitch.tv/helix/users";

    /** The one event this asks for: a viewer spent points on a reward. */
    static final String REDEMPTION = "channel.channel_points_custom_reward_redemption.add";

    /** What Twitch sends when it is ready to be subscribed to. */
    static final String WELCOME = "session_welcome";
    static final String NOTIFICATION = "notification";

    /** Where the credentials come from. Never a command, never a file. */
    public static final String CLIENT_ID_VARIABLE = "DDC_TWITCH_CLIENT_ID";
    public static final String TOKEN_VARIABLE = "DDC_TWITCH_TOKEN";

    /** A viewer's redemption, in the two facts anything here needs. */
    public record Redemption(String viewer, String reward) {
    }

    private final HttpClient http;
    private final Consumer<Redemption> onRedeemed;
    private volatile WebSocket socket;

    public TwitchRewards(Consumer<Redemption> onRedeemed) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), onRedeemed);
    }

    /** For tests, which have no Twitch to talk to and no business trying. */
    TwitchRewards(HttpClient http, Consumer<Redemption> onRedeemed) {
        this.http = http;
        this.onRedeemed = onRedeemed;
    }

    /** Whether the streamer has put credentials in this session's environment. */
    public static boolean isConfigured() {
        return credentials().isPresent();
    }

    /** The client id and token, if both are there. Neither is any use without the other. */
    static Optional<String[]> credentials() {
        String clientId = System.getenv(CLIENT_ID_VARIABLE);
        String token = System.getenv(TOKEN_VARIABLE);
        if (clientId == null || clientId.isBlank() || token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new String[] {clientId, token});
    }

    /**
     * Connects and starts listening for redemptions.
     *
     * @return empty when it started, or the reason it did not
     */
    public Optional<String> start() {
        Optional<String[]> credentials = credentials();
        if (credentials.isEmpty()) {
            return Optional.of("ddc.stream.rewards_unconfigured");
        }
        if (socket != null) {
            return Optional.of("ddc.stream.rewards_running");
        }
        try {
            String clientId = credentials.get()[0];
            String token = credentials.get()[1];
            String broadcaster = broadcasterId(clientId, token);

            socket = http.newWebSocketBuilder()
                    .buildAsync(SOCKET, new Listener(clientId, token, broadcaster))
                    .join();
            return Optional.empty();
        } catch (Exception e) {
            // The token is not in the message: an error line ends up in logs and on streams, and a
            // token that leaked because a request failed would be a worse failure than the request.
            DDC.LOGGER.warn("Twitch rewards could not start: {}", e.getMessage());
            return Optional.of("ddc.stream.rewards_failed");
        }
    }

    /** Who the token belongs to. Twitch only tells a broadcaster about their own redemptions. */
    private String broadcasterId(String clientId, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(USERS))
                .header("Client-Id", clientId)
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Twitch refused the token: HTTP " + response.statusCode());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject()
                .getAsJsonArray("data").get(0).getAsJsonObject()
                .get("id").getAsString();
    }

    /** Asks Twitch to send this session's socket every redemption on the streamer's own channel. */
    private void subscribe(String clientId, String token, String broadcaster, String session)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(SUBSCRIPTIONS))
                .header("Client-Id", clientId)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        subscriptionBody(broadcaster, session)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Twitch refused the subscription: HTTP " + response.statusCode());
        }
    }

    /** The subscription, as Twitch's API wants it. Package-visible so its shape can be tested. */
    static String subscriptionBody(String broadcaster, String session) {
        JsonObject condition = new JsonObject();
        condition.addProperty("broadcaster_user_id", broadcaster);

        JsonObject transport = new JsonObject();
        transport.addProperty("method", "websocket");
        transport.addProperty("session_id", session);

        JsonObject body = new JsonObject();
        body.addProperty("type", REDEMPTION);
        body.addProperty("version", "1");
        body.add("condition", condition);
        body.add("transport", transport);
        return body.toString();
    }

    /**
     * Reads one of Twitch's messages.
     *
     * <p>Package-visible and free of the socket, because this is the part that has to be right and the
     * part a test can hold: a welcome carries the session to subscribe with, a notification carries a
     * redemption, and everything else -- keepalives, revocations, reconnect notices -- is not ours.
     */
    static Optional<String> sessionIn(String message) {
        JsonObject json = JsonParser.parseString(message).getAsJsonObject();
        if (!WELCOME.equals(json.getAsJsonObject("metadata").get("message_type").getAsString())) {
            return Optional.empty();
        }
        return Optional.of(json.getAsJsonObject("payload").getAsJsonObject("session")
                .get("id").getAsString());
    }

    /** The redemption in a message, if it is one. */
    static Optional<Redemption> redemptionIn(String message) {
        JsonObject json = JsonParser.parseString(message).getAsJsonObject();
        if (!NOTIFICATION.equals(json.getAsJsonObject("metadata").get("message_type").getAsString())) {
            return Optional.empty();
        }
        JsonObject event = json.getAsJsonObject("payload").getAsJsonObject("event");
        return Optional.of(new Redemption(
                event.get("user_name").getAsString(),
                event.getAsJsonObject("reward").get("title").getAsString()));
    }

    @Override
    public void close() {
        WebSocket open = socket;
        socket = null;
        if (open != null) {
            open.sendClose(WebSocket.NORMAL_CLOSURE, "closing").join();
        }
    }

    public boolean isRunning() {
        return socket != null;
    }

    /** Turns Twitch's socket into redemptions, and nothing else. */
    private final class Listener implements WebSocket.Listener {

        private final String clientId;
        private final String token;
        private final String broadcaster;
        private final StringBuilder buffer = new StringBuilder();

        private Listener(String clientId, String token, String broadcaster) {
            this.clientId = clientId;
            this.token = token;
            this.broadcaster = broadcaster;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            // A message can arrive in pieces, and half a JSON object parses as nothing.
            buffer.append(data);
            if (last) {
                handle(buffer.toString());
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        private void handle(String message) {
            try {
                sessionIn(message).ifPresent(session -> {
                    try {
                        subscribe(clientId, token, broadcaster, session);
                    } catch (Exception e) {
                        DDC.LOGGER.warn("Twitch rewards could not subscribe: {}", e.getMessage());
                    }
                });
                redemptionIn(message).ifPresent(onRedeemed);
            } catch (RuntimeException e) {
                // Twitch sends message types this does not know and will send more of them later. An
                // unknown message is not an error; it is Twitch getting on with its own life.
                DDC.LOGGER.debug("Unreadable Twitch message", e);
            }
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            DDC.LOGGER.warn("Twitch rewards dropped: {}", error.getMessage());
            socket = null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int status, String reason) {
            socket = null;
            return null;
        }
    }
}
