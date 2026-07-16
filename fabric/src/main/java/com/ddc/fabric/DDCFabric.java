package com.ddc.fabric;

import com.ddc.DDC;
import net.fabricmc.api.ModInitializer;

/** Fabric bootstrap. Everything it does lives in {@link DDC#init()}, shared with NeoForge. */
public final class DDCFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        DDC.init();
    }
}
