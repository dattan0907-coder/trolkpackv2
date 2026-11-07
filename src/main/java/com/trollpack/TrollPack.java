package com.trollpack;

import com.trollpack.entity.DecorEntity;
import com.trollpack.entity.SpearProjectile;
import com.trollpack.entity.TrollEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Mod entry point + all registries (kept in one file on purpose: the pack is small). */
@Mod(TrollPack.ID)
public class TrollPack {
    public static final String ID = "trollpack";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);

    // ---- sounds (names match assets/trollpack/sounds.json) ----
    public static final List<Supplier<SoundEvent>> SWING = new ArrayList<>();
    public static final List<Supplier<SoundEvent>> IMPACT = new ArrayList<>();
    public static final Supplier<SoundEvent> JUMP = sound("jump");
    public static final Supplier<SoundEvent> HEAL = sound("heal");
    public static final Supplier<SoundEvent> GREETINGS = sound("greetings");
    public static final Supplier<SoundEvent> SPELL_ATTACK = sound("spell_attack");

    static {
        for (int i = 1; i <= 5; i++) SWING.add(sound("swing" + i));
        for (int i = 1; i <= 4; i++) IMPACT.add(sound("impact" + i));
    }

    private static Supplier<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id(name)));
    }

    // ---- entities ----
    // sizes come from the "hitbox" bone of each ModelEngine blueprint (tools/model_meta.json)
    public static final DeferredHolder<EntityType<?>, EntityType<TrollEntity>> TROLL = troll("troll", TrollEntity.Kind.NEUTRAL, 1.5f, 2.69f);
    public static final DeferredHolder<EntityType<?>, EntityType<TrollEntity>> TROLL_AGGRO = troll("troll_aggro", TrollEntity.Kind.AGGRO, 1.5f, 2.69f);
    public static final DeferredHolder<EntityType<?>, EntityType<TrollEntity>> TROLL_NPC = troll("troll_npc", TrollEntity.Kind.NPC, 1.5f, 2.69f);
    public static final DeferredHolder<EntityType<?>, EntityType<TrollEntity>> TROLL_ELITE = troll("troll_elite", TrollEntity.Kind.ELITE, 1.5f, 2.69f);
    public static final DeferredHolder<EntityType<?>, EntityType<TrollEntity>> TROLL_VOODOO = troll("troll_voodoo", TrollEntity.Kind.VOODOO, 1.5f, 2.69f);
    public static final DeferredHolder<EntityType<?>, EntityType<TrollEntity>> TROLL_CHIEF = troll("troll_chief", TrollEntity.Kind.CHIEF, 1.38f, 1.75f);

    public static final DeferredHolder<EntityType<?>, EntityType<DecorEntity>> DECOR_PIG = decor("troll_pig", DecorEntity.Kind.PIG, 1.0f, 1.0f);
    public static final DeferredHolder<EntityType<?>, EntityType<DecorEntity>> DECOR_POT = decor("troll_pot", DecorEntity.Kind.POT, 0.88f, 1.12f);
    public static final DeferredHolder<EntityType<?>, EntityType<DecorEntity>> DECOR_TOTEM = decor("troll_totem", DecorEntity.Kind.TOTEM, 1.0f, 1.56f);
    public static final DeferredHolder<EntityType<?>, EntityType<DecorEntity>> DECOR_BEDMAT = decor("troll_bedmat", DecorEntity.Kind.BEDMAT, 1.5f, 0.2f);
    public static final DeferredHolder<EntityType<?>, EntityType<DecorEntity>> DECOR_THRONE = decor("troll_throne", DecorEntity.Kind.THRONE, 2.19f, 3.75f);

    public static final DeferredHolder<EntityType<?>, EntityType<SpearProjectile>> SPEAR = ENTITIES.register("troll_spear_projectile",
            () -> EntityType.Builder.<SpearProjectile>of(SpearProjectile::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(4).updateInterval(10).build("troll_spear_projectile"));
    public static final DeferredHolder<EntityType<?>, EntityType<SpearProjectile>> SKULL_MISSILE = ENTITIES.register("voodoo_missile",
            () -> EntityType.Builder.<SpearProjectile>of((t, l) -> new SpearProjectile(t, l, true), MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(4).updateInterval(2).noSave().build("voodoo_missile"));

    private static DeferredHolder<EntityType<?>, EntityType<TrollEntity>> troll(String name, TrollEntity.Kind kind, float w, float h) {
        return ENTITIES.register(name, () -> EntityType.Builder.<TrollEntity>of((t, l) -> new TrollEntity(t, l, kind), MobCategory.CREATURE)
                .sized(w, h).clientTrackingRange(10).build(name));
    }

    private static DeferredHolder<EntityType<?>, EntityType<DecorEntity>> decor(String name, DecorEntity.Kind kind, float w, float h) {
        return ENTITIES.register(name, () -> EntityType.Builder.<DecorEntity>of((t, l) -> new DecorEntity(t, l, kind), MobCategory.MISC)
                .sized(w, h).clientTrackingRange(8).build(name));
    }

    // ---- items (models: assets/trollpack/models/item/<name>.json, converted from the pack's custom_model_data models) ----
    public static final DeferredItem<Item> TROLL_SPEAR = ITEMS.register("troll_spear",
            () -> new SwordItem(Tiers.IRON, new Item.Properties().attributes(SwordItem.createAttributes(Tiers.IRON, 4, -2.6f))));
    public static final DeferredItem<Item> TROLL_KOPESH = ITEMS.register("troll_kopesh",
            () -> new SwordItem(Tiers.IRON, new Item.Properties().attributes(SwordItem.createAttributes(Tiers.IRON, 5, -2.4f))));
    public static final DeferredItem<Item> TROLL_SHIELD = ITEMS.register("troll_shield",
            () -> new ShieldItem(new Item.Properties().durability(336)));
    public static final DeferredItem<Item> TROLL_MASK = ITEMS.register("troll_mask", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> TROLL_CHIEF_HELM = ITEMS.register("troll_chief_helm", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> TROLL_ELITE_HELM = ITEMS.register("troll_elite_helm", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> TROLL_VOODOO_STAFF = ITEMS.register("troll_voodoo_staff",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> VOODOO_SKULL = ITEMS.register("voodoo_skull", () -> new Item(new Item.Properties()));

    private static void egg(DeferredHolder<EntityType<?>, ? extends EntityType<? extends net.minecraft.world.entity.Mob>> h,
                            String name, int c1, int c2) {
        ITEMS.register(name + "_spawn_egg", () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(
                (Supplier) h, c1, c2, new Item.Properties()));
    }

    static {
        egg((DeferredHolder) TROLL, "troll", 0x3f7f6f, 0x8a3b2e);
        egg((DeferredHolder) TROLL_AGGRO, "troll_aggro", 0x3f7f6f, 0xb02020);
        egg((DeferredHolder) TROLL_NPC, "troll_npc", 0x3f7f6f, 0xd0d0d0);
        egg((DeferredHolder) TROLL_ELITE, "troll_elite", 0x5a3a2a, 0xc8a24a);
        egg((DeferredHolder) TROLL_VOODOO, "troll_voodoo", 0x2a2a4a, 0x40d0c0);
        egg((DeferredHolder) TROLL_CHIEF, "troll_chief", 0x7a2a2a, 0xe0c040);
        for (var d : new DeferredHolder[]{DECOR_PIG, DECOR_POT, DECOR_TOTEM, DECOR_BEDMAT, DECOR_THRONE}) {
            String n = d.getId().getPath();
            ITEMS.register(n + "_spawn_egg", () -> new DecorSpawnItem((Supplier) d, new Item.Properties()));
        }
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.trollpack"))
            .icon(() -> TROLL_SPEAR.get().getDefaultInstance())
            .displayItems((p, out) -> ITEMS.getEntries().forEach(i -> out.accept(i.get())))
            .build());

    public TrollPack(IEventBus bus) {
        ENTITIES.register(bus);
        ITEMS.register(bus);
        SOUNDS.register(bus);
        TABS.register(bus);
        bus.addListener(TrollPack::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(TROLL.get(), TrollEntity.attributes(250, 0.28, 32, 10).build());
        e.put(TROLL_AGGRO.get(), TrollEntity.attributes(100, 0.28, 32, 10).build());
        e.put(TROLL_NPC.get(), TrollEntity.attributes(250, 0.28, 32, 0).build());
        e.put(TROLL_ELITE.get(), TrollEntity.attributes(750, 0.30, 48, 15).build());
        e.put(TROLL_VOODOO.get(), TrollEntity.attributes(750, 0.25, 48, 8).build());
        e.put(TROLL_CHIEF.get(), TrollEntity.attributes(750, 0.0001, 48, 0).build());
    }
}
