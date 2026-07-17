package com.ddc.client;

import com.ddc.client.dice.RollCache;
import com.ddc.client.screen.CharacterSheetScreen;
import com.ddc.client.screen.GameMasterScreen;
import com.ddc.client.screen.GuideScreen;
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

    /** While possessing: first person, over the shoulder, or facing yourself. */
    private static final KeyMapping VIEW_KEY = new KeyMapping("key.ddc.view",
            org.lwjgl.glfw.GLFW.GLFW_KEY_V, keyCategory());

    /** The streamer's HUD: the same facts, out of the way of a camera. */
    private static final KeyMapping STREAMER_KEY = new KeyMapping("key.ddc.streamer",
            org.lwjgl.glfw.GLFW.GLFW_KEY_O, keyCategory());

    private static KeyMapping.Category keyCategory() {
        return KEY_CATEGORY;
    }

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
    private static final com.ddc.client.stream.RewardActions REWARD_ACTIONS =
            new com.ddc.client.stream.RewardActions();

    /**
     * PRD 4.5's channel points: a viewer spends theirs and something happens.
     *
     * <p>What happens is a command the streamer mapped, run as the streamer, on the client thread --
     * so a redemption can do exactly what they could have typed and nothing more. The server checks it
     * the same way it checks anything else they type, which is why a viewer cannot buy a spawn on a
     * server that would not let the streamer spawn one.
     */
    private static final com.ddc.client.stream.TwitchRewards REWARDS =
            new com.ddc.client.stream.TwitchRewards(redemption -> {
                Minecraft client = Minecraft.getInstance();
                client.execute(() -> REWARD_ACTIONS.commandFor(redemption).ifPresent(command -> {
                    if (client.player != null) {
                        client.player.sendSystemMessage(
                                net.minecraft.network.chat.Component.translatable(
                                        "ddc.stream.reward_redeemed", redemption.viewer(),
                                        redemption.reward()).withStyle(net.minecraft.ChatFormatting.GOLD));
                        client.player.connection.sendCommand(command);
                    }
                }));
            });

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

    /** What the streamer's chat voted, which the wheel's roll carries. */
    public static ChatVote vote() {
        return VOTE;
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

    /**
     * Sends the attack key on to the server while the player is driving a monster.
     *
     * <p>A possessing Game Master is a spectator, and vanilla throws a spectator's clicks away before
     * anything can see them -- which is why the GM could steer a monster but never hit anyone with it.
     *
     * <p>Spectating something that is not yourself is the whole test. The server checks whether this
     * player is really possessing that mob before anything is hit, so a client that sent this at the
     * wrong moment gets nothing for it.
     */
    private static void sendPossessedAttacks(Minecraft client) {
        if (client.player == null || client.getCameraEntity() == client.player) {
            return;
        }
        while (client.options.keyAttack.consumeClick()) {
            NetworkManager.sendToServer(new com.ddc.network.PossessActionPayload());
        }
    }

    /**
     * PRD 3.2's "first-person or third-person" while possessing.
     *
     * <p>Vanilla's own F5 changes the camera, and while spectating a creature it changes nothing:
     * spectator locks the view to the eyes of what you are riding. So this says it again, in a way the
     * game listens to -- and only while a GM is actually driving something, because a key that changed
     * the camera everywhere would be a worse F5.
     */
    private static void togglePossessionView(Minecraft client) {
        if (client.player == null || client.getCameraEntity() == client.player) {
            return;
        }
        net.minecraft.client.CameraType next = switch (client.options.getCameraType()) {
            case FIRST_PERSON -> net.minecraft.client.CameraType.THIRD_PERSON_BACK;
            case THIRD_PERSON_BACK -> net.minecraft.client.CameraType.THIRD_PERSON_FRONT;
            case THIRD_PERSON_FRONT -> net.minecraft.client.CameraType.FIRST_PERSON;
        };
        client.options.setCameraType(next);
        // Above the hotbar: a camera change is a thing you see happen, not a thing to read about.
        client.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                "ddc.gm.view", net.minecraft.network.chat.Component.translatable(
                        "options.thirdperson." + next.name().toLowerCase(java.util.Locale.ROOT))));
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

        // The quest goes to the overlay and nowhere else: it is a line for a stream, and a player who
        // wants to know what they are doing has a Game Master to ask.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, com.ddc.network.QuestPayload.TYPE,
                com.ddc.network.QuestPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() ->
                        OVERLAY.broadcast(OverlayEvents.quest(payload.text()))));

        // The party goes straight to the overlay: PRD 4.5's health cards are the only thing that
        // asked for it, and a client already draws its own player from its own sheet.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, com.ddc.network.PartyPayload.TYPE,
                com.ddc.network.PartyPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() ->
                        OVERLAY.broadcast(OverlayEvents.party(payload.members()))));

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, NarrationPayload.TYPE,
                NarrationPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> NARRATION.accept(payload.text(), Util.getMillis())));

        // A client command, on the client's own root: /ddc belongs to the server, and a client that
        // registered a branch of it swallowed every server command under it -- which shipped once.
        dev.architectury.event.events.client.ClientCommandRegistrationEvent.EVENT.register(
                (dispatcher, buildContext) -> dispatcher.register(
                        dev.architectury.event.events.client.ClientCommandRegistrationEvent
                                .literal("ddcguide")
                                .executes(context -> {
                                    // Opened on the next tick: a screen cannot be set from inside the
                                    // command that is still running.
                                    Minecraft.getInstance().schedule(
                                            () -> Minecraft.getInstance().setScreen(new GuideScreen()));
                                    return 1;
                                })));

        // PRD 3.2's context menu. The click is caught on the client because a screen can only be
        // opened here; the server never hears about it, so the wand's own placing is untouched for
        // anyone the menu does not open for -- and every option on the menu is a command the server
        // checks exactly as if it had been typed.
        dev.architectury.event.events.common.InteractionEvent.RIGHT_CLICK_BLOCK.register(
                (player, hand, pos, face) -> {
                    if (!player.level().isClientSide()
                            || hand != net.minecraft.world.InteractionHand.MAIN_HAND
                            || !ClientRules.isGameMaster()
                            || !player.getMainHandItem().is(com.ddc.registry.DDCItems.GM_WAND.get())
                            || player.isCrouching()) {
                        return net.minecraft.world.InteractionResult.PASS;
                    }
                    Minecraft.getInstance().schedule(() ->
                            Minecraft.getInstance().setScreen(
                                    com.ddc.client.screen.BlockWheel.forBlock(pos)));
                    return net.minecraft.world.InteractionResult.FAIL;
                });

        new OverlayCommand(OVERLAY).register();
        new TwitchCommand(TWITCH, VOTE, REWARDS, REWARD_ACTIONS).register();
        KeyMappingRegistry.register(SHEET_KEY);
        KeyMappingRegistry.register(PANEL_KEY);
        KeyMappingRegistry.register(WHEEL_KEY);
        KeyMappingRegistry.register(VIEW_KEY);
        KeyMappingRegistry.register(STREAMER_KEY);

        // Said once, on joining: a player standing in a world with no idea what R does will not read
        // a README, and the mod that asks them to learn a second game should say where to start.
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player ->
                player.sendSystemMessage(
                        net.minecraft.network.chat.Component.translatable("ddc.guide.open")
                                .withStyle(net.minecraft.ChatFormatting.GOLD)));

        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            RollCache.clear();
            GRADE.tick(Long.MAX_VALUE);
            ClientRules.clear();
            // Leaving a world drops the widgets and the chat: what they were watching is gone.
            OVERLAY.forgetState();
            OVERLAY.stop();
            TWITCH.close();
            REWARDS.close();
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
            while (STREAMER_KEY.consumeClick()) {
                CHARACTER_HUD.toggleStreamerMode();
            }
            while (WHEEL_KEY.consumeClick()) {
                openWheel(client);
            }
            sendPossessedAttacks(client);
            while (VIEW_KEY.consumeClick()) {
                togglePossessionView(client);
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
