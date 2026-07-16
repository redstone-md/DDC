package com.ddc.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Opens the one door the renderer keeps shut.
 *
 * <p>Minecraft can already grade the whole screen -- it is what spectating a creeper does -- but the
 * method that turns an effect on is private, and the public ways in pick the effect from an entity
 * type. There is no vanilla road from "this player rolled a 20" to "grade the screen gold", so this
 * is the smallest possible one: no behaviour changed, nothing injected into, just the existing method
 * made callable.
 *
 * <p>Deliberately the only mixin in the mod. Everything else has an API to go through, and a mixin is
 * a promise to break on an update that an API is not.
 */
@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {

    @Invoker("setPostEffect")
    void ddc$setPostEffect(Identifier effect);
}
