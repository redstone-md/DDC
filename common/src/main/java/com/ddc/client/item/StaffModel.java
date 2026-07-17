package com.ddc.client.item;

import com.ddc.DDC;
import com.ddc.item.AnimatedStaffItem;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;

/**
 * Where the staff's model, skin and animations live.
 *
 * <p>All three are DDC's own files. GeckoLib is MIT, so the engine can be borrowed; its assets could
 * be too, and are not -- they are its author's work about its author's mod, and a staff of ours should
 * look like ours.
 */
@Environment(EnvType.CLIENT)
public class StaffModel extends GeoModel<AnimatedStaffItem> {

    // GeckoLib 5 keys its cache by the bare name: it scans assets/ddc/geckolib/models and
    // assets/ddc/geckolib/animations and strips the folder, the .geo/.animation and the .json. So
    // both of these are just the file's name, and the texture keeps its own path under textures/.
    private static final Identifier MODEL = DDC.id("staff");
    private static final Identifier TEXTURE = DDC.id("item/staff_geo");
    private static final Identifier ANIMATIONS = DDC.id("staff");

    @Override
    public Identifier getModelResource(GeoRenderState state) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(AnimatedStaffItem staff) {
        return ANIMATIONS;
    }
}
