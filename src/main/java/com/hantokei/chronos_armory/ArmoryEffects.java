package com.hantokei.chronos_armory;

import static com.hantokei.chronos_armory.ArmoryCombat.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.fatalitaya.combatnexus.api.id.NexusId;
import io.github.fatalitaya.combatnexus.attribute.api.*;
import io.github.fatalitaya.combatnexus.combat.api.*;
import io.github.fatalitaya.combatnexus.combat.api.behavior.*;
import io.github.fatalitaya.combatnexus.combat.api.scheduling.*;
import io.github.fatalitaya.combatnexus.combat.api.transaction.*;
import io.github.fatalitaya.combatnexus.combat.ability.api.*;
import io.github.fatalitaya.combatnexus.combat.shield.api.*;
import io.github.fatalitaya.combatnexus.effect.api.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import java.util.*;

public final class ArmoryEffects {
    static final io.github.fatalitaya.combatnexus.combat.state.api.StateKey<Long> BLACK_NEXT=new io.github.fatalitaya.combatnexus.combat.state.api.StateKey<>(id("black_next"),Codec.LONG,v->v,io.github.fatalitaya.combatnexus.combat.state.api.StateKey.Scope.ENTITY,Set.of(io.github.fatalitaya.combatnexus.combat.state.api.StateKey.Boundary.DEATH),true);
    static io.github.fatalitaya.combatnexus.combat.state.api.StateAddress<Long> blackAddress(LivingEntity t){return new io.github.fatalitaya.combatnexus.combat.state.api.StateAddress<>(t.getUUID(),BLACK_NEXT,Optional.empty());}
    static void register(CombatRegistrar r) {
        r.runtimeState(BLACK_NEXT);
        r.effectStacking(id("strongest_later"),(old,incoming,now)->{
            if(old.snapshot().level()>incoming.snapshot().level())return old;
            if(old.snapshot().level()==incoming.snapshot().level()&&old.snapshot().expiresAtTick().orElse(Long.MAX_VALUE)>=incoming.snapshot().expiresAtTick().orElse(Long.MAX_VALUE))return old;
            return incoming;
        });
        r.behavior(new BehaviorType<>(id("guard"),Guard.class,RecordCodecBuilder.mapCodec(i->i.group(Codec.STRING.fieldOf("kind").forGetter(Guard::kind)).apply(i,Guard::new))));
        r.behavior(new BehaviorType<>(id("periodic"),Periodic.class,RecordCodecBuilder.mapCodec(i->i.group(Codec.STRING.fieldOf("kind").forGetter(Periodic::kind)).apply(i,Periodic::new))));
        r.intent(new IntentType<>(id("periodic_tick"),Tick.class,RecordCodecBuilder.mapCodec(i->i.group(Codec.STRING.fieldOf("kind").forGetter(Tick::kind)).apply(i,Tick::new)),EnumSet.allOf(CombatOperation.class),(tick,e)->{
            var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            LivingEntity t=null;
            for(var level:server.getAllLevels()){var entity=level.getEntity(e.target());if(entity instanceof LivingEntity living){t=living;break;}}
            if(t==null||!t.isAlive())return;
            var owner=e.source().owner().map(ArmoryCombat::player).orElse(null);
            if(tick.kind.equals("black_flame")) {
                long next=world(t).time().now(t.getUUID(),id("black_flame"))+5;
                world(t).states().write(UUID.randomUUID(),blackAddress(t),e.source(),next);
                var req=io.github.fatalitaya.combatnexus.combat.damage.api.damage.DamageRequest.builder((net.minecraft.server.level.ServerLevel)t.level(),t,
                    io.github.fatalitaya.combatnexus.combat.damage.api.damage.DamageSourceDescriptor.of(new NexusId("minecraft","indirect_magic")),(float)(1.5+t.getMaxHealth()*0.005))
                    .logicalAttacker(owner).effectOwner(owner).directEntity(owner).sourceTag(id("periodic")).sourceTag(id("weapon/antares"))
                    .cooldownPolicy(io.github.fatalitaya.combatnexus.combat.damage.api.damage.DamageCooldownPolicy.INDEPENDENT).build();
                e.submit(new io.github.fatalitaya.combatnexus.combat.damage.api.damage.NativeDamageCommand(UUID.randomUUID(),req));
                judge(e.source(),t,null);
            }
            if(tick.kind.equals("dragon_blessing")&&owner!=null)heal(owner,t,level(t,"dragon_blessing"),null);
        }));
    }
    public static int level(LivingEntity target,String name) {return world(target).effects().snapshot(target.getUUID()).stream().filter(e->e.definition().equals(id(name))).mapToInt(EffectView::level).max().orElse(0);}
    public static boolean has(LivingEntity t,String name){return level(t,name)>0;}
    public static void remove(LivingEntity t,String name) {for(var e:world(t).effects().snapshot(t.getUUID()))if(e.definition().equals(id(name))&&e.source().isPresent())world(t).effects().remove(UUID.randomUUID(),t.getUUID(),e.definition(),e.source().get(),EffectService.Removal.CONDITION_ENDED);}
    static EffectInstance.AttributeContribution attr(String name,String attribute,double amount,AttributeOperation op) {
        return new EffectInstance.AttributeContribution(new NexusId("minecraft",attribute),new AttributeSnapshot.Contribution(id(name),amount,op));
    }
    static EffectInstance instance(ServerPlayer p,LivingEntity t,String name,int level,int ticks) {
        var attrs=new ArrayList<EffectInstance.AttributeContribution>();var behavior=BehaviorPlan.NONE;
        var control=EffectControl.NONE;
        boolean harmful=Set.of("vulnerable","armor_break","black_flame","dissolve","rewind_slow").contains(name);
        if(name.equals("armor_break"))attrs.add(attr(name,"generic.armor",-Math.min(1,0.1*level),AttributeOperation.ADD_MULTIPLIED_TOTAL));
        if(name.equals("core_guard"))attrs.add(attr(name,"generic.armor",0.1*level,AttributeOperation.ADD_MULTIPLIED_TOTAL));
        if(name.equals("hunt_armor"))attrs.add(attr(name,"generic.armor",0.4,AttributeOperation.ADD_MULTIPLIED_TOTAL));
        if(name.equals("rewind_slow")){attrs.add(attr(name,"generic.movement_speed",-0.45,AttributeOperation.ADD_MULTIPLIED_TOTAL));control=new EffectControl(Set.of(ControlCategory.MOVEMENT),Set.of(),Set.of(),Set.of());}
        if(name.equals("dissolve")) {attrs.add(attr(name,"generic.max_health",-15/0.85,AttributeOperation.ADD_VALUE));attrs.add(attr(name+"_ratio","generic.max_health",-0.15,AttributeOperation.ADD_MULTIPLIED_TOTAL));}
        if(name.equals("dragon_blessing")){attrs.add(attr(name,"generic.max_health",3*level,AttributeOperation.ADD_VALUE));behavior=new BehaviorPlan(0,List.of(new Periodic(name)));}
        if(name.equals("dragon_pressure")){attrs.add(attr(name,"generic.attack_damage",0.05*level,AttributeOperation.ADD_MULTIPLIED_TOTAL));attrs.add(attr(name,"generic.attack_speed",0.05*level,AttributeOperation.ADD_MULTIPLIED_TOTAL));attrs.add(attr(name,"player.entity_interaction_range",0.5*level,AttributeOperation.ADD_VALUE));attrs.add(attr(name,"player.block_interaction_range",0.5*level,AttributeOperation.ADD_VALUE));}
        if(name.equals("black_flame"))behavior=new BehaviorPlan(0,List.of(new Periodic(name)));
        if(name.equals("immune")||name.equals("dragon_blessing")&&level==5)control=new EffectControl(Set.of(),Set.of(),Set.of(),EnumSet.allOf(ControlCategory.class));
        if(name.endsWith("_guard")&&!name.equals("core_guard"))behavior=new BehaviorPlan(0,List.of(new Guard(name)));
        long expiry=world(t).time().now(t.getUUID(),id(name))+ticks;
        return new EffectInstance(new EffectSnapshot(t.getUUID(),id(name),id(name),level,OptionalLong.of(expiry),Set.of()),attrs,EffectOwnership.MANAGED,behavior,
            new EffectPolicy(harmful?EffectPolicy.Category.HARMFUL:EffectPolicy.Category.BENEFICIAL,!name.equals("rewind_slow"),harmful?Set.of(id("negative"),new NexusId("combatnexus","harmful")):Set.of(),control,name.equals("rewind_slow")?EffectApplicationPolicy.FORCE:EffectApplicationPolicy.NORMAL),List.of(),
            new EffectDisplay(null,false,false,true,p.getUUID(),true,true,false));
    }
    public static void mark(ServerPlayer p,LivingEntity t,String name,int level,int ticks) {mark(p,t,name,level,ticks,null);}
    public static void mark(ServerPlayer p,LivingEntity t,String name,int level,int ticks,CombatParent parent) {
        boolean newBlack=name.equals("black_flame")&&!has(t,name);
        var application=new EffectApplication(UUID.randomUUID(),instance(p,t,name,level,ticks),Set.of("black_flame","vulnerable","armor_break").contains(name)?id("strongest_later"):EffectService.Stacking.REFRESH_KEEP_STRONGER.id());
        var result=parent==null?world(t).effects().apply(application):world(t).effects().apply(application,parent);
        if(newBlack&&has(t,"black_flame"))world(t).states().write(UUID.randomUUID(),blackAddress(t),source(p,"black_flame"),world(t).time().now(t.getUUID(),id("black_flame"))+5,parent);
        if(name.equals("dissolve")&&t.getHealth()>t.getMaxHealth())world(t).health().correct(new io.github.fatalitaya.combatnexus.combat.health.api.HealthCorrection(UUID.randomUUID(),t.getUUID(),source(p,name),t.getMaxHealth(),io.github.fatalitaya.combatnexus.combat.health.api.HealthCorrection.Reason.MAXIMUM_CHANGED),parent);
    }
    static void heldNative(ServerPlayer p,String sourceName,String nativeName,int amplifier,boolean enabled) {
        var effect=new NexusId("minecraft",nativeName);var origin=id(sourceName);
        if(!enabled){world(p).effects().remove(UUID.randomUUID(),p.getUUID(),effect,origin,EffectService.Removal.CONDITION_ENDED);return;}
        var attrs=new ArrayList<EffectInstance.AttributeContribution>();
        if(nativeName.equals("speed"))attrs.add(attr(sourceName,"generic.movement_speed",0.2*(amplifier+1),AttributeOperation.ADD_MULTIPLIED_TOTAL));
        var instance=new EffectInstance(new EffectSnapshot(p.getUUID(),effect,origin,amplifier,OptionalLong.empty(),Set.of()),attrs,EffectOwnership.MANAGED,BehaviorPlan.NONE,new EffectPolicy(EffectPolicy.Category.BENEFICIAL,true),List.of(),new EffectDisplay(effect,false,false,true,p.getUUID(),true,false,false));
        world(p).effects().apply(new EffectApplication(UUID.randomUUID(),instance,EffectService.Stacking.STRONGER_WITH_HIDDEN));
    }
    static void forcedSlow(ServerPlayer p,LivingEntity t){mark(p,t,"rewind_slow",3,120);}
    static void fallGuard(ServerPlayer p){remove(p,"fall_guard");mark(p,p,"fall_guard",1,200);}
    static void dragon(ServerPlayer p,boolean hit) {dragon(p,hit,null);}
    static void dragon(ServerPlayer p,boolean hit,CombatParent parent) {
        for(String name:List.of("dragon_blessing","dragon_pressure")){
            int old=level(p,name);if(hit&&old==0)continue;
            mark(p,p,name,hit?Math.min(5,old+1):Math.max(1,old),name.equals("dragon_blessing")?100:200,parent);
        }
        if(level(p,"dragon_blessing")==5)world(p).effects().cleanse(new EffectCleanse(UUID.randomUUID(),p.getUUID(),EnumSet.allOf(ControlCategory.class),Set.of()),parent);
    }
    static boolean ready(ServerPlayer p,String name) {return world(p).abilities().cooldown(p.getUUID(),new AbilityCooldownSlot.Skill(id(name))).remaining()==0;}
    static DomainCommand cooldown(ServerPlayer p,String name,int ticks) {return new DomainCommand.AbilityCooldown(new AbilityCooldownCommand(UUID.randomUUID(),p.getUUID(),source(p,name),new AbilityCooldownSlot.Skill(id(name)),AbilityCooldownCommand.Operation.RESET,ticks));}
    static List<BeforeApplicationPlan.Step> applePlan(ServerPlayer p) {
        var steps=new ArrayList<BeforeApplicationPlan.Step>();
        var shield=new ShieldGrant(source(p,"apple_shield"),new ShieldDefinition(20,0,Set.of(),Set.of(),0,0,ShieldDefinition.Stacking.REPLACE_SAME_SOURCE),20,OptionalLong.of(world(p).time().now(p.getUUID(),id("apple_shield"))+100));
        for(var c:List.<DomainCommand>of(cooldown(p,"apple_survival",800),new DomainCommand.Cleanse(new EffectCleanse(UUID.randomUUID(),p.getUUID(),EnumSet.allOf(ControlCategory.class),Set.of(id("negative"),new NexusId("combatnexus","harmful")))),new DomainCommand.GrantShield(UUID.randomUUID(),p.getUUID(),shield),new DomainCommand.ManagedEffect(new EffectApplication(UUID.randomUUID(),instance(p,p,"immune",1,100),EffectService.Stacking.REPLACE))))
            steps.add(new BeforeApplicationPlan.Step(c,BeforeApplicationPlan.RejectionPolicy.CONTINUE));
        return steps;
    }
    public record Guard(String kind) implements DamageGuardBehavior {
        public Stage stage(){return kind.equals("apple_guard")?Stage.REMAINING_HEALTH:Stage.BEFORE_RESOURCES;}
        public int cooldownTicks(){return kind.equals("fall_guard")?0:1;}
        public Optional<BeforeApplicationPlan> select(DamageGuardContext c) {
            ServerPlayer p=player(c.target());if(p==null)return Optional.empty();
            if(kind.equals("apple_guard"))return holds(p,Weapon.BIG_APPLE)&&ready(p,"apple_survival")&&c.predictedHealth()<c.maximumHealth()*0.2?Optional.of(new BeforeApplicationPlan(applePlan(p))):Optional.empty();
            if(kind.equals("fall_guard"))return c.inputs().damageType().filter(t->t.equals(new NexusId("minecraft","fall"))).isPresent()&&c.damage()>0?Optional.of(BeforeApplicationPlan.EMPTY):Optional.empty();
            if(!holds(p,Weapon.DRAGONSPINE_BREAKER)||!ready(p,"dragon_step")||c.damage()<=0||c.sourceTags().contains(id("periodic"))||c.sourceTags().contains(id("replay"))||c.sourceTags().contains(id("extra")))return Optional.empty();
            boolean enemy=c.inputs().participants().keySet().stream().filter(u->!u.equals(p.getUUID())).map(u->p.serverLevel().getEntity(u)).anyMatch(e->e instanceof LivingEntity l&&ArmoryCombat.enemy(p,l));
            if(!enemy)return Optional.empty();
            var speed=new NativeEffectCommand(UUID.randomUUID(),p.getUUID(),source(p,"dragon_step"),new NexusId("minecraft","speed"),NativeEffectCommand.Operation.REFRESH,200,1,false,true,true);
            return Optional.of(new BeforeApplicationPlan(List.of(new BeforeApplicationPlan.Step(cooldown(p,"dragon_step",400),BeforeApplicationPlan.RejectionPolicy.CONTINUE),new BeforeApplicationPlan.Step(new DomainCommand.Effect(speed),BeforeApplicationPlan.RejectionPolicy.CONTINUE))));
        }
    }
    public record Periodic(String kind) implements PeriodicBehavior {
        public int intervalTicks(){return kind.equals("black_flame")?5:20;}
        public PeriodicPhase phase(){return PeriodicPhase.PRESERVE;}
        public void execute(PeriodicExecution e){e.execute(new Tick(kind));}
    }
    public record Tick(String kind) implements ScheduledIntent {}
    static void judge(CombatSource source,LivingEntity t,CombatParent parent) {
        if(!t.isAlive())return;
        long n=world(t).time().now(t.getUUID(),id("black_flame"));
        long remaining=world(t).effects().snapshot(t.getUUID()).stream().filter(e->e.definition().equals(id("black_flame"))).mapToLong(e->e.remainingTicks().orElse(0)).max().orElse(0);
        long next=world(t).states().read(blackAddress(t)).orElse(n+5);
        long jumps=next<n+remaining?1+(n+remaining-1-Math.max(n,next))/5:0;
        if((1.5+t.getMaxHealth()*0.005)*jumps>t.getHealth()){
            world(t).health().execute(new io.github.fatalitaya.combatnexus.combat.health.api.LethalAttempt(UUID.randomUUID(),t.getUUID(),source,new NexusId("minecraft","indirect_magic"),source.owner(),false),parent);
            ServerPlayer owner=source.owner().map(ArmoryCombat::player).orElse(null);
            if(!t.isAlive()&&owner!=null)ArmoryPassives.formless(owner,parent);
        }
    }
}
