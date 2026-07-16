package com.ddc.fabric;

import com.ddc.client.DDCClient;
import net.fabricmc.api.ClientModInitializer;

/** Fabric client bootstrap. All of it is shared with NeoForge through {@link DDCClient#init()}. */
public final class DDCFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        DDCClient.init();
    }
}
