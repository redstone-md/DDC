package com.ddc.neoforge;

import com.ddc.client.dice.DiceEntityRenderer;
import com.ddc.registry.DDCEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Registers the shared dice renderer on NeoForge's mod bus.
 *
 * <p>Architectury has no entity-renderer registry, so this is the NeoForge half of a hook Fabric also
 * has to write for itself. What it registers is shared: only the registration differs.
 */
// NeoForge 26 dropped the mod-bus/game-bus split, so there is no bus to name any more.
@EventBusSubscriber(modid = com.ddc.DDC.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeDiceRendering {

    private NeoForgeDiceRendering() {
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DDCEntities.DICE.get(), DiceEntityRenderer::new);
    }
}
