package com.hantokei.chronos_armory;

import static com.hantokei.chronos_armory.ArmoryCombat.*;
import io.github.fatalitaya.combatnexus.combat.ability.api.*;
import io.github.fatalitaya.combatnexus.combat.api.source.*;
import io.github.fatalitaya.combatnexus.combat.api.transaction.CombatOperation;
import io.github.fatalitaya.combatnexus.combat.platform.api.CombatRegisterEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class ArmoryAbilities {
    public static void register(CombatRegisterEvent event) {
        var r=event.registrar();
        r.facts(HELD_INSTANCE,entity->{var instance=instance(entity.getMainHandItem());return instance==null?"":instance.toString();});
        r.abilityExecutor(id("weapon_skill"),new AbilityExecutor(new AbilityExecutorSpec(Set.of(AbilityTarget.Kind.SELF),EnumSet.allOf(CombatOperation.class),1),ArmoryAbilities::available,ArmoryAbilities::execute));
        for(Weapon w:Weapon.values())for(boolean ultimate:new boolean[]{false,true}) {
            var key=id(w.path()+(ultimate?"/ultimate":"/skill"));
            r.definitions().ability(key,new AbilityDefinition(key,"","",Set.of(AbilityTarget.Kind.SELF),id("weapon_skill"),
                new AbilityParameters(Map.of("ultimate",ultimate?1d:0d),Map.of("weapon",id(w.path()))),ultimate?0:w.cooldown,Optional.empty(),0,
                AbilityDefinition.CooldownOwner.PLAYER_SKILL,w==Weapon.CYANFROST_VIOLETVOLT&&!ultimate?AbilityDefinition.CooldownStart.EXECUTION_END:AbilityDefinition.CooldownStart.ADMISSION,
                AbilityDefinition.DeathCooldown.RETAIN,AbilityTrigger.ACTIVE));
        }
        r.sourceProvider(id("weapons"),0,input->{
            ServerPlayer p=player(input.entity());if(p==null)return List.of();
            var offers=new ArrayList<SourceProvider.Offer>();
            for(var stack:List.of(p.getMainHandItem(),p.getOffhandItem())) {
                Weapon w=Weapon.of(stack);UUID instance=instance(stack);if(w==null||instance==null)continue;
                var source=new SourceInstance(instance,id("weapons"),Optional.of(p.getUUID()),stack==p.getMainHandItem()?"mainhand":"offhand");
                offers.add(new SourceProvider.Offer(source,new SourcePlan(id(w.path()),Map.of(),Map.of(),Map.of(),Map.of(id("skill"),id(w.path()+"/skill"),id("ultimate"),id(w.path()+"/ultimate")),false)));
            }
            return offers;
        });
        r.reactAbility(id("aurora_charge"),0,EnumSet.allOf(CombatOperation.class),callback->{
            var f=callback.result();if(f.kind()!=AbilityFact.Kind.RELEASED||!f.ability().path().endsWith("/ultimate")||f.ability().path().startsWith("cyanfrost_violetvolt"))return;
            ServerPlayer p=player(f.caster().entity());if(p==null)return;
            for(ServerPlayer ally:p.serverLevel().players())if(ally!=p&&p.isAlliedTo(ally)&&ally.distanceToSqr(p)<=400&&holds(ally,Weapon.CYANFROST_VIOLETVOLT))ArmoryRuntime.addCharge(ally,Weapon.CYANFROST_VIOLETVOLT,200);
        });
        ArmoryEffects.register(r);
    }
    static Weapon weapon(AbilityInput input) { return Weapon.valueOf(input.definition().parameters().id("weapon").path().toUpperCase(Locale.ROOT)); }
    static boolean ultimate(AbilityInput input) { return input.definition().parameters().number("ultimate")==1; }
    static boolean available(AbilityInput in) {
        ServerPlayer p=player(in.actor().entity());if(p==null||!p.isAlive())return false;
        Weapon w=weapon(in);ItemStack stack=active(p);
        if(Weapon.of(stack)!=w||!Objects.equals(instance(stack),in.request().grant().source().instance()))return false;
        if(w==Weapon.MOON_MARROW&&!night(p))return false;
        if(ultimate(in)&&world(p).abilities().execution(in.request().requestId()).isEmpty()&&ArmoryRuntime.charge(p,w)<w.charge)return false;
        if(w==Weapon.EVERNIGHT_STAR&&ArmoryRuntime.arrowInFlight(p,instance(stack)))return false;
        if(w==Weapon.ANTARES&&!ultimate(in)&&area(p,p.position(),8).isEmpty())return false;
        if(w==Weapon.EVERNIGHT_STAR&&ultimate(in)&&ArmoryRuntime.starTargets(p).isEmpty())return false;
        if(w==Weapon.BIG_APPLE&&!ultimate(in)&&!p.onGround())return false;
        if(w==Weapon.MOON_MARROW&&!ultimate(in))return !p.isPassenger()&&!p.isInWater()&&!p.hasEffect(MobEffects.LEVITATION)&&p.level().noCollision(p,p.getBoundingBox().expandTowards(0,1,0));
        return true;
    }
    public static void input(ServerPlayer p,int key) {
        if(!p.isAlive()||p.isSpectator())return;
        ItemStack stack=active(p);Weapon w=Weapon.of(stack);if(w==null)return;
        var state=ArmoryRuntime.state(p);long now=now(p);
        if(key==2) {
            if(w==Weapon.EVERNIGHT_STAR&&state.charging>=0) {
                long elapsed=now-state.charging;state.charging=-1;
                if(elapsed>=2)cast(p,w,false,Math.min(1,elapsed/20d));
            }
            return;
        }
        if(key==0&&w==Weapon.EVERNIGHT_STAR) { if(state.charging<0&&!ArmoryRuntime.arrowInFlight(p,instance(stack))) {state.charging=now;state.chargingItem=instance(stack);}return; }
        if(w==Weapon.CYANFROST_VIOLETVOLT&&state.reinput!=null) {
            var current=world(p).abilities().execution(state.reinput);
            if(current.isPresent()&&current.get().waiting()==AbilitySnapshot.Waiting.INPUT) {
                world(p).abilities().reinput(new AbilityReinput(UUID.randomUUID(),p.getUUID(),state.reinput,new AbilityTarget.Self(),AbilityParameters.EMPTY));return;
            }
            state.reinput=null;
        }
        boolean ultimate=key==1;
        if(w==Weapon.CYANFROST_VIOLETVOLT&&chargeBelow(p,w))ultimate=false;
        cast(p,w,ultimate,1);
    }
    static boolean chargeBelow(ServerPlayer p,Weapon w){return ArmoryRuntime.charge(p,w)<w.charge;}
    static void cast(ServerPlayer p,Weapon w,boolean ult,double q) {
        var service=world(p).abilities();var item=instance(active(p));
        var grant=service.grants(p.getUUID()).stream().filter(g->g.source().instance().equals(item)&&g.ability().equals(id(w.path()+(ult?"/ultimate":"/skill")))).findFirst();
        if(grant.isEmpty())return;
        var result=service.execute(new AbilityRequest(UUID.randomUUID(),grant.get(),new AbilityTarget.Self(),new AbilityParameters(Map.of("charge",q),Map.of())));
        if(w==Weapon.CYANFROST_VIOLETVOLT&&!ult)result.execution().filter(s->s.waiting()==AbilitySnapshot.Waiting.INPUT).ifPresent(s->ArmoryRuntime.state(p).reinput=s.execution());
    }
    static AbilityStepResult execute(AbilityExecution e) {return executing(e,()->executeSkill(e));}
    static AbilityStepResult executeSkill(AbilityExecution e) {
        ServerPlayer p=player(e.input().actor().entity());Weapon w=weapon(e.input());boolean ult=ultimate(e.input());
        var state=ArmoryRuntime.state(p);boolean first=e.invocation()==AbilityExecution.Invocation.FIRST;
        if(first&&ult)ArmoryRuntime.addCharge(p,w,-w.charge);
        if(first)state.attackDamage=p.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if(first)ArmoryEffects.remove(p,"formless");
        if(w==Weapon.NULL) {
            if(ult) {
                Vec3 center;
                if(first) {
                    center=aim(p,16);LivingEntity lock=ArmoryRuntime.rayTarget(p,p.getEyePosition(),center,0);
                    if(lock!=null){center=lock.getBoundingBox().getCenter();ArmoryPassives.annihilate(p,lock,true,null);}
                } else center=new Vec3(e.state().number("x"),e.state().number("y"),e.state().number("z"));
                Vec3 fixed=center;e.attackAction(()->area(p,fixed,2.5).forEach(t->damage(p,t,w,"ultimate",state.attackDamage,"physical",null)));
                e.released();if(e.step()<5)return new AbilityStepResult.Delay(new AbilityContinuation(2,new AbilityParameters(Map.of("x",center.x,"y",center.y,"z",center.z),Map.of())));
            } else {
                if(first) {
                    var start=p.position();var target=ArmoryRuntime.rayTarget(p,p.getEyePosition(),aim(p,16),p.getBbWidth()/2);
                    double range=target==null?16:Math.max(0,p.distanceTo(target)-(p.getBbWidth()+target.getBbWidth())/2-0.1);
                    dash(p,p.getLookAngle(),range);e.released();
                    if(target!=null) {e.attackAction(()->damage(p,target,w,"skill",state.attackDamage,"physical",null));state.secondTarget=target.getUUID();return new AbilityStepResult.Delay(new AbilityContinuation(2,AbilityParameters.EMPTY));}
                } else {var t=p.serverLevel().getEntity(state.secondTarget);if(t instanceof LivingEntity living&&enemy(p,living)&&p.distanceToSqr(living)<=9)e.attackAction(()->damage(p,living,w,"skill",state.attackDamage,"physical",null));}
            }
        } else if(w==Weapon.ANTARES) {
            if(ult){Vec3 from=p.position(),to=dash(p,p.getLookAngle(),8);e.attackAction(()->ArmoryRuntime.pathTargets(p,from,to,0.5).forEach(t->damage(p,t,w,"ultimate",15,"magic",null)));ArmoryRuntime.fireballs(p);}
            else for(var t:area(p,p.position(),8)){ArmoryEffects.forcedSlow(p,t);double history=ArmoryRuntime.history(t);if(history>0)damage(p,t,w,"replay",history*0.5,"true",null);}
            e.released();
        } else if(w==Weapon.MOON_MARROW) {
            if(ult){int[] count={0};e.attackAction(()->{for(var t:area(p,p.position(),4))if(damage(p,t,w,"ultimate",19,"physical",null).settlement().map(s->s.successfulHit()).orElse(false))count[0]++;});if(count[0]>0){potion(p,p,MobEffects.DAMAGE_BOOST,200,Math.min(5,count[0]),null);potion(p,p,MobEffects.DAMAGE_RESISTANCE,200,Math.min(4,count[0]),null);}}
            else {double y=p.getY();dash(p,new Vec3(0,1,0),25);if(p.getY()-y>=1){state.gliding=true;p.startFallFlying();ArmoryRuntime.addCharge(p,w,200);}}
            e.released();
        } else if(w==Weapon.DRAGONSPINE_BREAKER) {
            if(ult){ArmoryEffects.dragon(p,false);}
            else {int[] count={0};e.attackAction(()->{for(var t:fan(p,6)){var result=damage(p,t,w,"skill",20,"physical",null);if(result.settlement().map(s->s.successfulHit()).orElse(false)){count[0]++;ArmoryEffects.mark(p,t,"armor_break",4,60);}}});if(count[0]>=5)world(p).abilities().cooldown(new AbilityCooldownCommand(UUID.randomUUID(),p.getUUID(),source(p,"refresh"),new AbilityCooldownSlot.Skill(id(w.path()+"/skill")),AbilityCooldownCommand.Operation.CLEAR,0));}
            e.released();
        } else if(w==Weapon.BIG_APPLE) {
            if(ult)e.attackAction(()->{for(var t:fan(p,5)){ArmoryEffects.mark(p,t,"armor_break",2,160);damage(p,t,w,"ultimate",40,"physical",null);}});
            else ArmoryRuntime.field(p);
            e.released();
        } else if(w==Weapon.EVERNIGHT_STAR) {
            double q=e.input().request().input().numbers().getOrDefault("charge",1d);ArmoryRuntime.star(p,ult,q);e.released();
        } else if(w==Weapon.CYANFROST_VIOLETVOLT) {
            if(ult)ArmoryEffects.mark(p,p,"aurora",1,300);
            else {Vec3 from=p.position(),to=dash(p,p.getLookAngle(),6);e.attackAction(()->{for(var t:ArmoryRuntime.pathTargets(p,from,to,1.5)){damage(p,t,w,"skill",10,"physical",null);ArmoryEffects.mark(p,t,"vulnerable",2,80);}});ArmoryEffects.fallGuard(p);}
            e.released();if(!ult&&first)return new AbilityStepResult.AwaitInput(40,1,AbilityParameters.EMPTY);
        }
        return new AbilityStepResult.Complete();
    }
}
