package com.ddc.dice;

import com.ddc.registry.DDCEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The dice of a roll, standing where they were thrown.
 *
 * <p>ARCHITECTURE.md draws the dice from a client-side render layer with no entity behind them.
 * Minecraft 26 has no such layer: its renderer works by extracting render state, and neither Fabric's
 * API nor Architectury exposes a hook to draw arbitrary geometry into the world any more. An entity
 * is what is left, and it is a cheap one: one per roll rather than one per die, it saves nothing to
 * disk, it has no AI, no collision and no gravity, and it removes itself after four seconds.
 *
 * <p>It carries only the roll's seed. The faces came to the client already, in the
 * {@code ddc:dice_result} payload, and looking them up from there keeps one answer to what was
 * rolled: an entity that carried its own copy of the faces could disagree with the roll log beside
 * it.
 */
public class DiceEntity extends Entity {

    /** How long the dice stand there, in ticks: the flight and the linger, and then they are gone. */
    private static final int LIFETIME_TICKS = 80;

    private static final EntityDataAccessor<Long> SEED =
            SynchedEntityData.defineId(DiceEntity.class, EntityDataSerializers.LONG);

    private int age;

    public DiceEntity(EntityType<? extends DiceEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Puts a roll's dice into the world at a point. */
    public static DiceEntity spawn(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.phys.Vec3 at, long seed) {
        DiceEntity dice = new DiceEntity(DDCEntities.DICE.get(), level);
        dice.setPos(at.x, at.y, at.z);
        dice.setSeed(seed);
        level.addFreshEntity(dice);
        return dice;
    }

    public long seed() {
        return entityData.get(SEED);
    }

    private void setSeed(long seed) {
        entityData.set(SEED, seed);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SEED, 0L);
    }

    @Override
    public void tick() {
        // No super.tick(): there is nothing to move, fall, or collide with. The dice are drawn from
        // the seed, and this entity is only where and when.
        age++;
        if (level() instanceof net.minecraft.server.level.ServerLevel && age > LIFETIME_TICKS) {
            discard();
        }
    }

    /**
     * Dice cannot be hurt. They are scenery for four seconds, and a stray arrow must not be able to
     * make one flash and vanish.
     */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    /** How long these dice have been in the world, in seconds. What the flight is measured against. */
    public double seconds(float partialTick) {
        return (age + partialTick) / 20.0;
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag input) {
        // Nothing: the dice never survive a save. They are a four-second event.
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag output) {
    }
}
