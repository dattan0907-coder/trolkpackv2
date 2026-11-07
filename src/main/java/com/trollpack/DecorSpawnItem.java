package com.trollpack;

import com.trollpack.entity.DecorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

import java.util.function.Supplier;

/** "Spawn egg" for the static props (pig, pot, totem, bed mat, throne). */
public class DecorSpawnItem extends Item {
    private final Supplier<EntityType<DecorEntity>> type;

    public DecorSpawnItem(Supplier<EntityType<DecorEntity>> type, Properties props) {
        super(props);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace() == Direction.DOWN ? Direction.DOWN : Direction.UP);
        if (ctx.getClickedFace() != Direction.UP && ctx.getClickedFace() != Direction.DOWN) pos = ctx.getClickedPos().relative(ctx.getClickedFace());
        DecorEntity e = type.get().create(level);
        if (e == null) return InteractionResult.FAIL;
        double y = ctx.getClickedFace() == Direction.UP ? ctx.getClickedPos().getY() + 1 : pos.getY();
        e.moveTo(pos.getX() + 0.5, y, pos.getZ() + 0.5, Math.round(ctx.getRotation() / 90f) * 90f, 0f);
        level.addFreshEntity(e);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
