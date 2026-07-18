package com.ddc.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Opens the one door the renderer keeps shut: {@code loadEffect} is private, and there is no public
 * road from "this player rolled a 20" to "grade the screen gold". It injects nothing and changes no
 * behaviour -- it makes an existing method callable.
 */
@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {

    @Invoker("loadEffect")
    void ddc$loadEffect(ResourceLocation effect);
}
