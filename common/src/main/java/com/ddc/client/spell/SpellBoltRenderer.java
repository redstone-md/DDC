package com.ddc.client.spell;

import com.ddc.spell.SpellBoltEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Draws the bolt, which is to say: draws nothing.
 *
 * <p>The bolt is light, and light in this game is particles. It had a faceted solid inside it and a
 * player called it what it was -- a pebble with a colour on. The entity is now a position that
 * particles are laid along, which is the same thing every bright moving thing in Minecraft is.
 *
 * <p>A renderer that draws nothing still has to exist: an entity without one is a crash on spawn.
 */
@Environment(EnvType.CLIENT)
public class SpellBoltRenderer extends EntityRenderer<SpellBoltEntity, EntityRenderState> {

    public SpellBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void submit(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
            CameraRenderState camera) {
        // Deliberately nothing. See the class note.
    }
}
