package com.ddc.item;

import com.ddc.character.CharacterService;
import com.ddc.rules.DataRegistry;
import com.ddc.rules.Spell;
import com.ddc.spell.SpellService;
import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The staff, animated.
 *
 * <p>A staff that never moved was the plainest thing in the mod: you clicked, a number appeared, and
 * the object in your hand did not acknowledge that magic had happened. Now the stone turns and
 * breathes while it is held, and a cast kicks the staff back and flares the stone -- recoil is how a
 * hand tells you it did something.
 *
 * <p>Animated with GeckoLib, which is MIT and therefore actually usable: DDC ships its own model, its
 * own animations and its own texture, and borrows only the engine that plays them. That is the whole
 * reason this dependency exists and the reason it could not be the other mod's.
 *
 * <p>The cast animation is triggered from the server, because the server is what knows a spell was
 * cast. A client that animated on click would flare the stone for a spell that failed -- no slot, out
 * of range, not prepared -- and lie about it.
 */
public class AnimatedStaffItem extends SpellFocusItem implements GeoItem {

    /** What the controller is called. GeckoLib wants a name; nothing else uses it. */
    private static final String CONTROLLER = "staff";

    /** The trigger the server pulls when a spell actually goes off. */
    public static final String CAST_TRIGGER = "cast";

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.staff.idle");
    private static final RawAnimation CAST = RawAnimation.begin().thenPlay("animation.staff.cast");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public AnimatedStaffItem(Properties properties, Power power, CharacterService characters,
            DataRegistry<Spell> spells, SpellService casting) {
        super(properties, power, characters, spells, casting);
        // Without this, the server's triggerAnim reaches nothing: GeckoLib keeps its own list of the
        // animatables that may be triggered from across the wire, and says "unregistered synced
        // animatable" to a log nobody reads while the staff quietly never animates.
        GeoItem.registerSyncedAnimatable(this);
    }

    /**
     * Casts, and animates only if the cast happened.
     *
     * <p>The result comes back from the focus, which asked the spell service, which applied the rules.
     * A staff that flared on every click would be a staff that lied about failing.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionResult result = super.use(level, player, hand);
        if (result.consumesAction() && player instanceof ServerPlayer caster
                && level instanceof ServerLevel server) {
            triggerAnim(caster, GeoItem.getOrAssignId(player.getItemInHand(hand), server),
                    CONTROLLER, CAST_TRIGGER);
        }
        return result;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<AnimatedStaffItem>(CONTROLLER, 4, state -> {
            // Idle is the resting state: the controller falls back to it the moment a trigger ends,
            // so nothing has to say "stop casting".
            state.setAndContinue(IDLE);
            return PlayState.CONTINUE;
        }).triggerableAnim(CAST_TRIGGER, CAST));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /**
     * Hands the client its renderer.
     *
     * <p>Reached for by name so this class stays on both sides: the renderer is client-only code, and
     * an import of it here would follow the item onto a dedicated server and crash it on load.
     */
    @Override
    public void createGeoRenderer(java.util.function.Consumer<com.geckolib.animatable.client.GeoRenderProvider> consumer) {
        consumer.accept(new com.geckolib.animatable.client.GeoRenderProvider() {
            private Object renderer;

            @Override
            public com.geckolib.renderer.GeoItemRenderer<?> getGeoItemRenderer() {
                if (renderer == null) {
                    renderer = new com.ddc.client.item.StaffRenderer();
                }
                return (com.geckolib.renderer.GeoItemRenderer<?>) renderer;
            }
        });
    }
}
