package com.ddc.client.dice;

import com.ddc.core.dice.DiceThrow;
import com.ddc.core.dice.RollResult;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * What the renderer needs to draw a roll's dice, pulled out of the entity before drawing starts.
 *
 * <p>Minecraft 26 renders from extracted state rather than from entities, so the entity is read once
 * per frame here and the drawing never touches it again.
 */
@Environment(EnvType.CLIENT)
public class DiceRenderState extends EntityRenderState {

    /** The roll, as the server sent it. Null when this client never saw the roll's payload. */
    public RollResult result;

    /** The flight of each die, worked out from the roll's seed. */
    public List<DiceThrow> flights = List.of();

    /** How long the dice have been in the world. */
    public double seconds;
}
