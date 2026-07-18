package com.ddc.client.spell;

import com.ddc.spell.SpellBoltEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the bolt, which is to say: draws nothing.
 *
 * <p>The bolt is light, and light in this game is particles -- the entity lays them along its own path
 * as it flies. A renderer that draws nothing still has to exist: an entity without one crashes on
 * spawn.
 */
@Environment(EnvType.CLIENT)
public class SpellBoltRenderer extends EntityRenderer<SpellBoltEntity> {

    private static final ResourceLocation TEXTURE = com.ddc.DDC.id("textures/entity/dice.png");

    public SpellBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SpellBoltEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(SpellBoltEntity entity, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffer, int light) {
        // Deliberately nothing: see the class note.
    }
}
