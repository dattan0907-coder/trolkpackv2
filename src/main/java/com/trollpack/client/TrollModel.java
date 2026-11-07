package com.trollpack.client;

import com.trollpack.TrollPack;
import com.trollpack.entity.TrollEntity;
import com.trollpack.entity.TrollVariant;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class TrollModel extends GeoModel<TrollEntity> {
    @Override public ResourceLocation getModelResource(TrollEntity e) { return TrollPack.id("geo/entity/" + e.kind.modelId + ".geo.json"); }
    @Override public ResourceLocation getTextureResource(TrollEntity e) { return TrollPack.id("textures/entity/" + e.kind.modelId + ".png"); }
    @Override public ResourceLocation getAnimationResource(TrollEntity e) { return TrollPack.id("animations/entity/" + e.kind.modelId + ".animation.json"); }

    private static final String[] LIMBS = {"left_arm", "left_forearm", "left_hand", "right_arm", "right_forearm", "right_hand",
            "left_leg", "left_shin", "left_foot", "right_leg", "right_shin", "right_foot"};

    @Override
    public void setCustomAnimations(TrollEntity e, long id, AnimationState<TrollEntity> state) {
        // head follows the look direction (ModelEngine "h_" bones)
        getBone("h_head").ifPresent(head -> {
            EntityModelData d = state.getData(DataTickets.ENTITY_MODEL_DATA);
            head.setRotX(head.getRotX() + d.headPitch() * Mth.DEG_TO_RAD);
            head.setRotY(head.getRotY() + d.netHeadYaw() * Mth.DEG_TO_RAD);
        });
        if (e.kind.isTroll()) applyVariant(e.variant(), e.spearPhase());
    }

    // ---- changepart emulation: every alt bone is hidden, then the chosen one is shown and the bone it replaces
    //      hides ITSELF only (its child bones keep rendering and animating). ----
    private void applyVariant(TrollVariant v, int spearPhase) {
        for (int s = 0; s < 3; s++) {
            hide(s + "_body"); hide(s + "_body_fem"); hide(s + "_shield"); hide(s + "_spear"); hide(s + "_spear_throw");
            hide(s + "_left_arm_armor"); hide(s + "_right_arm_armor");
            for (String l : LIMBS) hide(s + "_" + l);
            for (int h = 1; h <= 14; h++) hide(s + "_" + h);
        }
        hide("h_tusk_gold"); hide("h_tusk2_gold");

        int s = v.skin();
        replace("body", v.fem() ? s + "_body_fem" : (s > 0 ? s + "_body" : null));
        if (s > 0 && v.fem() && getBone(s + "_body_fem").isEmpty()) replace("body", s + "_body");
        for (String l : LIMBS) replace(l, s > 0 ? s + "_" + l : null);
        if (v.armorL()) replace("left_arm", s + "_left_arm_armor");
        if (v.armorR()) replace("right_arm", s + "_right_arm_armor");
        if (v.shield()) replace("left_forearm", s + "_shield");
        String hand = s > 0 ? s + "_right_hand" : null;
        if (v.spear()) {
            if (spearPhase == 0) hand = s + "_spear";
            else if (spearPhase == 1) hand = s + "_spear_throw";
        }
        replace("right_hand", hand);
        replace("h_head", s + "_" + v.head());
        replace("h_tusk", v.tusk1() ? "h_tusk_gold" : null);
        replace("h_tusk2", v.tusk2() ? "h_tusk2_gold" : null);
    }

    private void hide(String bone) { getBone(bone).ifPresent(b -> b.setHidden(true)); }

    private void replace(String base, String alt) {
        if (alt != null && getBone(alt).isPresent()) {
            getBone(alt).get().setHidden(false);
            getBone(base).ifPresent(b -> { b.setHidden(true); b.setChildrenHidden(false); });
        } else {
            getBone(base).ifPresent(b -> b.setHidden(false));
        }
    }
}
