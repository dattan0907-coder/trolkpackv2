package com.trollpack.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Static props: cooking pig, pot (3 skins), totem, bed mat, chief throne. Replaces the LR_troll_decor_* armor stands. */
public class DecorEntity extends Entity implements GeoEntity {
    public enum Kind {
        PIG("troll_pig"), POT("troll_pot"), TOTEM("troll_totem"), BEDMAT("troll_bedmat"), THRONE("troll_throne");
        public final String modelId;
        Kind(String id) { modelId = id; }
    }

    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(DecorEntity.class, EntityDataSerializers.INT);
    public final Kind kind;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int cooldown;

    public DecorEntity(EntityType<?> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        if (kind == Kind.POT) entityData.set(SKIN, random.nextInt(3)); // TROLL_decorpot_randomize
    }

    public int skin() { return entityData.get(SKIN); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(SKIN, 0); }
    @Override protected void readAdditionalSaveData(CompoundTag t) { entityData.set(SKIN, t.getInt("Skin")); }
    @Override protected void addAdditionalSaveData(CompoundTag t) { t.putInt("Skin", skin()); }

    @Override public boolean isPickable() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return kind == Kind.THRONE || kind == Kind.POT; }
    @Override public ItemStack getPickResult() {
        var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(getType());
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(com.trollpack.TrollPack.id(key.getPath() + "_spawn_egg"));
        return item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) { // trolldecor_remove
            ((ServerLevel) level()).sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.5, getZ(), 30, 0.25, 0.25, 0.25, 0.02);
            if (!player.getAbilities().instabuild) spawnAtLocation(getPickResult());
            discard();
        } else { // trolldecor_rotate: 90 degree steps
            setYRot(Math.round((getYRot() + 90f) / 90f) * 90f);
            yRotO = getYRot();
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide || !(src.getEntity() instanceof Player)) return false;
        if (kind == Kind.PIG && cooldown <= 0) { // TROLL_cookingpig_interact
            cooldown = 100;
            triggerAnim("action", "hit");
            spawnAtLocation(new ItemStack(Items.COOKED_PORKCHOP));
        } else if (kind == Kind.POT) {
            triggerAnim("action", "nudge");
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (cooldown > 0) cooldown--;
        if (level().isClientSide) {
            if (kind == Kind.TOTEM && tickCount % 10 == 0)
                level().addParticle(ParticleTypes.FLAME, getX(), getY() + 1.05, getZ(), 0, 0.01, 0);
        } else if (kind == Kind.POT && tickCount % 5 == 0 && cooldown <= 0
                && !level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(1.0)).isEmpty()) {
            cooldown = 20; // TROLL_decorpot_nudge
            triggerAnim("action", "nudge");
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "main", 0, s ->
                kind == Kind.PIG ? s.setAndContinue(RawAnimation.begin().thenLoop("idle")) : PlayState.STOP));
        c.add(new AnimationController<>(this, "action", 0, s -> PlayState.STOP)
                .triggerableAnim("hit", RawAnimation.begin().then("hit", Animation.LoopType.PLAY_ONCE))
                .triggerableAnim("nudge", RawAnimation.begin().then("nudge", Animation.LoopType.PLAY_ONCE)));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
