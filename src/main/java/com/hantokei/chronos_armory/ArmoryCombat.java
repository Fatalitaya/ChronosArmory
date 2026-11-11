package com.hantokei.chronos_armory;

import io.github.fatalitaya.combatnexus.api.id.NexusId;
import io.github.fatalitaya.combatnexus.combat.api.*;
import io.github.fatalitaya.combatnexus.combat.api.transaction.CombatParent;
import io.github.fatalitaya.combatnexus.combat.damage.api.damage.*;
import io.github.fatalitaya.combatnexus.combat.healing.api.HealingRequest;
import io.github.fatalitaya.combatnexus.effect.api.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import java.util.*;

public final class ArmoryCombat {
    static final ThreadLocal<io.github.fatalitaya.combatnexus.combat.ability.api.AbilityExecution> EXECUTION=new ThreadLocal<>();
    static final DamageMetadataKey<Boolean> VIOLET_ACTION=DamageMetadataKey.booleanKey(id("violet_action"));
    static final DamageMetadataKey<UUID> WEAPON_INSTANCE=DamageMetadataKey.uuidKey(id("weapon_instance"));
    static final io.github.fatalitaya.combatnexus.combat.api.input.FactKey<String> HELD_INSTANCE=new io.github.fatalitaya.combatnexus.combat.api.input.FactKey<>(id("held_instance"),com.mojang.serialization.Codec.STRING);
    static <T> T executing(io.github.fatalitaya.combatnexus.combat.ability.api.AbilityExecution execution,java.util.function.Supplier<T> work){
        var previous=EXECUTION.get();EXECUTION.set(execution);try{return work.get();}finally{if(previous==null)EXECUTION.remove();else EXECUTION.set(previous);}
    }
    public static NexusId id(String path) { return new NexusId(ChronosArmory.MODID,path); }
    public static CombatWorld world(ServerLevel level) { return CombatAccess.find((WorldLookup)level).orElseThrow(); }
    public static CombatWorld world(LivingEntity entity) { return world((ServerLevel)entity.level()); }
    public static long now(LivingEntity entity) { return world(entity).time().now(entity.getUUID(),id("clock")); }
    public static ServerPlayer player(UUID id) { var s=ServerLifecycleHooks.getCurrentServer();return s==null?null:s.getPlayerList().getPlayer(id); }
    public static CombatSource source(LivingEntity owner,String name) { return new CombatSource(id(name),Optional.of(owner.getUUID()),Optional.empty()); }
    public static ItemStack active(ServerPlayer p) {
        if (Weapon.of(p.getMainHandItem())!=null) return p.getMainHandItem();
        return Weapon.of(p.getOffhandItem())==Weapon.EVERNIGHT_STAR?p.getOffhandItem():ItemStack.EMPTY;
    }
    public static boolean holds(LivingEntity p,Weapon w) { return Weapon.of(p.getMainHandItem())==w||Weapon.of(p.getOffhandItem())==w; }
    public static boolean carries(ServerPlayer p,Weapon w) { for(var stack:p.getInventory().items)if(Weapon.of(stack)==w)return true;return holds(p,w); }
    public static UUID instance(ItemStack stack) {
        var n=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        return n.hasUUID("chronos_instance")?n.getUUID("chronos_instance"):null;
    }
    public static void identify(ItemStack stack) {
        if(Weapon.of(stack)!=null&&instance(stack)==null)CustomData.update(DataComponents.CUSTOM_DATA,stack,n->n.putUUID("chronos_instance",UUID.randomUUID()));
    }
    public static boolean night(ServerPlayer p) { long t=Math.floorMod(p.level().getDayTime(),24000);return p.level().dimension()!=net.minecraft.world.level.Level.OVERWORLD||(t>=13000&&t<23000); }
    public static boolean enemy(ServerPlayer p,LivingEntity t) {
        if(t==p||!t.isAlive()||!t.isAttackable()||t.isSpectator()||p.isAlliedTo(t))return false;
        if(t instanceof Player other)return !other.isCreative()&&p.server.isPvpAllowed()&&p.canHarmPlayer(other);
        if(t instanceof TamableAnimal pet&&(pet.isOwnedBy(p)||pet.getOwner()!=null&&p.isAlliedTo(pet.getOwner())))return false;
        if(t instanceof Enemy)return true;
        return t instanceof Mob mob&&mob.getTarget()!=null&&(mob.getTarget()==p||p.isAlliedTo(mob.getTarget()));
    }
    public static boolean visible(ServerLevel level,Vec3 from,LivingEntity target) {
        return level.clip(new ClipContext(from,target.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,target)).getType()==HitResult.Type.MISS;
    }
    public static List<LivingEntity> area(ServerPlayer p,Vec3 center,double radius) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),t->enemy(p,t)&&t.getBoundingBox().distanceToSqr(center)<=radius*radius&&visible(p.serverLevel(),center,t))
            .stream().sorted(Comparator.comparingDouble((LivingEntity t)->t.distanceToSqr(center)).thenComparing(Entity::getUUID)).toList();
    }
    public static List<LivingEntity> fan(ServerPlayer p,double radius) {
        Vec3 f=p.getLookAngle().multiply(1,0,1).normalize();
        return area(p,p.position(),radius).stream().filter(t->t.getBoundingBox().maxY>=p.getY()-2&&t.getBoundingBox().minY<=p.getY()+2)
            .filter(t->{Vec3 d=t.position().subtract(p.position()).multiply(1,0,1);return d.lengthSqr()<0.01||d.normalize().dot(f)>=0.5;}).toList();
    }
    public static Vec3 aim(ServerPlayer p,double distance) {
        Vec3 end=p.getEyePosition().add(p.getLookAngle().scale(distance));
        return p.level().clip(new ClipContext(p.getEyePosition(),end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getLocation();
    }
    public static Vec3 dash(ServerPlayer p,Vec3 direction,double distance) {
        Vec3 start=p.position(),last=start,step=direction.normalize().scale(0.2);
        for(int i=0;i<(int)(distance/0.2);i++) {
            Vec3 next=last.add(step);var box=p.getBoundingBox().move(next.subtract(start));
            if(!p.serverLevel().hasChunkAt(net.minecraft.core.BlockPos.containing(next))||!p.level().noCollision(p,box))break;
            last=next;
        }
        Vec3 delta=last.subtract(start);
        var movement=new io.github.fatalitaya.combatnexus.combat.action.api.EntityCommand.Displace(UUID.randomUUID(),p.getUUID(),source(p,"dash"),new io.github.fatalitaya.combatnexus.api.geometry.Vector3(delta.x,delta.y,delta.z),io.github.fatalitaya.combatnexus.combat.action.api.EntityCommand.Displace.Mode.POSITION,io.github.fatalitaya.combatnexus.combat.action.api.EntityCommand.Displace.Origin.VOLUNTARY);
        if(EXECUTION.get()!=null)EXECUTION.get().submit(new DomainCommand.Entity(movement));else world(p).entities().submit(movement);
        p.connection.teleport(p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot());return p.position();
    }
    public static DamageResult damage(ServerPlayer p,LivingEntity t,Weapon w,String skill,double amount,String channel,CombatParent parent) {
        return damage(p,t,w,skill,amount,channel,parent,instance(active(p)));
    }
    public static DamageResult damage(ServerPlayer p,LivingEntity t,Weapon w,String skill,double amount,String channel,CombatParent parent,UUID item) {
        return damage(p,t,w,skill,amount,channel,parent,item,false);
    }
    static DamageResult damage(ServerPlayer p,LivingEntity t,Weapon w,String skill,double amount,String channel,CombatParent parent,UUID item,boolean violetAction) {
        var b=DamageRequest.builder(p.serverLevel(),t,DamageSourceDescriptor.of(new NexusId("minecraft",channel.equals("magic")?"indirect_magic":"player_attack")),(float)amount)
            .logicalAttacker(p).directEntity(p).effectOwner(p).equipmentOwner(p).kind(DamageRequestKind.CUSTOM)
            .sourceTag(id("weapon/"+w.path())).sourceTag(id(skill)).cooldownPolicy(DamageCooldownPolicy.INDEPENDENT);
        if(channel.equals("true"))b.sourceTag(id("true"));
        if(item!=null)b.metadata(WEAPON_INSTANCE,item);
        if(violetAction)b.metadata(VIOLET_ACTION,true);
        if(parent!=null)b.combatParent(parent);
        var request=b.build();
        if(parent==null&&EXECUTION.get()!=null)return EXECUTION.get().submit(new NativeDamageCommand(UUID.randomUUID(),request)).result(DamageResult.class).orElseThrow();
        return world(p).damage(request);
    }
    public static void heal(ServerPlayer p,LivingEntity target,double amount,CombatParent parent) {
        var r=new HealingRequest(UUID.randomUUID(),target.getUUID(),source(p,"healing"),HealingRequest.Reason.SKILL,amount);
        if(parent==null)world(p).healing().submit(r);else world(p).healing().submit(r,parent);
    }
    public static void potion(ServerPlayer p,LivingEntity t,Holder<MobEffect> effect,int ticks,int level,CombatParent parent) {
        var key=BuiltInRegistries.MOB_EFFECT.getKey(effect.value());
        var command=new NativeEffectCommand(UUID.randomUUID(),t.getUUID(),source(p,"potion"),new NexusId(key.getNamespace(),key.getPath()),NativeEffectCommand.Operation.REFRESH,ticks,level-1,false,true,true);
        if(parent==null)world(p).effects().nativeEffect(command);else world(p).effects().nativeEffect(command,parent);
    }
    private ArmoryCombat() {}
}
