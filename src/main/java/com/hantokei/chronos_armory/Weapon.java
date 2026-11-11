package com.hantokei.chronos_armory;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

public enum Weapon {
    NULL(750,300), ANTARES(800,300), MOON_MARROW(400,300), DRAGONSPINE_BREAKER(750,300),
    BIG_APPLE(300,400), EVERNIGHT_STAR(500,100), CYANFROST_VIOLETVOLT(600,400);
    public final int charge, cooldown;
    Weapon(int charge,int cooldown) { this.charge=charge;this.cooldown=cooldown; }
    public String path() { return name().toLowerCase(java.util.Locale.ROOT); }
    public static Weapon of(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmoryWeaponItem)) return null;
        return valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toUpperCase(java.util.Locale.ROOT));
    }
}
