package com.hantokei.chronos_armory.mixin;

import com.hantokei.chronos_armory.ArmoryRuntime;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class SkillGlidingMixin {
    @Redirect(method="updateFallFlying",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;canElytraFly(Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private boolean chronos$canGlide(ItemStack stack,LivingEntity entity){return ArmoryRuntime.skillGliding(entity)||stack.canElytraFly(entity);}
    @Redirect(method="updateFallFlying",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;elytraFlightTick(Lnet/minecraft/world/entity/LivingEntity;I)Z"))
    private boolean chronos$glideTick(ItemStack stack,LivingEntity entity,int ticks){return ArmoryRuntime.skillGliding(entity)||stack.elytraFlightTick(entity,ticks);}
}
