package com.ddc.client.item;

import com.ddc.item.AnimatedStaffItem;
import com.geckolib.renderer.GeoItemRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Draws the staff in a hand, in a menu, and on the ground.
 *
 * <p>Nothing but a model reference: the engine does the rest, which is the entire reason for taking
 * a dependency rather than writing keyframe interpolation for the second time in one mod.
 */
@Environment(EnvType.CLIENT)
public class StaffRenderer extends GeoItemRenderer<AnimatedStaffItem> {

    public StaffRenderer() {
        super(new StaffModel());
    }
}
