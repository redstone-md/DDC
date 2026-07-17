package com.ddc.spell;

import com.ddc.registry.DDCEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A spell in flight: the thing between saying a word and something catching fire.
 *
 * <p>Casting was instant. Damage landed on the same tick as the click and the only sign anything had
 * happened was a number in chat, which reads as a spreadsheet rather than as magic. A bolt takes a
 * moment to cross a room, and the room gets to watch it.
 *
 * <p><strong>It carries no rules.</strong> The d20 was rolled, the save was made and the damage was
 * applied before this existed; the bolt is a picture of a decision already taken. That is the honest
 * difference from a mod like Iron's Spells, where the projectile <em>is</em> the rule and aiming is
 * the skill. DDC is a tabletop game: the dice decide, and the bolt shows the table what they decided.
 * A bolt that could miss would be a bolt arguing with a roll that had already hit.
 *
 * <p>Which is also why it has no collision. It flies to what the spell was aimed at, and it gets
 * there, because it is already true that the spell did.
 */
public class SpellBoltEntity extends Entity {

    /** The colour, synced because the client draws a bolt it did not roll. */
    private static final EntityDataAccessor<Integer> COLOUR =
            SynchedEntityData.defineId(SpellBoltEntity.class, EntityDataSerializers.INT);

    /** Blocks per tick. Fast enough to read as a bolt, slow enough to see leave the staff. */
    private static final double SPEED = 1.6;

    /** How long a bolt may fly before it gives up, in ticks: a spell is not a missile. */
    private static final int LIFETIME = 60;

    /** How close counts as arrived. */
    private static final double ARRIVED = 0.8;

    private Vec3 destination = Vec3.ZERO;
    private int age;

    public SpellBoltEntity(EntityType<? extends SpellBoltEntity> type, Level level) {
        super(type, level);
        // Nothing about this entity is worth saving: a bolt outliving a restart would arrive at a
        // fight that finished last week.
        setNoGravity(true);
        noPhysics = true;
    }

    /** A bolt from a caster to whatever they cast at. */
    public static SpellBoltEntity between(ServerLevel level, LivingEntity caster, LivingEntity target,
            int colour) {
        SpellBoltEntity bolt = new SpellBoltEntity(DDCEntities.SPELL_BOLT.get(), level);
        bolt.setPos(caster.getX(), caster.getEyeY() - 0.2, caster.getZ());
        bolt.destination = target.getEyePosition().add(0, -0.3, 0);
        bolt.entityData.set(COLOUR, colour);
        bolt.setDeltaMovement(bolt.destination.subtract(bolt.position()).normalize().scale(SPEED));
        return bolt;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(COLOUR, 0xC9973F);
    }

    /** The colour the client draws it in. */
    public int colour() {
        return this.entityData.get(COLOUR);
    }

    @Override
    public void tick() {
        super.tick();
        setPos(position().add(getDeltaMovement()));

        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0.01, 0.01, 0.01, 0.0);
            if (++age > LIFETIME || position().distanceTo(destination) < ARRIVED) {
                discard();
            }
        }
    }

    /**
     * A bolt cannot be hurt.
     *
     * <p>It is a picture of a spell, not a thing in the world: an arrow that could shoot down a
     * fireball would be a rule nobody wrote, and this has no rules at all.
     */
    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source,
            float amount) {
        return false;
    }

    /** Bolts are not saved: see the constructor. */
    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
    }
}
