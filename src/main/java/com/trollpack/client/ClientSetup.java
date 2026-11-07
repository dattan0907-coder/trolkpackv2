package com.trollpack.client;

import com.trollpack.TrollPack;
import com.trollpack.entity.DecorEntity;
import com.trollpack.entity.TrollEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

@EventBusSubscriber(modid = TrollPack.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        for (var t : new net.neoforged.neoforge.registries.DeferredHolder[]{TrollPack.TROLL, TrollPack.TROLL_AGGRO, TrollPack.TROLL_NPC,
                TrollPack.TROLL_ELITE, TrollPack.TROLL_VOODOO, TrollPack.TROLL_CHIEF}) {
            boolean glow = t == TrollPack.TROLL_VOODOO; // only the voodoo troll ships a _glowmask texture
            e.registerEntityRenderer((net.minecraft.world.entity.EntityType<TrollEntity>) t.get(), c -> new TrollRenderer(c, glow));
        }
        for (var t : new net.neoforged.neoforge.registries.DeferredHolder[]{TrollPack.DECOR_PIG, TrollPack.DECOR_POT, TrollPack.DECOR_TOTEM,
                TrollPack.DECOR_BEDMAT, TrollPack.DECOR_THRONE}) {
            e.registerEntityRenderer((net.minecraft.world.entity.EntityType<DecorEntity>) t.get(), DecorRenderer::new);
        }
        e.registerEntityRenderer(TrollPack.SPEAR.get(), c -> new ThrownItemRenderer<>(c, 1.5f, true));
        e.registerEntityRenderer(TrollPack.SKULL_MISSILE.get(), c -> new ThrownItemRenderer<>(c, 1.0f, true));
    }

    public static class TrollRenderer extends GeoEntityRenderer<TrollEntity> {
        public TrollRenderer(EntityRendererProvider.Context ctx, boolean glow) {
            super(ctx, new TrollModel());
            this.shadowRadius = 0.8f;
            if (glow) addRenderLayer(new AutoGlowingGeoLayer<>(this)); // uses troll_voodoo_glowmask.png (from the pack's _e texture)
        }
        @Override protected float getDeathMaxRotation(TrollEntity e) { return 0f; } // the model has its own death animation
    }

    public static class DecorRenderer extends GeoEntityRenderer<DecorEntity> {
        public DecorRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new DecorModel());
            this.shadowRadius = 0.3f;
        }
    }
}
