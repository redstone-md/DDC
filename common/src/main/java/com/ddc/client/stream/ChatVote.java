package com.ddc.client.stream;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * PRD 4.4's viewer vote: chat decides whether the streamer's next roll has advantage.
 *
 * <p>One vote per viewer, counted while the vote is open and ignored after. The tally is what the
 * overlay draws and what the roll uses.
 *
 * <p>Nothing here talks to Twitch. It counts votes, and is fed by whatever is reading the chat, which
 * makes the rule -- one viewer, one vote, majority wins, ties do nothing -- something that can be
 * tested without a network.
 */
@Environment(EnvType.CLIENT)
public final class ChatVote {

    /** What a viewer can type. Anything else is somebody talking. */
    private static final Map<String, Choice> WORDS = Map.of(
            "!adv", Choice.ADVANTAGE,
            "!advantage", Choice.ADVANTAGE,
            "!dis", Choice.DISADVANTAGE,
            "!disadvantage", Choice.DISADVANTAGE);

    /** Which way a viewer voted. */
    public enum Choice {
        ADVANTAGE,
        DISADVANTAGE
    }

    private final Map<String, Choice> votes = new ConcurrentHashMap<>();
    private volatile boolean open;

    /** Opens a vote, forgetting the last one. */
    public void open() {
        votes.clear();
        open = true;
    }

    /** Closes the vote. The tally stays readable until the next one opens. */
    public void close() {
        open = false;
    }

    public boolean isOpen() {
        return open;
    }

    /**
     * Counts a line of chat.
     *
     * <p>A viewer who votes twice has their last word counted, rather than both: a vote is what
     * someone thinks now, and letting one person stack votes by spamming would make the whole thing
     * a typing race.
     *
     * @return what it was read as, or empty when the line was not a vote or the vote is closed
     */
    public Optional<Choice> accept(String viewer, String message) {
        if (!open) {
            return Optional.empty();
        }
        Choice choice = WORDS.get(message.trim().toLowerCase(Locale.ROOT));
        if (choice == null) {
            return Optional.empty();
        }
        votes.put(viewer.toLowerCase(Locale.ROOT), choice);
        return Optional.of(choice);
    }

    public int count(Choice choice) {
        return (int) votes.values().stream().filter(vote -> vote == choice).count();
    }

    public int total() {
        return votes.size();
    }

    /**
     * What chat decided, or empty on a tie or an empty vote.
     *
     * <p>A tie is nothing rather than a coin flip: the table asked chat a question, and "chat could
     * not agree" is a real answer that a coin flip would hide.
     */
    public Optional<Choice> result() {
        int advantage = count(Choice.ADVANTAGE);
        int disadvantage = count(Choice.DISADVANTAGE);
        if (advantage == disadvantage) {
            return Optional.empty();
        }
        return Optional.of(advantage > disadvantage ? Choice.ADVANTAGE : Choice.DISADVANTAGE);
    }

    /** The vote as the roll wants it: the mode to roll with, or normal when chat did not decide. */
    public com.ddc.core.dice.RollMode mode() {
        return result()
                .map(choice -> choice == Choice.ADVANTAGE
                        ? com.ddc.core.dice.RollMode.ADVANTAGE
                        : com.ddc.core.dice.RollMode.DISADVANTAGE)
                .orElse(com.ddc.core.dice.RollMode.NORMAL);
    }

    /** The words a viewer can type, for telling chat what to do. */
    public static Set<String> words() {
        return WORDS.keySet();
    }
}
