package com.hantokei.chronos_armory;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public final class ArmoryWeaponItem extends Item {
    private final int ultimateCharge;

    public ArmoryWeaponItem(double attackDamage, double attackSpeed, int ultimateCharge) {
        super(new Item.Properties().stacksTo(1).attributes(ItemAttributeModifiers.builder()
                // Players already have 1 attack damage and 4 attacks per second.
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID,
                        attackDamage - 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID,
                        attackSpeed - 4.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build()));
        this.ultimateCharge = ultimateCharge;
    }

    public int ultimateCharge() {
        return ultimateCharge;
    }
}
