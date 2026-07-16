package com.ddc.client.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.core.dice.RollMode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.SocketFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The chat reader is a promise about someone else's protocol, so this stands up a fake IRC server
 * that speaks the lines Twitch speaks and checks what comes out the other end.
 */
class TwitchChatTest {

    private static String privmsg(String viewer, String text) {
        return ":" + viewer + "!" + viewer + "@" + viewer + ".tmi.twitch.tv PRIVMSG #channel :" + text;
    }

    @Test
    void readsAViewerAndTheirMessage() {
        Optional<TwitchChat.Message> message = TwitchChat.parse(privmsg("viewer", "!adv"));

        assertTrue(message.isPresent());
        assertEquals("viewer", message.orElseThrow().viewer());
        assertEquals("!adv", message.orElseThrow().text());
    }

    @Test
    @DisplayName("a message with a colon in it survives intact")
    void keepsTheWholeMessage() {
        assertEquals("well: maybe !adv?",
                TwitchChat.parse(privmsg("viewer", "well: maybe !adv?")).orElseThrow().text());
    }

    @Test
    void ignoresEverythingThatIsNotChat() {
        assertTrue(TwitchChat.parse(":tmi.twitch.tv 001 justinfan12345 :Welcome, GLHF!").isEmpty());
        assertTrue(TwitchChat.parse(":viewer!viewer@viewer.tmi.twitch.tv JOIN #channel").isEmpty());
        assertTrue(TwitchChat.parse("PING :tmi.twitch.tv").isEmpty());
        assertTrue(TwitchChat.parse("").isEmpty());
        assertTrue(TwitchChat.parse(":malformed PRIVMSG").isEmpty());
    }

    /**
     * The whole path, against a server that speaks what Twitch speaks: connect, join, read chat, and
     * answer a ping so the connection is not dropped.
     */
    @Test
    @DisplayName("chat is read off a real socket, and a ping is answered")
    void readsChatFromASocket() throws Exception {
        CountDownLatch got = new CountDownLatch(2);
        CountDownLatch ponged = new CountDownLatch(1);
        StringBuilder heard = new StringBuilder();

        try (ServerSocket server = new ServerSocket(0)) {
            Thread fakeTwitch = new Thread(() -> {
                try (Socket client = server.accept();
                        PrintWriter out = new PrintWriter(new java.io.OutputStreamWriter(
                                client.getOutputStream(), StandardCharsets.UTF_8), true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(
                                client.getInputStream(), StandardCharsets.UTF_8))) {
                    in.readLine(); // PASS
                    in.readLine(); // NICK
                    in.readLine(); // JOIN

                    out.println(":tmi.twitch.tv 001 justinfan12345 :Welcome, GLHF!");
                    out.println(privmsg("alice", "!adv"));
                    out.println(privmsg("bob", "hello chat"));
                    out.println("PING :tmi.twitch.tv");

                    if ("PONG :tmi.twitch.tv".equals(in.readLine())) {
                        ponged.countDown();
                    }
                } catch (IOException e) {
                    // The test's own socket closing at the end is not a failure.
                }
            });
            fakeTwitch.setDaemon(true);
            fakeTwitch.start();

            try (TwitchChat chat = new TwitchChat(SocketFactory.getDefault())) {
                Optional<String> failure = chat.connect("127.0.0.1", server.getLocalPort(), "channel",
                        (viewer, text) -> {
                            heard.append(viewer).append(':').append(text).append(' ');
                            got.countDown();
                        });

                assertTrue(failure.isEmpty(), () -> "could not connect: " + failure.orElse(""));
                assertTrue(got.await(10, TimeUnit.SECONDS), "chat never arrived: " + heard);
                assertTrue(ponged.await(10, TimeUnit.SECONDS), "the ping went unanswered");

                assertTrue(heard.toString().contains("alice:!adv"));
                assertTrue(heard.toString().contains("bob:hello chat"),
                        "every line is passed on; deciding what is a vote is not this class's job");
            }
        }
    }

    @Test
    void reportsAHostItCannotReach() {
        try (TwitchChat chat = new TwitchChat(SocketFactory.getDefault())) {
            Optional<String> failure = chat.connect("127.0.0.1", 1, "channel", (viewer, text) -> { });

            assertTrue(failure.isPresent());
            assertFalse(chat.isConnected());
        }
    }

    @Test
    void aVoteCountsOneViewerOnce() {
        ChatVote vote = new ChatVote();
        vote.open();

        vote.accept("alice", "!adv");
        vote.accept("alice", "!adv");
        vote.accept("bob", "!dis");

        assertEquals(2, vote.total(), "alice votes once, however often she types");
        assertEquals(1, vote.count(ChatVote.Choice.ADVANTAGE));
    }

    @Test
    @DisplayName("a viewer who changes their mind is counted where they ended up")
    void aViewerCanChangeTheirMind() {
        ChatVote vote = new ChatVote();
        vote.open();

        vote.accept("alice", "!adv");
        vote.accept("alice", "!dis");

        assertEquals(1, vote.total());
        assertEquals(ChatVote.Choice.DISADVANTAGE, vote.result().orElseThrow());
    }

    @Test
    void chatTalkingIsNotAVote() {
        ChatVote vote = new ChatVote();
        vote.open();

        assertTrue(vote.accept("alice", "advantage would be nice").isEmpty());
        assertTrue(vote.accept("bob", "").isEmpty());
        assertEquals(0, vote.total());
    }

    @Test
    void votesOutsideAnOpenVoteAreIgnored() {
        ChatVote vote = new ChatVote();

        assertTrue(vote.accept("alice", "!adv").isEmpty());

        vote.open();
        vote.accept("alice", "!adv");
        vote.close();
        vote.accept("bob", "!dis");

        assertEquals(1, vote.total(), "the vote closed before bob typed");
    }

    @Test
    @DisplayName("a tie is chat failing to decide, not a coin flip")
    void aTieDecidesNothing() {
        ChatVote vote = new ChatVote();
        vote.open();
        vote.accept("alice", "!adv");
        vote.accept("bob", "!dis");

        assertTrue(vote.result().isEmpty());
        assertEquals(RollMode.NORMAL, vote.mode());
    }

    @Test
    void anEmptyVoteDecidesNothing() {
        ChatVote vote = new ChatVote();
        vote.open();

        assertTrue(vote.result().isEmpty());
        assertEquals(RollMode.NORMAL, vote.mode());
    }

    @Test
    void theMajorityDecidesTheRoll() {
        ChatVote vote = new ChatVote();
        vote.open();
        vote.accept("alice", "!advantage");
        vote.accept("bob", "!adv");
        vote.accept("carol", "!dis");

        assertEquals(RollMode.ADVANTAGE, vote.mode());
    }

    @Test
    void openingAVoteForgetsTheLastOne() {
        ChatVote vote = new ChatVote();
        vote.open();
        vote.accept("alice", "!adv");

        vote.open();

        assertEquals(0, vote.total());
    }
}
