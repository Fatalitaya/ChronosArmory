package com.hantokei.chronos_armory;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ArmoryItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ChronosArmory.MODID);

    public static final DeferredItem<ArmoryWeaponItem> NULL = register("null", 9, 3.00, 750);
    public static final DeferredItem<ArmoryWeaponItem> ANTARES = register("antares", 14, 1.75, 800);
    public static final DeferredItem<ArmoryWeaponItem> MOON_MARROW = register("moon_marrow", 19, 1.20, 400);
    public static final DeferredItem<ArmoryWeaponItem> DRAGONSPINE_BREAKER = register("dragonspine_breaker", 15, 1.40, 750);
    public static final DeferredItem<ArmoryWeaponItem> BIG_APPLE = register("big_apple", 20, 0.70, 300);
    public static final DeferredItem<ArmoryWeaponItem> EVERNIGHT_STAR = register("evernight_star", 8, 1.60, 500);
    public static final DeferredItem<ArmoryWeaponItem> CYANFROST_VIOLETVOLT = register("cyanfrost_violetvolt", 10, 1.50, 600);

    private ArmoryItems() {}

    private static DeferredItem<ArmoryWeaponItem> register(String id, double damage, double speed, int charge) {
        return ITEMS.register(id, () -> new ArmoryWeaponItem(id, damage, speed, charge));
    }

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ChronosArmory.MODID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ARMORY_TAB =
            TABS.register("weapons", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.chronos_armory"))
                    .icon(() -> NULL.get().getDefaultInstance())
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .displayItems((parameters, output) -> {
                        output.accept(NULL.get());
                        output.accept(ANTARES.get());
                        output.accept(MOON_MARROW.get());
                        output.accept(DRAGONSPINE_BREAKER.get());
                        output.accept(BIG_APPLE.get());
                        output.accept(EVERNIGHT_STAR.get());
                        output.accept(CYANFROST_VIOLETVOLT.get());
                    })
                    .build());
}
