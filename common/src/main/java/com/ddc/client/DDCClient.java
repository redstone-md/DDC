package com.ddc.client;

import com.ddc.client.dice.RollCache;
import com.ddc.client.screen.CharacterSheetScreen;
import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import com.ddc.client.stream.OverlayCommand;
import com.ddc.client.stream.OverlayEvents;
import com.ddc.client.stream.OverlayServer;
import com.ddc.network.CharacterSheetPayload;
import com.ddc.network.DiceResultPayload;
import com.ddc.network.NarrationPayload;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
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

    private static final KeyMapping.Category KEY_CATEGORY =
            KeyMapping.Category.register(com.ddc.DDC.id("keys"));

    private static final RollLog ROLL_LOG = new RollLog();
    private static final CharacterHud CHARACTER_HUD = new CharacterHud();
    private static final NarrationOverlay NARRATION = new NarrationOverlay();
    private static final Fanfare FANFARE = new Fanfare();

    /**
     * The stream overlay. Off until a streamer asks for it: a mod that opens a socket on every
     * machine that installs it, for a feature most players never use, has helped itself to something
     * it was not given.
     */
    private static final OverlayServer OVERLAY = new OverlayServer();

    /** PRD 3.1's sheet key. */
    private static final KeyMapping SHEET_KEY = new KeyMapping("key.ddc.sheet",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, KEY_CATEGORY);

    private DDCClient() {
    }

    /**
     * Opens the sheet the server last sent.
     *
     * <p>Nothing is asked of the server: the client already has the sheet, and a key that made a
     * round trip would open on a stale one anyway.
     */
    private static void openSheet(Minecraft client) {
        CHARACTER_HUD.sheet().ifPresent(sheet -> client.setScreen(
                new CharacterSheetScreen(sheet, CHARACTER_HUD.className())));
    }

    /** The fanfare, for the loader hooks that have to drive the camera themselves. */
    public static Fanfare fanfare() {
        return FANFARE;
    }

    /** Whether a roll was this client's own: only your own dice shake your screen. */
    private static boolean isMine(java.util.UUID roller) {
        return Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.getUUID().equals(roller);
    }

    public static void init() {
        // Payloads arrive on the netty thread; queue() hops to the client thread before touching any
        // game state.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DiceResultPayload.TYPE,
                DiceResultPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> {
                    long now = Util.getMillis();
                    ROLL_LOG.accept(payload, now);
                    // The dice standing in the world find their faces here, by seed.
                    RollCache.put(payload.result());
                    FANFARE.accept(payload.result(), isMine(payload.roller()), now);
                    OVERLAY.broadcast(OverlayEvents.diceRoll(payload.rollerName(), payload.result()));
                }));

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, CharacterSheetPayload.TYPE,
                CharacterSheetPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() ->
                        CHARACTER_HUD.accept(payload.sheet())));

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, NarrationPayload.TYPE,
                NarrationPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> NARRATION.accept(payload.text(), Util.getMillis())));

        new OverlayCommand(OVERLAY).register();
        KeyMappingRegistry.register(SHEET_KEY);

        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            RollCache.clear();
            // Leaving a world drops the widgets: what they were watching is gone.
            OVERLAY.stop();
        });

        // The shake moves the player's own head, so it has to happen on the client tick rather than
        // during rendering, where the camera has already been placed for the frame.
        dev.architectury.event.events.client.ClientTickEvent.CLIENT_POST.register(client -> {
            FANFARE.applyShake(Util.getMillis());
            while (SHEET_KEY.consumeClick()) {
                openSheet(client);
            }
        });

        ClientGuiEvent.RENDER_HUD.register((graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            long now = Util.getMillis();
            CHARACTER_HUD.render(graphics, client.font, client.player);
            ROLL_LOG.render(graphics, client.font, now);
            NARRATION.render(graphics, client.font, now);
            FANFARE.render(graphics, client.font, now);
        });
    }
}
