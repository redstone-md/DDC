package com.ddc.client;

import com.ddc.network.CharacterSheetPayload;
import com.ddc.network.DiceResultPayload;
import com.ddc.network.NarrationPayload;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.networking.NetworkManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

/**
 * The client-side bootstrap, called by both loaders' client entry points.
 *
 * <p>Registering the S2C receivers here is also what registers their payload types on this side; see
 * {@link com.ddc.network.DDCNetwork}.
 */
@Environment(EnvType.CLIENT)
public final class DDCClient {

    private static final RollLog ROLL_LOG = new RollLog();
    private static final CharacterHud CHARACTER_HUD = new CharacterHud();
    private static final NarrationOverlay NARRATION = new NarrationOverlay();

    private DDCClient() {
    }

    public static void init() {
        // Payloads arrive on the netty thread; queue() hops to the client thread before touching any
        // game state.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DiceResultPayload.TYPE,
                DiceResultPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> ROLL_LOG.accept(payload, Util.getMillis())));

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, CharacterSheetPayload.TYPE,
                CharacterSheetPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() ->
                        CHARACTER_HUD.accept(payload.sheet())));

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, NarrationPayload.TYPE,
                NarrationPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> NARRATION.accept(payload.text(), Util.getMillis())));

        ClientGuiEvent.RENDER_HUD.register((graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            long now = Util.getMillis();
            CHARACTER_HUD.render(graphics, client.font, client.player);
            ROLL_LOG.render(graphics, client.font, now);
            NARRATION.render(graphics, client.font, now);
        });
    }
}
