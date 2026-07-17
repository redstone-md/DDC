package com.ddc.client;

import com.ddc.client.dice.RollCache;
import com.ddc.client.screen.CharacterSheetScreen;
import com.ddc.client.screen.GameMasterScreen;
import com.ddc.client.screen.PlayerWheel;
import com.ddc.client.screen.WheelScreen;
import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import com.ddc.client.stream.ChatVote;
import com.ddc.client.stream.OverlayCommand;
import com.ddc.client.stream.TwitchChat;
import com.ddc.client.stream.TwitchCommand;
import com.ddc.client.stream.OverlayEvents;
import com.ddc.client.stream.OverlayServer;
import com.ddc.network.CharacterSheetPayload;
import com.ddc.network.DiceResultPayload;
import com.ddc.network.NarrationPayload;
import com.ddc.network.RulesPayload;
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
    private static final ColourGrade GRADE = new ColourGrade();

    /**
     * The stream overlay. Off until a streamer asks for it: a mod that opens a socket on every
     * machine that installs it, for a feature most players never use, has helped itself to something
     * it was not given.
     */
    private static final OverlayServer OVERLAY = new OverlayServer();

    /** Twitch chat, for PRD 4.4's viewer votes. Reads nothing until a streamer asks it to. */
    private static final TwitchChat TWITCH = new TwitchChat();
    private static final ChatVote VOTE = new ChatVote();

    /** PRD 3.1's sheet key. */
    private static final KeyMapping SHEET_KEY = new KeyMapping("key.ddc.sheet",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, KEY_CATEGORY);

    /** PRD 3.2's control panel key. Bound for everyone; every button it sends is GM-gated. */
    private static final KeyMapping PANEL_KEY = new KeyMapping("key.ddc.panel",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, KEY_CATEGORY);

    /** The wheel: the actions a character has, without typing them. */
    private static final KeyMapping WHEEL_KEY = new KeyMapping("key.ddc.wheel",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, KEY_CATEGORY);

    private DDCClient() {
    }

    /**
     * Opens the sheet the server last sent.
     *
     * <p>Nothing is asked of the server: the client already has the sheet, and a key that made a
     * round trip would open on a stale one anyway.
     */
    private static void openSheet(Minecraft client) {
        CHARACTER_HUD.sheet().ifPresent(sheet -> client.setScreen(sheetScreen()));
    }

    /** Opens the wheel: the one that makes a character, or the one that plays them. */
    private static void openWheel(Minecraft client) {
        client.setScreen(PlayerWheel.forPlayer(CHARACTER_HUD.sheet().orElse(null),
                CHARACTER_HUD.summary()));
    }

    /** The sheet this client was last sent, for the menus that draw from it. */
    public static java.util.Optional<com.ddc.character.CharacterSheet> sheet() {
        return CHARACTER_HUD.sheet();
    }

    /** The sheet screen, for the wheel slice that opens it. */
    public static net.minecraft.client.gui.screens.Screen sheetScreen() {
        return new CharacterSheetScreen(CHARACTER_HUD.sheet().orElse(null), CHARACTER_HUD.className());
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
                    GRADE.accept(payload.result().isNatural20(), payload.result().isNatural1(),
                            isMine(payload.roller()), now);
                    OVERLAY.broadcast(OverlayEvents.diceRoll(payload.rollerName(), payload.result()));
                }));

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, CharacterSheetPayload.TYPE,
                CharacterSheetPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() ->
                        CHARACTER_HUD.accept(payload.sheet(), payload.summary(), payload.armorClass())));

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, RulesPayload.TYPE,
                RulesPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> ClientRules.accept(payload)));

        // The party goes straight to the overlay: PRD 4.5's health cards are the only thing that
        // asked for it, and a client already draws its own player from its own sheet.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, com.ddc.network.PartyPayload.TYPE,
                com.ddc.network.PartyPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() ->
                        OVERLAY.broadcast(OverlayEvents.party(payload.members()))));

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, NarrationPayload.TYPE,
                NarrationPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> NARRATION.accept(payload.text(), Util.getMillis())));

        new OverlayCommand(OVERLAY).register();
        new TwitchCommand(TWITCH, VOTE).register();
        KeyMappingRegistry.register(SHEET_KEY);
        KeyMappingRegistry.register(PANEL_KEY);
        KeyMappingRegistry.register(WHEEL_KEY);

        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            RollCache.clear();
            GRADE.tick(Long.MAX_VALUE);
            ClientRules.clear();
            // Leaving a world drops the widgets and the chat: what they were watching is gone.
            OVERLAY.stop();
            TWITCH.close();
        });

        // The shake moves the player's own head, so it has to happen on the client tick rather than
        // during rendering, where the camera has already been placed for the frame.
        dev.architectury.event.events.client.ClientTickEvent.CLIENT_POST.register(client -> {
            FANFARE.applyShake(Util.getMillis());
            // The grade must come off on its own, so it is taken off on a tick rather than trusted
            // to whatever set it.
            GRADE.tick(Util.getMillis());
            while (SHEET_KEY.consumeClick()) {
                openSheet(client);
            }
            while (PANEL_KEY.consumeClick()) {
                client.setScreen(new GameMasterScreen());
            }
            while (WHEEL_KEY.consumeClick()) {
                openWheel(client);
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
