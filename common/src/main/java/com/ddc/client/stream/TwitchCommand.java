package com.ddc.client.stream;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent.ClientCommandSourceStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * {@code /ddc twitch connect <channel>|disconnect|vote|close}: PRD 4.4's viewer votes.
 *
 * <p>A client command, like the overlay: this is the streamer's own machine reading their own chat,
 * and no server has any business being asked about it.
 *
 * <p>A vote is opened by the streamer, chat types {@code !adv} or {@code !dis}, and the streamer
 * closes it. The result decides the next {@code /roll}: {@code /ddc twitch close} says what chat
 * chose and how to roll it, rather than reaching over and rolling for them. What the table does with
 * chat's opinion stays the table's decision.
 */
@Environment(EnvType.CLIENT)
public final class TwitchCommand {

    private static final String ARG_CHANNEL = "channel";

    private final TwitchChat chat;
    private final ChatVote vote;

    public TwitchCommand(TwitchChat chat, ChatVote vote) {
        this.chat = chat;
        this.vote = vote;
    }

    public void register() {
        ClientCommandRegistrationEvent.EVENT.register((dispatcher, context) -> register(dispatcher));
    }

    private void register(CommandDispatcher<ClientCommandSourceStack> dispatcher) {
        dispatcher.register(ClientCommandRegistrationEvent.literal("ddc")
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
                                .executes(this::closeVote))));
    }

    private int connect(CommandContext<ClientCommandSourceStack> context) {
        String channel = StringArgumentType.getString(context, ARG_CHANNEL);

        return chat.connect(channel, vote::accept)
                .map(failure -> {
                    context.getSource().arch$sendFailure(Component.literal(failure));
                    return 0;
                })
                .orElseGet(() -> {
                    context.getSource().arch$sendSuccess(() -> Component.literal(
                            "Reading " + channel + "'s chat. Open a vote with /ddc twitch vote.")
                            .withStyle(ChatFormatting.GOLD), false);
                    return 1;
                });
    }

    private int disconnect(CommandContext<ClientCommandSourceStack> context) {
        chat.close();
        vote.close();
        context.getSource().arch$sendSuccess(() -> Component.literal("Stopped reading chat."), false);
        return 1;
    }

    private int openVote(CommandContext<ClientCommandSourceStack> context) {
        if (!chat.isConnected()) {
            context.getSource().arch$sendFailure(Component.literal(
                    "Not reading any chat. Connect with /ddc twitch connect <channel>."));
            return 0;
        }
        vote.open();
        context.getSource().arch$sendSuccess(() -> Component.literal(
                        "Vote open. Chat types " + String.join(" or ", ChatVote.words()) + ".")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private int closeVote(CommandContext<ClientCommandSourceStack> context) {
        vote.close();
        String verdict = vote.result()
                .map(choice -> "chat says " + choice.name().toLowerCase(java.util.Locale.ROOT)
                        + " — roll it with /roll 1d20 "
                        + choice.name().toLowerCase(java.util.Locale.ROOT))
                .orElse("chat could not agree — roll it straight");

        context.getSource().arch$sendSuccess(() -> Component.literal(
                        "Vote closed: " + vote.count(ChatVote.Choice.ADVANTAGE) + " advantage, "
                                + vote.count(ChatVote.Choice.DISADVANTAGE) + " disadvantage. " + verdict)
                .withStyle(ChatFormatting.GOLD), false);
        return vote.total();
    }
}
