package com.trollpack.entity;

import com.trollpack.TrollPack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;

/** Thrown troll spear, and (missile = true) the homing voodoo skull. */
public class SpearProjectile extends ThrowableItemProjectile {
    private final boolean missile;
    private LivingEntity target;
    private int life;

    public SpearProjectile(EntityType<? extends SpearProjectile> type, Level level) {
        this(type, level, false);
    }

    public SpearProjectile(EntityType<? extends SpearProjectile> type, Level level, boolean missile) {
        super(type, level);
        this.missile = missile;
        if (missile) setNoGravity(true);
    }

    public void setTarget(LivingEntity t) { this.target = t; }

    @Override
    protected Item getDefaultItem() {
        return missile ? TrollPack.VOODOO_SKULL.get() : TrollPack.TROLL_SPEAR.get();
    }

    @Override
    protected double getDefaultGravity() { return missile ? 0.0 : 0.05; }

    @Override
    public void tick() {
        super.tick();
        if (++life > 200) { discard(); return; }
        if (missile && target != null && target.isAlive() && !level().isClientSide) {
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(position()).normalize();
            setDeltaMovement(getDeltaMovement().scale(0.85).add(to.scale(0.12)));
            hasImpulse = true;
        }
        if (missile && level().isClientSide)
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY(), getZ(), 0, 0, 0);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level().isClientSide) return;
        if (hit.getEntity() == getOwner() || hit.getEntity() instanceof TrollEntity) return;
        float dmg = missile ? 8f : 9f;
        hit.getEntity().hurt(damageSources().thrown(this, getOwner()), dmg);
        discard();
    }

    @Override
    protected void onHit(HitResult hit) {
        if (hit.getType() == HitResult.Type.ENTITY && ((EntityHitResult) hit).getEntity() instanceof TrollEntity) return;
        super.onHit(hit);
        if (!level().isClientSide && hit.getType() == HitResult.Type.BLOCK) discard();
    }
}
