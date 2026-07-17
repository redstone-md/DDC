package com.ddc.fabric;

import com.ddc.client.DDCClient;
import com.ddc.client.dice.DiceEntityRenderer;
import com.ddc.registry.DDCEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Fabric client bootstrap. All of it is shared with NeoForge through {@link DDCClient#init()}. */
public final class DDCFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        DDCClient.init();
        // Architectury has no entity-renderer registry, so this is one of the few things each loader
        // has to say for itself. What it registers is shared.
        EntityRendererRegistry.register(DDCEntities.DICE.get(), DiceEntityRenderer::new);
        EntityRendererRegistry.register(DDCEntities.SPELL_BOLT.get(),
                com.ddc.client.spell.SpellBoltRenderer::new);
    }
}
