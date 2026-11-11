package com.hantokei.chronos_armory;

import static com.hantokei.chronos_armory.ArmoryCombat.*;
import io.github.fatalitaya.combatnexus.combat.damage.api.*;
import io.github.fatalitaya.combatnexus.combat.damage.api.context.*;
import io.github.fatalitaya.combatnexus.combat.damage.api.damage.*;
import io.github.fatalitaya.combatnexus.combat.damage.api.enums.*;
import io.github.fatalitaya.combatnexus.combat.damage.api.event.*;
import io.github.fatalitaya.combatnexus.combat.damage.platform.api.event.DamageModuleRegisterEvent;
import io.github.fatalitaya.combatnexus.combat.api.transaction.CombatParent;
import io.github.fatalitaya.combatnexus.combat.health.api.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import java.util.*;

public final class ArmoryPassives {
    static final Map<UUID,Set<UUID>> BOOSTED=new HashMap<>();
    static int multiplier,outgoingMultiplier;
    public static void register(DamageModuleRegisterEvent e){
        multiplier=e.registerPreMultiplierBucket(id("incoming_multiplier"));outgoingMultiplier=e.registerPreMultiplierBucket(id("outgoing_multiplier"));
        for(var phase:List.of(DamagePhase.BASE_MODIFICATION,DamagePhase.CONDITIONAL_MULTI,DamagePhase.MITIGATION_SETUP,DamagePhase.FINAL_OVERRIDE))e.registerPhaseProcessor(id("weapon_"+phase.name().toLowerCase(Locale.ROOT)),new DamagePhaseProcessor(){public DamagePhase phase(){return phase;}public void apply(NativeDamageRuleContext c){modify(c,phase);}});
        e.registerSettlementListener(id("weapon_hits"),0,ArmoryPassives::settled);
    }
    static Weapon weapon(Set<io.github.fatalitaya.combatnexus.api.id.NexusId> tags,LivingEntity attacker){
        for(Weapon w:Weapon.values())if(tags.contains(id("weapon/"+w.path())))return w;
        return attacker==null?null:Weapon.of(attacker.getMainHandItem());
    }
    static boolean direct(Set<io.github.fatalitaya.combatnexus.api.id.NexusId> tags,DamageRequestKind kind){return !tags.contains(id("periodic"))&&!tags.contains(id("automatic"))&&!tags.contains(id("extra"))&&!tags.contains(id("replay"))&&kind!=DamageRequestKind.PROC&&kind!=DamageRequestKind.REFLECTED&&kind!=DamageRequestKind.DOT&&kind!=DamageRequestKind.THORNS&&kind!=DamageRequestKind.ENVIRONMENTAL;}
    static void modify(NativeDamageRuleContext c,DamagePhase phase){
        var target=c.victim();ServerPlayer p=c.logicalAttacker() instanceof ServerPlayer a?a:null;
        Weapon w=weapon(c.sourceTags(),c.requestKind()==DamageRequestKind.PRIMARY||c.requestKind()==DamageRequestKind.SWEEP?p:null);boolean direct=direct(c.sourceTags(),c.requestKind());boolean replay=c.sourceTags().contains(id("replay"));
        if(phase==DamagePhase.BASE_MODIFICATION){
            if(c.sourceTags().contains(id("true"))){float base=c.origin().initialBaseDamage();c.tryAddBaseDamage(c.getInitialChannel(),c.nativeInput().initialBaseBucket(),-base,"chronos_true_replace");c.tryAddTrueDamage(c.getInitialChannel(),base,"chronos_true");}
            if(!replay&&p!=null&&w==Weapon.ANTARES&&direct&&(!target.getActiveEffects().isEmpty()||!world(target).effects().snapshot(target.getUUID()).isEmpty()))c.tryAddBaseDamage(c.getInitialChannel(),5,"chronos_black_flame");
        }
        if(phase==DamagePhase.CONDITIONAL_MULTI&&!replay){
            int vulnerable=ArmoryEffects.level(target,"vulnerable");if(vulnerable>0)c.tryAddGlobalPreMultiplier(multiplier,0.2f*vulnerable,"chronos_vulnerable");
            if(p!=null&&holds(p,Weapon.CYANFROST_VIOLETVOLT)&&direct){
                var action=world(p).currentAction(p.getUUID()).map(a->a.id());boolean used=action.filter(a->BOOSTED.getOrDefault(p.getUUID(),Set.of()).contains(a)).isPresent();
                if(ArmoryEffects.has(p,"aurora"))c.tryAddGlobalPreMultiplier(outgoingMultiplier,0.4f,"chronos_aurora");else if(ArmoryRuntime.state(p).violet||used||c.metadata().get(VIOLET_ACTION).orElse(false))c.tryAddGlobalPreMultiplier(outgoingMultiplier,0.2f,"chronos_violet");
            }
        }
        if(phase==DamagePhase.MITIGATION_SETUP&&!replay){
            int dragon=ArmoryEffects.level(target,"dragon_blessing");
            boolean full=holds(target,Weapon.ANTARES)&&target.getHealth()==target.getMaxHealth();
            for(var channel:c.activeChannels())if(channel.id().equals(DamageChannel.PHYSICAL_ID)||channel.id().equals(DamageChannel.MAGIC_ID)){
                if(dragon>0)c.tryAddChannelMitigation(channel,dragon*0.1f,"chronos_dragon");if(full)c.tryAddChannelMitigation(channel,0.4f,"chronos_form");
            }
        }
        if(phase==DamagePhase.FINAL_OVERRIDE&&replay)c.tryOverrideFinalDamage(c.origin().initialBaseDamage(),"chronos_replay");
    }
    static void settled(DamageSettlementCallback callback){
        var s=callback.snapshot();var ctx=callback.context();LivingEntity target=ctx.target();if(target==null)return;
        ServerPlayer p=ctx.logicalAttacker() instanceof ServerPlayer a?a:null;CombatParent parent=callback.combatAuthority().orElse(null);
        double loss=s.evidence().healthLoss()+s.absorptionDamage()+s.evidence().shieldLoss();
        if(loss>0){if(target instanceof ServerPlayer tp)ArmoryRuntime.state(tp).lastHurt=now(tp);if(p!=null)ArmoryRuntime.state(p).lastDealt=now(p);}
        if(!s.sourceTags().contains(id("replay"))&&s.evidence().healthWriteObserved())ArmoryRuntime.recordLoss(target,s.evidence().healthLoss());
        if(p==null)return;
        if(s.successfulHit()&&direct(s.sourceTags(),s.requestKind())&&enemy(p,target)){
            if(holds(p,Weapon.CYANFROST_VIOLETVOLT)){
                var state=ArmoryRuntime.state(p);
                if(ArmoryEffects.has(p,"aurora")){if(now(p)>=state.lightningAt){state.lightningAt=now(p)+10;if(target.isAlive())damage(p,target,Weapon.CYANFROST_VIOLETVOLT,"extra",5,"magic",parent);}}
                else if(state.violet&&!s.metadata().get(VIOLET_ACTION).orElse(false)&&s.evidence().action().map(a->!BOOSTED.getOrDefault(p.getUUID(),Set.of()).contains(a.id())).orElse(true)){state.violet=false;s.evidence().action().ifPresent(a->BOOSTED.computeIfAbsent(p.getUUID(),k->new HashSet<>()).add(a.id()));}
            }
        }
        if(holds(p,Weapon.CYANFROST_VIOLETVOLT)&&s.evidence().healthWriteObserved()&&s.healthBefore()>0&&s.healthAfter()<=0&&!target.isAlive()&&!p.isAlliedTo(target))ArmoryRuntime.state(p).violet=true;
        Weapon w=weapon(s.sourceTags(),s.requestKind()==DamageRequestKind.PRIMARY||s.requestKind()==DamageRequestKind.SWEEP?p:null);if(w==null)return;
        boolean direct=direct(s.sourceTags(),s.requestKind()),ultimate=s.sourceTags().contains(id("ultimate"))||s.sourceTags().contains(id("fireball"));
        boolean legal=enemy(p,target)||!target.isAlive()&&!p.isAlliedTo(target)&&(target instanceof Enemy||target instanceof net.minecraft.world.entity.player.Player&&p.server.isPvpAllowed());
        if(!legal)return;
        if(direct&&!ultimate&&s.evidence().healthWriteObserved())ArmoryRuntime.addCharge(p,w,s.evidence().healthLoss());
        if(s.successfulHit()&&direct){
            if(w==Weapon.NULL&&target.isAlive()){annihilate(p,target,false,parent);if(target.isAlive())damage(p,target,w,"extra",3,List.of("physical","magic","true").get(p.getRandom().nextInt(3)),parent);}
            if(w==Weapon.ANTARES&&loss>0&&target.isAlive()){
                ArmoryEffects.mark(p,target,"black_flame",1,s.sourceTags().contains(id("fireball"))?200:120,parent);
                if(s.sourceTags().contains(id("fireball")))ArmoryEffects.mark(p,target,"dissolve",1,200,parent);
                judge(p,target,parent);
            }
            if(w==Weapon.MOON_MARROW&&night(p)){
                int next=Math.min(3,ArmoryEffects.level(target,"vulnerable")+1);potion(p,target,MobEffects.MOVEMENT_SLOWDOWN,120,next,parent);ArmoryEffects.mark(p,target,"vulnerable",next,120,parent);heal(p,p,1,parent);
            }
            if(w==Weapon.DRAGONSPINE_BREAKER){
                UUID action=s.evidence().action().map(a->a.id()).orElse(null);
                if(action==null||ArmoryRuntime.state(p).dragonActions.add(action))ArmoryEffects.dragon(p,true,parent);
            }

        }
        if(s.evidence().healthWriteObserved()&&s.healthBefore()>0&&s.healthAfter()<=0&&!target.isAlive()){
            if(direct&&!ultimate)ArmoryRuntime.addCharge(p,w,20);
            if(holds(p,Weapon.CYANFROST_VIOLETVOLT))ArmoryRuntime.state(p).violet=true;
            if(w==Weapon.MOON_MARROW&&ultimate&&direct&&night(p)){grow(p,w,0.1,10,false,weaponInstance(s,ctx,p),parent);potion(p,p,MobEffects.REGENERATION,200,2,parent);potion(p,p,MobEffects.SLOW_FALLING,200,1,parent);}
            if(w==Weapon.DRAGONSPINE_BREAKER&&target instanceof Enemy&&ctx.evidence().reactionInputs().participants().get(target.getUUID()).facts().maxHealth()>25)grow(p,w,0.1,5,true,weaponInstance(s,ctx,p),parent);
            if(w==Weapon.ANTARES)formless(p,parent);
        }
    }
    static void formless(ServerPlayer p,CombatParent parent){
        if(ArmoryEffects.ready(p,"formless_cooldown")){
                world(p).abilities().cooldown(((io.github.fatalitaya.combatnexus.combat.api.DomainCommand.AbilityCooldown)ArmoryEffects.cooldown(p,"formless_cooldown",300)).request(),parent);
                ArmoryEffects.mark(p,p,"formless",1,200,parent);
                if(!p.server.isPvpAllowed())for(var ally:p.serverLevel().players())if(ally!=p&&p.isAlliedTo(ally)&&ally.distanceToSqr(p)<=25){ArmoryEffects.mark(p,ally,"formless",1,200,parent);}
            }
    }
    public static void lethal(ServerPlayer p,LivingEntity t,CombatParent parent){
        var request=new LethalAttempt(UUID.randomUUID(),t.getUUID(),source(p,"annihilation"),new io.github.fatalitaya.combatnexus.api.id.NexusId("minecraft","player_attack"),Optional.of(p.getUUID()),false);
        if(parent==null)world(p).health().execute(request);else world(p).health().execute(request,parent);
    }
    public static void annihilate(ServerPlayer p,LivingEntity t,boolean forced,CombatParent parent){
        int enchantments=active(p).getEnchantments().size();if(!forced&&p.getRandom().nextDouble()>=Math.min(1,0.003+0.001*enchantments))return;
        if(!t.isAlive())return;if(t.getHealth()<=50)lethal(p,t,parent);else damage(p,t,Weapon.NULL,"extra",30+(t.getHealth()-50)*0.1,"true",parent);
    }
    static void judge(ServerPlayer p,LivingEntity t,CombatParent parent){ArmoryEffects.judge(source(p,"black_flame"),t,parent);}
    static UUID weaponInstance(DamageSettlementSnapshot s,DamageSettlementContext ctx,ServerPlayer p){
        return s.metadata().get(WEAPON_INSTANCE).orElseGet(()->{
            var frame=ctx.evidence().reactionInputs().participants().get(p.getUUID());
            String value=frame==null?"":frame.facts().external().find(HELD_INSTANCE).orElse("");return value.isEmpty()?null:UUID.fromString(value);
        });
    }
    static void grow(ServerPlayer p,Weapon w,double amount,double cap,boolean counter,UUID item,CombatParent parent){
        ItemStack stack=ItemStack.EMPTY;
        for(var candidate:p.getInventory().items)if(Weapon.of(candidate)==w&&Objects.equals(instance(candidate),item)){stack=candidate;break;}
        if(stack.isEmpty()&&Weapon.of(p.getOffhandItem())==w&&Objects.equals(instance(p.getOffhandItem()),item))stack=p.getOffhandItem();
        if(stack.isEmpty()||item==null)return;
        var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(counter){int count=data.getInt("chronos_hunt")+1;data.putInt("chronos_hunt",count%50);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));if(count<50)return;ArmoryEffects.mark(p,p,"hunt_armor",1,200,parent);}
        double growth=Math.min(cap,data.getDouble("chronos_growth")+amount);data.putDouble("chronos_growth",growth);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
        var modifiers=stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.EMPTY);
        modifiers=modifiers.withModifierAdded(Attributes.ATTACK_DAMAGE,new AttributeModifier(ResourceLocation.fromNamespaceAndPath(ChronosArmory.MODID,"growth"),growth,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND);stack.set(DataComponents.ATTRIBUTE_MODIFIERS,modifiers);
    }
    public static void eat(LivingEntityUseItemEvent.Finish e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!carries(p,Weapon.BIG_APPLE)||!e.getItem().is(TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath(ChronosArmory.MODID,"apples")))||!ArmoryEffects.ready(p,"apple_food"))return;
        var good=new ArrayList<>(List.of(MobEffects.MOVEMENT_SPEED,MobEffects.DIG_SPEED,MobEffects.DAMAGE_BOOST,MobEffects.JUMP,MobEffects.REGENERATION,MobEffects.DAMAGE_RESISTANCE));
        var chosen=new ArrayList<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>>();for(int i=0;i<3;i++)chosen.add(good.remove(p.getRandom().nextInt(good.size())));
        good.addAll(List.of(MobEffects.MOVEMENT_SLOWDOWN,MobEffects.DIG_SLOWDOWN,MobEffects.WEAKNESS));for(int i=0;i<2;i++)chosen.add(good.remove(p.getRandom().nextInt(good.size())));
        world(p).abilities().cooldown(((io.github.fatalitaya.combatnexus.combat.api.DomainCommand.AbilityCooldown)ArmoryEffects.cooldown(p,"apple_food",200)).request());chosen.forEach(effect->potion(p,p,effect,200,2,null));
    }
}
