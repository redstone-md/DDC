package com.ddc.client.stream;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Serves the overlay's page to OBS.
 *
 * <p>A browser source opens a URL and renders what comes back, so the overlay has to be a page. This
 * is the page: one file, HTML and CSS and script together, which then opens the WebSocket for itself.
 * Requests for {@code /ws} never reach here -- the upgrade handler ahead of this takes them.
 *
 * <p>One file rather than a page plus a stylesheet plus a script: three requests to serve, three
 * paths to get right, and a streamer who wants to restyle it can read one file.
 */
@Environment(EnvType.CLIENT)
final class WidgetHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final String WIDGET = "/assets/ddc/overlay/widget.html";

    @Override
    protected void channelRead0(ChannelHandlerContext context, FullHttpRequest request) {
        if (!HttpMethod.GET.equals(request.method())) {
            send(context, HttpResponseStatus.METHOD_NOT_ALLOWED, "text/plain", "GET only");
            return;
        }
        String path = request.uri().split("\\?", 2)[0];
        if (!"/".equals(path) && !"/index.html".equals(path)) {
            send(context, HttpResponseStatus.NOT_FOUND, "text/plain",
                    "The overlay is at / and its socket is at " + OverlayServer.SOCKET_PATH);
            return;
        }
        send(context, HttpResponseStatus.OK, "text/html; charset=utf-8", widget());
    }

    /**
     * The widget's source.
     *
     * <p>Read from the jar every time rather than cached: it is a few kilobytes, a browser source is
     * opened once, and a streamer editing a resource pack copy should see their edit on refresh.
     */
    private static String widget() {
        try (InputStream in = WidgetHandler.class.getResourceAsStream(WIDGET)) {
            if (in == null) {
                return "<!doctype html><title>DDC</title><p>The overlay's page is missing from the jar.";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<!doctype html><title>DDC</title><p>The overlay's page could not be read.";
        }
    }

    private static void send(ChannelHandlerContext context, HttpResponseStatus status,
            String contentType, String body) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        // A browser source that reconnects must not be handed a stale table.
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
        context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
