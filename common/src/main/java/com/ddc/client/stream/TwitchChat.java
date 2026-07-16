package com.ddc.client.stream;

import com.ddc.DDC;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.net.SocketFactory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Reads a Twitch channel's chat, for PRD 4.4's viewer votes.
 *
 * <p>Twitch's chat is IRC, and this is the small part of IRC that matters: connect, authenticate,
 * join a channel, and read PRIVMSG lines. There is no Twitch library here because a dependency for
 * forty lines of line-splitting would be a worse trade than the forty lines.
 *
 * <p><strong>Read-only, and off unless asked for.</strong> It never sends a message to chat, and it
 * connects nowhere until a streamer runs {@code /ddc twitch connect}. A mod that dialled out on its
 * own would be doing something nobody asked it to.
 *
 * <p>Anonymous by default: Twitch lets a client read any public chat by logging in as {@code
 * justinfan} with no token, which is what this does. That means a streamer never has to hand DDC an
 * OAuth token to run a vote, and DDC never has to be trusted with one.
 */
@Environment(EnvType.CLIENT)
public final class TwitchChat implements AutoCloseable {

    private static final String HOST = "irc.chat.twitch.tv";
    private static final int PORT = 6697;

    /** Twitch's anonymous reader. No token, no account, read-only by construction. */
    private static final String ANONYMOUS_USER = "justinfan12345";

    private final SocketFactory sockets;
    private volatile Socket socket;
    private volatile Thread reader;

    public TwitchChat() {
        this(javax.net.ssl.SSLSocketFactory.getDefault());
    }

    /** Takes the socket factory so a test can hand it a plain local server instead of Twitch. */
    TwitchChat(SocketFactory sockets) {
        this.sockets = sockets;
    }

    /**
     * Connects and starts reading a channel.
     *
     * @param channel  the channel's name, with or without its leading hash
     * @param onMessage called with (viewer, message) for every line of chat, on the reader thread
     * @return empty once connected, or why it did not
     */
    public synchronized Optional<String> connect(String host, int port, String channel,
            BiConsumer<String, String> onMessage) {
        if (isConnected()) {
            return Optional.of("Already connected. Disconnect first.");
        }
        String room = channel.startsWith("#") ? channel : "#" + channel;
        try {
            socket = sockets.createSocket(host, port);
            PrintWriter out = new PrintWriter(
                    new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            out.println("PASS SCHMOOPIIE");
            out.println("NICK " + ANONYMOUS_USER);
            out.println("JOIN " + room.toLowerCase(Locale.ROOT));

            reader = new Thread(() -> read(out, onMessage), "DDC Twitch chat");
            // A daemon: chat must never be the reason Minecraft will not close.
            reader.setDaemon(true);
            reader.start();
            DDC.LOGGER.info("Reading Twitch chat for {}", room);
            return Optional.empty();
        } catch (IOException e) {
            close();
            return Optional.of("Could not reach " + host + ": " + e.getMessage());
        }
    }

    /** Connects to Twitch itself. */
    public Optional<String> connect(String channel, BiConsumer<String, String> onMessage) {
        return connect(HOST, PORT, channel, onMessage);
    }

    public boolean isConnected() {
        Socket current = socket;
        return current != null && current.isConnected() && !current.isClosed();
    }

    private void read(PrintWriter out, BiConsumer<String, String> onMessage) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                // Twitch drops a reader that does not answer its ping.
                if (line.startsWith("PING")) {
                    out.println("PONG :tmi.twitch.tv");
                    continue;
                }
                parse(line).ifPresent(message -> onMessage.accept(message.viewer(), message.text()));
            }
        } catch (IOException e) {
            DDC.LOGGER.debug("Twitch chat closed", e);
        }
    }

    /**
     * Pulls a viewer and their message out of an IRC line, if it is one.
     *
     * <p>The shape is {@code :viewer!viewer@viewer.tmi.twitch.tv PRIVMSG #channel :the message}.
     * Anything else -- joins, notices, the welcome banner -- is not chat and is dropped.
     *
     * <p>Package-visible so the parsing can be tested without a socket.
     */
    static Optional<Message> parse(String line) {
        if (!line.startsWith(":") || !line.contains(" PRIVMSG ")) {
            return Optional.empty();
        }
        int bang = line.indexOf('!');
        int privmsg = line.indexOf(" PRIVMSG ");
        if (bang < 1 || bang > privmsg) {
            return Optional.empty();
        }
        int text = line.indexOf(" :", privmsg);
        if (text < 0) {
            return Optional.empty();
        }
        return Optional.of(new Message(line.substring(1, bang), line.substring(text + 2)));
    }

    /** One line of chat. */
    record Message(String viewer, String text) {
    }

    @Override
    public synchronized void close() {
        Socket current = socket;
        socket = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException e) {
                DDC.LOGGER.debug("Twitch chat would not close cleanly", e);
            }
        }
        Thread current_reader = reader;
        reader = null;
        if (current_reader != null) {
            current_reader.interrupt();
        }
    }
}
