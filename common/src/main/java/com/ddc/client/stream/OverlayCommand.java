package com.ddc.client.stream;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent.ClientCommandSourceStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * {@code /ddc overlay start|stop|status}: the streamer's switch for the OBS widget feed.
 *
 * <p>A client command, not a server one. The overlay is the streamer's own machine talking to their
 * own OBS; a server has no business being asked, and a player on someone else's server should not
 * need permission to point a browser source at their own game.
 *
 * <p>It lives under {@code /ddcstream} rather than {@code /ddc} on purpose. A client command tree
 * swallows the root it registers: putting these under {@code /ddc} took the whole server-side tree
 * with them, and {@code /ddc sheet} answered "incorrect argument" instead of reaching the server.
 * The two roots are separate so they cannot shadow each other.
 */
@Environment(EnvType.CLIENT)
public final class OverlayCommand {

    /** Ports below this need privileges on most systems; above it is the ephemeral range. */
    private static final int MIN_PORT = 1024;
    private static final int MAX_PORT = 65535;

    private static final String ARG_PORT = "port";

    /**
     * The client's own command root.
     *
     * <p>Never {@code ddc}: a client tree swallows its root, and the server's {@code /ddc} lives
     * there.
     */
    public static final String ROOT = "ddcstream";

    private final OverlayServer server;

    public OverlayCommand(OverlayServer server) {
        this.server = server;
    }

    public void register() {
        ClientCommandRegistrationEvent.EVENT.register(this::register);
    }

    private void register(CommandDispatcher<ClientCommandSourceStack> dispatcher,
            net.minecraft.commands.CommandBuildContext context) {
        dispatcher.register(ClientCommandRegistrationEvent.literal(ROOT)
                .then(ClientCommandRegistrationEvent.literal("overlay")
                        .then(ClientCommandRegistrationEvent.literal("start")
                                .executes(command -> start(command, OverlayServer.DEFAULT_PORT))
                                .then(ClientCommandRegistrationEvent.argument(ARG_PORT,
                                                IntegerArgumentType.integer(MIN_PORT, MAX_PORT))
                                        .executes(command -> start(command,
                                                IntegerArgumentType.getInteger(command, ARG_PORT)))))
                        .then(ClientCommandRegistrationEvent.literal("stop")
                                .executes(this::stop))
                        .then(ClientCommandRegistrationEvent.literal("status")
                                .executes(this::status))));
    }

    private int start(CommandContext<ClientCommandSourceStack> context, int port) {
        if (server.isRunning()) {
            context.getSource().arch$sendFailure(Component.literal("The overlay is already running."));
            return 0;
        }
        OverlayServer.Result result = server.start(port);
        if (!result.started()) {
            context.getSource().arch$sendFailure(Component.literal(result.message()));
            return 0;
        }
        context.getSource().arch$sendSuccess(() -> Component.literal(result.message()
                + " — add it to OBS as a Browser Source.").withStyle(ChatFormatting.GOLD), false);
        return port;
    }

    private int stop(CommandContext<ClientCommandSourceStack> context) {
        server.stop();
        context.getSource().arch$sendSuccess(() -> Component.literal("Overlay stopped."), false);
        return 1;
    }

    private int status(CommandContext<ClientCommandSourceStack> context) {
        String message = server.isRunning()
                ? "Overlay running, " + server.connected() + " widget(s) connected."
                : "Overlay stopped. Start it with /ddcstream overlay start.";
        context.getSource().arch$sendSuccess(() -> Component.literal(message), false);
        return server.isRunning() ? 1 : 0;
    }
}
