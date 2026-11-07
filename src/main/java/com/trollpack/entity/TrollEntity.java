package com.trollpack.entity;

import com.trollpack.TrollPack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * One class for every humanoid of the pack (troll / aggro troll / NPC troll / elite / voodoo / chief).
 * Replaces the MythicMobs skill files with plain Java AI. See README for what was simplified.
 */
public class TrollEntity extends PathfinderMob implements GeoEntity {

    public enum Kind {
        NEUTRAL("troll", 50), AGGRO("troll", 50), NPC("troll", 50),
        ELITE("troll_elite", 40), VOODOO("troll_voodoo", 40), CHIEF("troll_chief", 20);

        public final String modelId;
        public final int deathDelay;

        Kind(String modelId, int deathDelay) {
            this.modelId = modelId;
            this.deathDelay = deathDelay;
        }

        public boolean isTroll() { return modelId.equals("troll"); }
        public boolean invulnerable() { return this == NPC || this == CHIEF; }
    }

    public static final int POSE_NONE = 0, POSE_SIT = 1, POSE_SLEEP = 2, POSE_DANCE = 3;

    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(TrollEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POSE_STATE = SynchedEntityData.defineId(TrollEntity.class, EntityDataSerializers.INT);
    /** 0 = spear in hand, 1 = winding up a throw, 2 = hand empty (spear thrown) */
    private static final EntityDataAccessor<Integer> SPEAR_PHASE = SynchedEntityData.defineId(TrollEntity.class, EntityDataSerializers.INT);

    public final Kind kind;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private record Scheduled(int[] ticks, Runnable r) {}
    private final List<Scheduled> scheduled = new ArrayList<>();
    private int attackCooldown, throwCooldown, leapCooldown, spellCooldown, poseTicks, leapAirTicks;
    private boolean leaping;

    public TrollEntity(EntityType<? extends TrollEntity> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        this.setPersistenceRequired();
        if (kind == Kind.CHIEF) this.setNoGravity(false);
    }

    public static AttributeSupplier.Builder attributes(double health, double speed, double follow, double damage) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, health)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.FOLLOW_RANGE, follow)
                .add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    // ------------------------------------------------------------------ data / variants
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(VARIANT, 0);
        b.define(POSE_STATE, POSE_NONE);
        b.define(SPEAR_PHASE, 0);
    }

    public TrollVariant variant() { return TrollVariant.unpack(entityData.get(VARIANT)); }
    public int pose() { return entityData.get(POSE_STATE); }
    public int spearPhase() { return entityData.get(SPEAR_PHASE); }
    private void setPose(int p) { entityData.set(POSE_STATE, p); }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", entityData.get(VARIANT));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Variant")) entityData.set(VARIANT, tag.getInt("Variant"));
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance diff, MobSpawnType type, @Nullable SpawnGroupData data) {
        if (kind.isTroll()) entityData.set(VARIANT, TrollVariant.random(random).pack());
        return super.finalizeSpawn(level, diff, type, data);
    }

    // ------------------------------------------------------------------ AI
    @Override
    protected void registerGoals() {
        if (kind == Kind.CHIEF) {
            goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 12f));
            return;
        }
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new FreezeGoal());
        if (kind == Kind.NPC) {
            goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12f));
            return;
        }
        goalSelector.addGoal(1, new AttackGoal());
        goalSelector.addGoal(2, new ThrowSpearGoal());
        goalSelector.addGoal(3, new LeapGoal());
        goalSelector.addGoal(4, new SpellGoal());
        goalSelector.addGoal(5, new MoveTowardsTargetGoal(this, 1.0, 32f));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, TrollEntity.class).setAlertOthers(TrollEntity.class));
        if (kind == Kind.AGGRO) {
            targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        }
    }

    /** Stops movement while sitting / sleeping / dancing / winding up. */
    private class FreezeGoal extends Goal {
        FreezeGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
        @Override public boolean canUse() { return pose() != POSE_NONE; }
        @Override public boolean canContinueToUse() { return pose() != POSE_NONE; }
        @Override public void start() { getNavigation().stop(); }
    }

    private boolean busy() { return pose() != POSE_NONE || !onGround() && !leaping || isDeadOrDying(); }

    private double reach() { return kind == Kind.ELITE ? 3.8 : kind == Kind.VOODOO ? 3.2 : 3.4; }

    private class AttackGoal extends Goal {
        AttackGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { LivingEntity t = getTarget(); return t != null && t.isAlive(); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            getLookControl().setLookAt(t, 30f, 30f);
            double d = Math.sqrt(distanceToSqr(t.getX(), t.getY(), t.getZ()));
            if (d > reach() - 0.5 || kind == Kind.VOODOO && d > 6) {
                // voodoo keeps its distance for spells (5..16), closes in only if the player is far away
                if (kind != Kind.VOODOO || d > 16) getNavigation().moveTo(t, kind == Kind.ELITE ? 1.25 : 1.15);
                else if (d < 5) getNavigation().stop();
            } else {
                getNavigation().stop();
            }
            if (attackCooldown <= 0 && d <= reach() && !isInWater() && !leaping && spearPhase() == 0) startMelee(t);
        }
    }

    private void startMelee(LivingEntity t) {
        attackCooldown = 20;
        swingSound();
        // small lunge forward (Mythic: lunge{v=.5})
        Vec3 f = Vec3.directionFromRotation(0, getYRot()).scale(0.35);
        setDeltaMovement(getDeltaMovement().add(f.x, 0, f.z));
        switch (kind) {
            case ELITE -> {
                boolean kick = random.nextBoolean();
                triggerAnim("action", kick ? "melee_2_1" : "melee_1_1");
                schedule(kick ? 8 : 6, () -> hitArea(15, 2.0, kick));
                if (!kick && random.nextFloat() < 0.5f) {
                    schedule(12, () -> { triggerAnim("action", "melee_1_2"); swingSound(); });
                    schedule(18, () -> hitArea(15, 2.0, false));
                }
            }
            case VOODOO -> {
                triggerAnim("action", "melee_1");
                schedule(6, () -> hitArea(8, 1.6, false));
            }
            default -> {
                boolean spear = variant().spear() && spearPhase() == 0;
                String a1 = spear ? "melee_1" : "melee_empty_1";
                String a2 = spear ? "melee_2" : "melee_empty_2";
                triggerAnim("action", a1);
                schedule(3, () -> hitArea(10, 1.8, false));
                if (random.nextFloat() < 0.75f) {
                    schedule(10, () -> { if (isAlive()) { triggerAnim("action", a2); swingSound(); } });
                    schedule(13, () -> hitArea(10, 1.8, true)); // second swing throws the target upwards
                    attackCooldown = 30;
                }
            }
        }
    }

    /** Damage everything that is not a troll in front of the mob (Mythic: totem{...} at the weapon hand). */
    private void hitArea(float dmg, double radius, boolean knockUp) {
        if (level().isClientSide || !isAlive()) return;
        Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
        Vec3 c = position().add(fwd.scale(1.8)).add(0, 1.0, 0);
        boolean hit = false;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius, 1.2, radius),
                e -> e != this && !(e instanceof TrollEntity) && e.isAlive())) {
            if (e.hurt(damageSources().mobAttack(this), dmg)) {
                hit = true;
                e.push(fwd.x * 0.6, knockUp ? 0.9 : 0.25, fwd.z * 0.6);
                e.hurtMarked = true;
            }
        }
        if (hit) impactSound();
    }

    private class ThrowSpearGoal extends Goal {
        @Override public boolean canUse() {
            LivingEntity t = getTarget();
            return kind.isTroll() && variant().spear() && spearPhase() == 0 && throwCooldown <= 0 && t != null
                    && onGround() && !isInWater() && distanceToSqr(t) > 64 && distanceToSqr(t) < 28 * 28 && hasLineOfSight(t);
        }
        @Override public boolean canContinueToUse() { return false; }
        @Override public void start() {
            LivingEntity t = getTarget();
            throwCooldown = 100;
            attackCooldown = 40;
            getNavigation().stop();
            addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false));
            entityData.set(SPEAR_PHASE, 1);
            triggerAnim("action", "throw_spear");
            schedule(18, () -> {
                if (!isAlive() || t == null) { entityData.set(SPEAR_PHASE, 0); return; }
                swingSound();
                SpearProjectile p = new SpearProjectile(TrollPack.SPEAR.get(), level());
                p.setOwner(TrollEntity.this);
                p.setPos(getX(), getEyeY() - 0.2, getZ());
                double dx = t.getX() - getX(), dz = t.getZ() - getZ();
                double dy = t.getY(0.5) - p.getY();
                double dist = Math.sqrt(dx * dx + dz * dz);
                p.shoot(dx, dy + dist * 0.12, dz, 1.7f, 1.0f);
                level().addFreshEntity(p);
                entityData.set(SPEAR_PHASE, 2);
            });
            schedule(30, () -> entityData.set(SPEAR_PHASE, 0));
        }
    }

    private class LeapGoal extends Goal {
        @Override public boolean canUse() {
            LivingEntity t = getTarget();
            if (t == null || leapCooldown > 0 || !onGround() || isInWater() || leaping || spearPhase() != 0) return false;
            if (!(kind == Kind.ELITE || kind.isTroll() && variant().spear())) return false;
            double d = Math.sqrt(distanceToSqr(t));
            return d > 3 && d < 12 && t.getY() - getY() < 1;
        }
        @Override public boolean canContinueToUse() { return false; }
        @Override public void start() {
            LivingEntity t = getTarget();
            leapCooldown = 100;
            attackCooldown = 20;
            getNavigation().stop();
            triggerAnim("action", kind == Kind.ELITE ? "leap" : "jump_attack_leap");
            level().playSound(null, blockPosition(), TrollPack.JUMP.get(), SoundSource.HOSTILE, 1f, 1f);
            schedule(10, () -> {
                if (!isAlive() || t == null) return;
                Vec3 dir = new Vec3(t.getX() - getX(), 0, t.getZ() - getZ()).normalize();
                double d = Math.min(12, Math.sqrt(distanceToSqr(t)));
                setDeltaMovement(dir.x * Math.min(1.1, d * 0.12), 0.55, dir.z * Math.min(1.1, d * 0.12));
                hasImpulse = true;
                leaping = true;
                leapAirTicks = 0;
            });
        }
    }

    private class SpellGoal extends Goal {
        @Override public boolean canUse() {
            LivingEntity t = getTarget();
            if (kind != Kind.VOODOO || t == null || spellCooldown > 0 || isInWater() || attackCooldown > 0 && attackCooldown > 10) return false;
            double d = Math.sqrt(distanceToSqr(t));
            return d >= 5 && d <= 16 && hasLineOfSight(t);
        }
        @Override public boolean canContinueToUse() { return false; }
        @Override public void start() {
            LivingEntity t = getTarget();
            spellCooldown = 100;
            attackCooldown = 50;
            getNavigation().stop();
            triggerAnim("action", "spell_1");
            schedule(35, () -> {
                if (!isAlive() || t == null || !t.isAlive()) return;
                level().playSound(null, blockPosition(), TrollPack.SPELL_ATTACK.get(), SoundSource.HOSTILE, 1f, 1f);
                SpearProjectile m = new SpearProjectile(TrollPack.SKULL_MISSILE.get(), level(), true);
                m.setOwner(TrollEntity.this);
                m.setPos(getX(), getEyeY() - 0.3, getZ());
                m.setTarget(t);
                level().addFreshEntity(m);
            });
        }
    }

    // ------------------------------------------------------------------ tick
    private void schedule(int ticks, Runnable r) { scheduled.add(new Scheduled(new int[]{ticks}, r)); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (attackCooldown > 0) attackCooldown--;
        if (throwCooldown > 0) throwCooldown--;
        if (leapCooldown > 0) leapCooldown--;
        if (spellCooldown > 0) spellCooldown--;
        for (int i = scheduled.size() - 1; i >= 0; i--) {
            Scheduled s = scheduled.get(i);
            if (--s.ticks()[0] <= 0) { scheduled.remove(i); s.r().run(); }
        }
        if (leaping) {
            leapAirTicks++;
            if (leapAirTicks > 4 && onGround()) landed();
            else if (leapAirTicks > 80) leaping = false;
        }
        if (pose() != POSE_NONE) {
            if (--poseTicks <= 0 || getTarget() != null) setPose(POSE_NONE);
        } else if (kind.isTroll() && kind != Kind.NPC && getTarget() == null && onGround() && !isInWater() && tickCount % 20 == 0) {
            // sit_toggle (25 % every 6 s) / sleep_toggle (only at night)
            boolean night = level().getDayTime() % 24000L > 13000L;
            if (night && random.nextFloat() < 0.04f) { setPose(POSE_SLEEP); poseTicks = 600 + random.nextInt(600); }
            else if (random.nextFloat() < 0.015f) { setPose(POSE_SIT); poseTicks = 300 + random.nextInt(600); }
        }
    }

    private void landed() {
        leaping = false;
        triggerAnim("action", kind == Kind.ELITE ? "land" : "jump_attack_hit");
        Vec3 c = position();
        boolean hit = false;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(2.5, 1.5, 2.5),
                e -> e != this && !(e instanceof TrollEntity) && e.isAlive())) {
            if (e.hurt(damageSources().mobAttack(this), kind == Kind.ELITE ? 15 : 10)) hit = true;
        }
        if (hit) impactSound();
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        boolean r = super.hurt(src, amount);
        if (r && pose() != POSE_NONE) setPose(POSE_NONE); // getup_damage
        return r;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource src) {
        if (kind.invulnerable() && !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        return super.isInvulnerableTo(src);
    }

    @Override
    public boolean isPushable() { return kind != Kind.VOODOO && kind != Kind.NPC && super.isPushable(); }

    // ------------------------------------------------------------------ interaction
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level().isClientSide) return InteractionResult.sidedSuccess(true);
        switch (kind) {
            case ELITE -> {
                if (held.is(Items.GOLDEN_APPLE) && pose() == POSE_NONE) {
                    if (!player.getAbilities().instabuild) held.shrink(1);
                    getLookControl().setLookAt(player);
                    ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, getX(), getY() + 2.5, getZ(), 5, 0.3, 0.3, 0.3, 0);
                    setPose(POSE_DANCE);
                    poseTicks = 60;
                    return InteractionResult.CONSUME;
                }
            }
            case CHIEF -> { triggerAnim("action", "hand_out"); return InteractionResult.CONSUME; }
            case VOODOO -> {
                triggerAnim("action", "greet");
                level().playSound(null, blockPosition(), TrollPack.GREETINGS.get(), SoundSource.NEUTRAL, 1f, 1f);
                return InteractionResult.CONSUME;
            }
            case NPC -> {
                level().playSound(null, blockPosition(), TrollPack.GREETINGS.get(), SoundSource.NEUTRAL, 1f, 1f);
                return InteractionResult.CONSUME;
            }
            default -> { }
        }
        return super.mobInteract(player, hand);
    }

    // ------------------------------------------------------------------ sounds / death / loot (Mythic: Silent: true)
    private void swingSound() {
        SoundEvent s = TrollPack.SWING.get(random.nextInt(TrollPack.SWING.size())).get();
        level().playSound(null, blockPosition(), s, SoundSource.HOSTILE, 1f, 1f);
    }

    private void impactSound() {
        SoundEvent s = TrollPack.IMPACT.get(random.nextInt(TrollPack.IMPACT.size())).get();
        level().playSound(null, blockPosition(), s, SoundSource.HOSTILE, 1f, 1f);
    }

    @Override protected SoundEvent getHurtSound(DamageSource s) { return null; }
    @Override protected SoundEvent getDeathSound() { return null; }
    @Override protected void playStepSound(net.minecraft.core.BlockPos p, net.minecraft.world.level.block.state.BlockState s) { }

    @Override
    protected void tickDeath() {
        ++deathTime;
        if (deathTime >= kind.deathDelay && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            ((ServerLevel) level()).sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 100, 0.5, 0.5, 0.5, 0.05);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource src, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, src, recentlyHit);
        if (kind.isTroll()) {
            TrollVariant v = variant();
            if (v.shield() && random.nextBoolean()) spawnAtLocation(TrollPack.TROLL_SHIELD.get());
            if (random.nextBoolean()) spawnAtLocation(TrollPack.TROLL_MASK.get());
            if (v.spear() && random.nextBoolean()) spawnAtLocation(TrollPack.TROLL_SPEAR.get());
        } else if (kind == Kind.VOODOO && random.nextBoolean()) {
            spawnAtLocation(TrollPack.TROLL_VOODOO_STAFF.get());
        }
    }

    // ------------------------------------------------------------------ animation
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (isDeadOrDying()) return state.setAndContinue(RawAnimation.begin().thenPlayAndHold("death"));
            if (kind == Kind.CHIEF) return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
            switch (pose()) {
                case POSE_SIT -> { return state.setAndContinue(RawAnimation.begin().then("sit", Animation.LoopType.PLAY_ONCE).thenLoop("sit_idle")); }
                case POSE_SLEEP -> { return state.setAndContinue(RawAnimation.begin().then("sleep_laydown", Animation.LoopType.PLAY_ONCE).thenLoop("sleep_idle")); }
                case POSE_DANCE -> { return state.setAndContinue(RawAnimation.begin().thenLoop("dance")); }
                default -> { }
            }
            if (kind.isTroll() && isInWater()) return state.setAndContinue(RawAnimation.begin().thenLoop("swim"));
            if (kind == Kind.ELITE && !onGround() && !leaping) return state.setAndContinue(RawAnimation.begin().thenLoop("in_air"));
            if (state.isMoving()) {
                boolean run = isAggressive() || getTarget() != null;
                return state.setAndContinue(RawAnimation.begin().thenLoop(run && kind != Kind.VOODOO ? "run" : "walk"));
            }
            return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
        }));

        AnimationController<TrollEntity> action = new AnimationController<>(this, "action", 2, state -> PlayState.STOP);
        for (String n : new String[]{"melee_1", "melee_2", "melee_empty_1", "melee_empty_2", "throw_spear", "jump_attack_leap",
                "jump_attack_hit", "melee_1_1", "melee_1_2", "melee_2_1", "leap", "land", "spell_1", "greet", "hand_out"}) {
            action.triggerableAnim(n, RawAnimation.begin().then(n, Animation.LoopType.PLAY_ONCE));
        }
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
