package com.ddc.network;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;

/**
 * Registers the mod's payload types.
 *
 * <p>Architectury splits this by physical side: the sending side registers the type, and the
 * receiving side registers a receiver, which registers the type for it. So a dedicated server
 * registers S2C types here, while a client registers them by installing its receivers in
 * {@code DDCClient}. Doing both on one side would register the same type twice.
 */
public final class DDCNetwork {

    private DDCNetwork() {
    }

    /** Called from the shared bootstrap on both loaders. */
    public static void register() {
        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(DiceResultPayload.TYPE, DiceResultPayload.STREAM_CODEC);
            NetworkManager.registerS2CPayloadType(CharacterSheetPayload.TYPE, CharacterSheetPayload.STREAM_CODEC);
            NetworkManager.registerS2CPayloadType(NarrationPayload.TYPE, NarrationPayload.STREAM_CODEC);
            NetworkManager.registerS2CPayloadType(RulesPayload.TYPE, RulesPayload.STREAM_CODEC);
        }
    }
}
