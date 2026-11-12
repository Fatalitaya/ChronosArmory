package com.hantokei.chronos_armory;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public final class ArmoryWeaponItem extends Item implements software.bernie.geckolib.animatable.GeoItem {
    private final int ultimateCharge;
    private final String assetName;
    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);
    public String assetName() { return assetName; }
    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        // Legacy exports contain no weapon animations. Use the model bind pose.
    }
    @Override
    public void createGeoRenderer(java.util.function.Consumer<software.bernie.geckolib.animatable.client.GeoRenderProvider> consumer) {
        consumer.accept(new software.bernie.geckolib.animatable.client.GeoRenderProvider() {
            private ArmoryWeaponRenderer renderer;
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (renderer == null) renderer = new ArmoryWeaponRenderer(assetName.equals("moon_marrow_scythe"));
                return renderer;
            }
        });
    }


    public ArmoryWeaponItem(String id, double attackDamage, double attackSpeed, int ultimateCharge) {
        super(new Item.Properties().stacksTo(1).attributes(ItemAttributeModifiers.builder()
                // Players already have 1 attack damage and 4 attacks per second.
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID,
                        attackDamage - 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID,
                        attackSpeed - 4.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build()));
        this.ultimateCharge = ultimateCharge;
        this.assetName = switch (id) {
            case "null" -> "null_katana";
            case "antares" -> "antares_rapier";
            case "moon_marrow" -> "moon_marrow_scythe";
            case "dragonspine_breaker" -> "azure_mountains_masher_sword";
            case "big_apple" -> "great_apple_heavy_axe";
            case "evernight_star" -> "perpetual_nightstar_trident";
            case "cyanfrost_violetvolt" -> "cyanfrost_violetvolt_katana";
            default -> throw new IllegalArgumentException("Unknown weapon: " + id);
        };
    }

    public int ultimateCharge() {
        return ultimateCharge;
    }
}
