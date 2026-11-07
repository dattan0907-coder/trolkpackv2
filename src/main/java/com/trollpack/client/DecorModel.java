package com.trollpack.client;

import com.trollpack.TrollPack;
import com.trollpack.entity.DecorEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class DecorModel extends GeoModel<DecorEntity> {
    @Override public ResourceLocation getModelResource(DecorEntity e) { return TrollPack.id("geo/entity/" + e.kind.modelId + ".geo.json"); }
    @Override public ResourceLocation getTextureResource(DecorEntity e) { return TrollPack.id("textures/entity/" + e.kind.modelId + ".png"); }
    @Override public ResourceLocation getAnimationResource(DecorEntity e) { return TrollPack.id("animations/entity/" + e.kind.modelId + ".animation.json"); }

    @Override
    public void setCustomAnimations(DecorEntity e, long id, AnimationState<DecorEntity> state) {
        if (e.kind != DecorEntity.Kind.POT) return;
        int s = e.skin(); // pot skins: base "pot", alts "pot2"/"pot3" (lr_troll_pots_parts)
        getBone("pot2").ifPresent(b -> b.setHidden(s != 1));
        getBone("pot3").ifPresent(b -> b.setHidden(s != 2));
        getBone("pot").ifPresent(b -> { b.setHidden(s != 0); b.setChildrenHidden(false); });
    }
}
