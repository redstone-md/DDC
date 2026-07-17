package com.ddc.client.stream;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent.ClientCommandSourceStack;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * {@code /ddcstream twitch connect <channel>|disconnect|vote|close}: PRD 4.4's viewer votes.
 *
 * <p>A client command, like the overlay, and under the same {@code /ddcstream} root: a client tree
 * swallows the root it registers, and {@code /ddc} belongs to the server.
 *
 * <p>A vote is opened by the streamer, chat types {@code !adv} or {@code !dis}, and the streamer
 * closes it. The result decides the next {@code /roll}: {@code /ddcstream twitch close} says what chat
 * chose and how to roll it, rather than reaching over and rolling for them. What the table does with
 * chat's opinion stays the table's decision.
 */
@Environment(EnvType.CLIENT)
public final class TwitchCommand {

    private static final String ARG_CHANNEL = "channel";
    private static final String ARG_REWARD = "reward";
    private static final String ARG_COMMAND = "command";

    private final TwitchChat chat;
    private final ChatVote vote;
    private final TwitchRewards rewards;
    private final RewardActions actions;

    public TwitchCommand(TwitchChat chat, ChatVote vote, TwitchRewards rewards, RewardActions actions) {
        this.rewards = rewards;
        this.actions = actions;
        this.chat = chat;
        this.vote = vote;
    }

    public void register() {
        ClientCommandRegistrationEvent.EVENT.register((dispatcher, context) -> register(dispatcher));
    }

    private void register(CommandDispatcher<ClientCommandSourceStack> dispatcher) {
        dispatcher.register(ClientCommandRegistrationEvent.literal(OverlayCommand.ROOT)
                .then(ClientCommandRegistrationEvent.literal("twitch")
                        .then(ClientCommandRegistrationEvent.literal("connect")
                                .then(ClientCommandRegistrationEvent.argument(ARG_CHANNEL,
                                                StringArgumentType.word())
                                        .executes(this::connect)))
                        .then(ClientCommandRegistrationEvent.literal("disconnect")
                                .executes(this::disconnect))
                        .then(ClientCommandRegistrationEvent.literal("vote")
                                .executes(this::openVote))
                        .then(ClientCommandRegistrationEvent.literal("close")
                                .executes(this::closeVote))
                        .then(rewardBranches())
                        .then(mapBranch())));
    }

    private int connect(CommandContext<ClientCommandSourceStack> context) {
        String channel = StringArgumentType.getString(context, ARG_CHANNEL);

        return chat.connect(channel, vote::accept)
                .map(failure -> {
                    context.getSource().arch$sendFailure(Component.literal(failure));
                    return 0;
                })
                .orElseGet(() -> {
                    context.getSource().arch$sendSuccess(
                            () -> Component.translatable("ddc.stream.twitch_reading", channel)
                                    .withStyle(ChatFormatting.GOLD), false);
                    return 1;
                });
    }

    /**
     * Starts listening for channel-point redemptions.
     *
     * <p>The token is not a parameter here and never will be: a command typed into a chat box puts an
     * account's credentials in a screenshot, a log, and whatever was recording at the time. It comes
     * from the environment, and the message says so when it is missing.
     */
    private int startRewards(CommandContext<ClientCommandSourceStack> context) {
        Optional<String> failure = rewards.start();
        if (failure.isPresent()) {
            context.getSource().arch$sendFailure(Component.translatable(failure.get()));
            return 0;
        }
        context.getSource().arch$sendSuccess(() -> Component.translatable("ddc.stream.rewards_started")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private int stopRewards(CommandContext<ClientCommandSourceStack> context) {
        rewards.close();
        context.getSource().arch$sendSuccess(
                () -> Component.translatable("ddc.stream.rewards_stopped"), false);
        return 1;
    }

    /** Maps a reward's title to a command, which is the only thing a redemption can ever do. */
    private int mapReward(CommandContext<ClientCommandSourceStack> context) {
        String reward = StringArgumentType.getString(context, ARG_REWARD);
        String command = StringArgumentType.getString(context, ARG_COMMAND);
        actions.map(reward, command);
        context.getSource().arch$sendSuccess(
                () -> Component.translatable("ddc.stream.reward_mapped", reward, command), false);
        return 1;
    }

    private int forgetReward(CommandContext<ClientCommandSourceStack> context) {
        String reward = StringArgumentType.getString(context, ARG_REWARD);
        if (!actions.forget(reward)) {
            context.getSource().arch$sendFailure(
                    Component.translatable("ddc.stream.reward_unknown", reward));
            return 0;
        }
        context.getSource().arch$sendSuccess(
                () -> Component.translatable("ddc.stream.reward_forgotten", reward), false);
        return 1;
    }

    private int listRewards(CommandContext<ClientCommandSourceStack> context) {
        if (actions.isEmpty()) {
            context.getSource().arch$sendSuccess(
                    () -> Component.translatable("ddc.stream.rewards_none"), false);
            return 0;
        }
        actions.all().forEach((reward, command) -> context.getSource().arch$sendSuccess(
                () -> Component.translatable("ddc.stream.reward_mapped", reward, command), false));
        return actions.all().size();
    }

    private int disconnect(CommandContext<ClientCommandSourceStack> context) {
        chat.close();
        vote.close();
        context.getSource().arch$sendSuccess(() -> Component.translatable("ddc.stream.twitch_stopped"), false);
        return 1;
    }

    /** The {@code rewards} and {@code reward} branches, for PRD 4.5's channel points. */
    private com.mojang.brigadier.builder.ArgumentBuilder<ClientCommandSourceStack, ?> rewardBranches() {
        return ClientCommandRegistrationEvent.literal("rewards")
                .then(ClientCommandRegistrationEvent.literal("start").executes(this::startRewards))
                .then(ClientCommandRegistrationEvent.literal("stop").executes(this::stopRewards))
                .then(ClientCommandRegistrationEvent.literal("list").executes(this::listRewards));
    }

    private com.mojang.brigadier.builder.ArgumentBuilder<ClientCommandSourceStack, ?> mapBranch() {
        return ClientCommandRegistrationEvent.literal("reward")
                .then(ClientCommandRegistrationEvent.argument(ARG_REWARD, StringArgumentType.string())
                        .then(ClientCommandRegistrationEvent.argument(ARG_COMMAND,
                                        StringArgumentType.greedyString())
                                .executes(this::mapReward))
                        .executes(this::forgetReward));
    }

    private int openVote(CommandContext<ClientCommandSourceStack> context) {
        if (!chat.isConnected()) {
            context.getSource().arch$sendFailure(Component.translatable("ddc.stream.twitch_none"));
            return 0;
        }
        vote.open();
        context.getSource().arch$sendSuccess(() -> Component.translatable("ddc.stream.vote_open",
                String.join(" / ", ChatVote.words())).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private int closeVote(CommandContext<ClientCommandSourceStack> context) {
        vote.close();
        Component verdict = vote.result()
                .map(choice -> (Component) Component.translatable("ddc.stream.vote_says",
                        choice.name().toLowerCase(java.util.Locale.ROOT)))
                .orElseGet(() -> Component.translatable("ddc.stream.vote_tie"));

        context.getSource().arch$sendSuccess(() -> Component.translatable("ddc.stream.vote_closed",
                        vote.count(ChatVote.Choice.ADVANTAGE), vote.count(ChatVote.Choice.DISADVANTAGE),
                        verdict).withStyle(ChatFormatting.GOLD), false);
        return vote.total();
    }
}
