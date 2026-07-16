package com.ddc.client.stream;

import com.ddc.DDC;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.net.InetSocketAddress;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * The stream overlay's WebSocket server, from ARCHITECTURE.md 5.
 *
 * <p>A streamer points an OBS browser source at {@code ws://localhost:8082} and gets the table's
 * state as JSON: every roll, as it happens. The widget itself is HTML the streamer writes or takes
 * from the docs; this only feeds it.
 *
 * <p><strong>Off unless asked for.</strong> It listens on a socket, and a mod that opens one on every
 * machine that installs it, for a feature most players will never use, has helped itself to
 * something it was not given. {@code /ddc overlay start} turns it on for the session.
 *
 * <p>Bound to the loopback address on purpose: OBS runs on the streamer's own machine, so nothing
 * outside it has any business connecting, and binding wider would put a player's table on their
 * local network without asking.
 */
@Environment(EnvType.CLIENT)
public final class OverlayServer {

    /** ARCHITECTURE.md's port. */
    public static final int DEFAULT_PORT = 8082;

    private final ChannelGroup clients = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private EventLoopGroup group;
    private Channel channel;

    /**
     * Starts listening.
     *
     * @return the port it came up on, or empty with the reason it did not
     * @throws IllegalStateException if it is already running
     */
    public synchronized Result start(int port) {
        if (isRunning()) {
            throw new IllegalStateException("The overlay server is already running");
        }
        try {
            group = new NioEventLoopGroup(1, runnable -> {
                Thread thread = new Thread(runnable, "DDC overlay");
                // A daemon: a stream overlay must never be the reason Minecraft will not close.
                thread.setDaemon(true);
                return thread;
            });
            channel = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new Initializer())
                    .bind(new InetSocketAddress("127.0.0.1", port))
                    .sync()
                    .channel();
            DDC.LOGGER.info("Stream overlay listening on ws://localhost:{}", port);
            return new Result(true, "Overlay running on ws://localhost:" + port);
        } catch (Exception e) {
            stop();
            DDC.LOGGER.warn("The stream overlay could not start on port {}", port, e);
            return new Result(false, "Could not open port " + port + ": " + e.getMessage());
        }
    }

    /** Stops listening and drops every widget. Safe to call when it was never started. */
    public synchronized void stop() {
        clients.close();
        if (channel != null) {
            channel.close();
            channel = null;
        }
        if (group != null) {
            group.shutdownGracefully();
            group = null;
        }
    }

    public synchronized boolean isRunning() {
        return channel != null && channel.isActive();
    }

    /** How many widgets are listening. What {@code /ddc overlay status} reports. */
    public int connected() {
        return clients.size();
    }

    /** Sends one line of JSON to every connected widget. Does nothing when none are. */
    public void broadcast(String json) {
        if (!clients.isEmpty()) {
            clients.writeAndFlush(new TextWebSocketFrame(json));
        }
    }

    /** What a start attempt did, for the command to report. */
    public record Result(boolean started, String message) {
    }

    private final class Initializer extends ChannelInitializer<SocketChannel> {

        @Override
        protected void initChannel(SocketChannel channel) {
            channel.pipeline()
                    .addLast(new HttpServerCodec())
                    .addLast(new HttpObjectAggregator(8192))
                    .addLast(new WebSocketServerProtocolHandler("/"))
                    .addLast(new io.netty.channel.ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(io.netty.channel.ChannelHandlerContext context) {
                            clients.add(context.channel());
                        }

                        @Override
                        public void exceptionCaught(io.netty.channel.ChannelHandlerContext context,
                                Throwable cause) {
                            // A widget that misbehaves loses its connection; it never takes the game
                            // down with it.
                            DDC.LOGGER.debug("Overlay client dropped", cause);
                            context.close();
                        }
                    });
        }
    }
}
