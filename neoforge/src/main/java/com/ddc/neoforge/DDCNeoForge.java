package com.ddc.neoforge;

import com.ddc.DDC;
import com.ddc.client.DDCClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** NeoForge bootstrap. The shared work lives in {@link DDC#init()}, the same call Fabric makes. */
@Mod(DDC.MOD_ID)
public final class DDCNeoForge {

    public DDCNeoForge(IEventBus modBus, Dist dist) {
        DDC.init();
        // Client-only classes must stay off the dedicated server's class path, so the client
        // bootstrap is wired up only when this side actually has a client.
        if (dist.isClient()) {
            modBus.addListener(DDCNeoForge::onClientSetup);
        }
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(DDCClient::init);
    }
}
