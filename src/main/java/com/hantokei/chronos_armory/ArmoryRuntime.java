package com.hantokei.chronos_armory;

import static com.hantokei.chronos_armory.ArmoryCombat.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import io.github.fatalitaya.combatnexus.combat.api.*;
import io.github.fatalitaya.combatnexus.combat.ability.api.*;
import io.github.fatalitaya.combatnexus.combat.absorption.api.*;
import java.util.*;

public final class ArmoryRuntime {
    static final Map<UUID,State> STATES=new HashMap<>();
    static final Map<UUID,Deque<Loss>> HISTORY=new HashMap<>();
    public static final class State {
        double attackDamage;long charging=-1,lastHurt,lastDealt,autoAt,lightningAt;UUID chargingItem,reinput,secondTarget,held;
        boolean gliding,violet,hidden;final Set<UUID> dragonActions=new HashSet<>();final List<Shot> shots=new ArrayList<>();Field field;Vec3 lastPos;
        State(long now){lastHurt=lastDealt=now;autoAt=now+20;}
    }
    record Loss(long tick,double amount) {}
    public static State state(ServerPlayer p){return STATES.computeIfAbsent(p.getUUID(),k->new State(now(p)));}
    public static double charge(ServerPlayer p,Weapon w){return p.getPersistentData().getCompound("chronos_charge").getDouble(w.path());}
    public static void addCharge(ServerPlayer p,Weapon w,double amount){var n=p.getPersistentData().getCompound("chronos_charge");n.putDouble(w.path(),Math.max(0,Math.min(w.charge,charge(p,w)+amount)));p.getPersistentData().put("chronos_charge",n);}
    static void recordLoss(LivingEntity t,double amount){if(amount<=0)return;var q=HISTORY.computeIfAbsent(t.getUUID(),k->new ArrayDeque<>());long time=now(t);while(!q.isEmpty()&&q.peek().tick<time-60)q.remove();q.add(new Loss(time,amount));}
    static double history(LivingEntity t){long time=now(t);return HISTORY.getOrDefault(t.getUUID(),new ArrayDeque<>()).stream().filter(l->l.tick>=time-60&&l.tick<time).mapToDouble(Loss::amount).sum();}
    public static void tick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer p)||!p.isAlive())return;
        for(ItemStack stack:p.getInventory().items)identify(stack);identify(p.getOffhandItem());
        if(CombatAccess.find((WorldLookup)p.serverLevel()).isEmpty())return;
        State s=state(p);long n=now(p);UUID held=instance(active(p));
        if(!Objects.equals(held,s.held)){
            s.charging=-1;s.violet=false;s.lastDealt=s.lastHurt=n;s.held=held;s.gliding=false;
            ArmoryEffects.remove(p,"fall_guard");
        }
        if(s.gliding){if(p.onGround()||p.isInWater()||!holds(p,Weapon.MOON_MARROW)) {s.gliding=false;p.stopFallFlying();} else p.startFallFlying();}
        heldEffects(p,s,n);
        boolean hidden=ArmoryEffects.has(p,"formless");
        if(hidden!=s.hidden||hidden&&n%20==0){s.hidden=hidden;net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(new ArmoryNetwork.Hidden(p.getUUID(),hidden));}
        if(n%20==0){s.dragonActions.clear();ArmoryPassives.BOOSTED.remove(p.getUUID());}
        if(s.field!=null&&!s.field.tick(p,n))s.field=null;
        s.shots.removeIf(shot->!shot.tick(p,n));
        if(Weapon.of(p.getOffhandItem())==Weapon.EVERNIGHT_STAR){
            if(n>=s.autoAt){s.autoAt=n+20;var targets=area(p,p.position(),8);if(!arrowInFlight(p,instance(p.getOffhandItem()))&&!targets.isEmpty())s.shots.add(Shot.automatic(p,targets.getFirst()));}
        }else s.autoAt=n+20;
        if(n%10==0&&Weapon.of(active(p))!=null){Weapon w=Weapon.of(active(p));long cd=world(p).abilities().cooldown(p.getUUID(),new AbilityCooldownSlot.Skill(id(w.path()+"/skill"))).remaining();p.displayClientMessage(Component.translatable("message.chronos_armory.status",(int)charge(p,w),w.charge,String.format(Locale.ROOT,"%.1f",cd/20d)),true);}
    }
    static void heldEffects(ServerPlayer p,State s,long n){
        boolean cyan=holds(p,Weapon.CYANFROST_VIOLETVOLT);
        ArmoryEffects.heldNative(p,"frost_speed","speed",0,carries(p,Weapon.CYANFROST_VIOLETVOLT));
        ArmoryEffects.heldNative(p,"violet_jump","jump_boost",1,carries(p,Weapon.CYANFROST_VIOLETVOLT));
        ArmoryEffects.heldNative(p,"moon_speed","speed",3,holds(p,Weapon.MOON_MARROW)&&night(p));
        ArmoryEffects.heldNative(p,"moon_glowing","glowing",0,holds(p,Weapon.MOON_MARROW)&&night(p));
        if(cyan){
            double cap=p.getMaxHealth()*(ArmoryEffects.has(p,"aurora")?0.3:0.2);
            world(p).absorption().submit(new AbsorptionCommand(UUID.randomUUID(),p.getUUID(),source(p,"frost"),AbsorptionCommand.Operation.RESIZE,0,cap));
            if(n-s.lastHurt>=60)world(p).absorption().submit(new AbsorptionCommand(UUID.randomUUID(),p.getUUID(),source(p,"frost"),AbsorptionCommand.Operation.GRANT,cap,cap));
            if(!ArmoryEffects.has(p,"aurora")&&n-s.lastDealt>=60)s.violet=true;
        }else{world(p).absorption().submit(new AbsorptionCommand(UUID.randomUUID(),p.getUUID(),source(p,"frost"),AbsorptionCommand.Operation.REMOVE,0,0));s.violet=false;}
        for(var pair:List.of(Map.entry(Weapon.DRAGONSPINE_BREAKER,"dragon_guard"),Map.entry(Weapon.BIG_APPLE,"apple_guard"))){
            if(holds(p,pair.getKey())){if(!ArmoryEffects.has(p,pair.getValue()))ArmoryEffects.mark(p,p,pair.getValue(),1,1200000);}else ArmoryEffects.remove(p,pair.getValue());
        }
        if(holds(p,Weapon.BIG_APPLE)&&p.getHealth()<p.getMaxHealth()*0.2&&ArmoryEffects.ready(p,"apple_survival"))world(p).beforeApplication(UUID.randomUUID(),p.getUUID(),new BeforeApplicationPlan(ArmoryEffects.applePlan(p)));
        if(ArmoryEffects.has(p,"formless"))for(var mob:p.serverLevel().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(64)))if(mob.getTarget()==p)mob.setTarget(null);
    }
    public static void leave(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)clear(p);}
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)clear(p);}
    public static void clone(PlayerEvent.Clone e){
        if(!e.isWasDeath())e.getEntity().getPersistentData().put("chronos_charge",e.getOriginal().getPersistentData().getCompound("chronos_charge").copy());
        STATES.remove(e.getOriginal().getUUID());HISTORY.remove(e.getOriginal().getUUID());
    }
    static void clear(ServerPlayer p){
        if(CombatAccess.find((WorldLookup)p.serverLevel()).isPresent())for(String effect:List.of("aurora","dragon_blessing","dragon_pressure","formless","fall_guard","return_star"))ArmoryEffects.remove(p,effect);
        net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(new ArmoryNetwork.Hidden(p.getUUID(),false));
        STATES.remove(p.getUUID());HISTORY.remove(p.getUUID());ArmoryPassives.BOOSTED.remove(p.getUUID());
    }
    public static boolean skillGliding(LivingEntity e){var s=STATES.get(e.getUUID());return !e.level().isClientSide&&s!=null&&s.gliding&&e.isAlive()&&!e.isInWater()&&holds(e,Weapon.MOON_MARROW);}
    public static void attack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent e){if(e.getEntity() instanceof ServerPlayer p)ArmoryEffects.remove(p,"formless");}
    public static void target(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent e){var target=e.getNewAboutToBeSetTarget();if(target instanceof ServerPlayer&&ArmoryEffects.has(target,"formless"))e.setNewAboutToBeSetTarget(null);}
    public static void died(net.neoforged.neoforge.event.entity.living.LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p){p.getPersistentData().remove("chronos_charge");clear(p);}}
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e){STATES.clear();HISTORY.clear();ArmoryPassives.BOOSTED.clear();}
    static boolean arrowInFlight(ServerPlayer p,UUID instance){return state(p).shots.stream().anyMatch(s->s.weapon==Weapon.EVERNIGHT_STAR&&Objects.equals(s.instance,instance));}
    static List<LivingEntity> pathTargets(ServerPlayer p,Vec3 from,Vec3 to,double radius){
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(radius+1),t->enemy(p,t)&&t.getBoundingBox().inflate(radius).clip(from,to).isPresent()&&visible(p.serverLevel(),from.add(0,p.getBbHeight()/2,0),t))
            .stream().sorted(Comparator.comparingDouble((LivingEntity t)->t.distanceToSqr(from)).thenComparing(Entity::getUUID)).toList();
    }
    static LivingEntity rayTarget(ServerPlayer p,Vec3 from,Vec3 to,double radius){return pathTargets(p,from,to,radius).stream().findFirst().orElse(null);}
    static List<LivingEntity> starTargets(ServerPlayer p){Vec3 f=p.getLookAngle().multiply(1,0,1).normalize(),side=new Vec3(-f.z,0,f.x);return area(p,p.position().add(f.scale(8)),12).stream().filter(t->{Vec3 d=t.getBoundingBox().getCenter().subtract(p.position());return d.dot(f)>=0&&d.dot(f)<=16&&Math.abs(d.dot(side))<=4+t.getBbWidth()/2&&Math.abs(d.y)<=3+t.getBbHeight()/2;}).sorted(Comparator.comparingDouble(t->t.distanceToSqr(p))).toList();}
    static void fireballs(ServerPlayer p){var targets=area(p,p.position(),16);for(int i=0;i<10;i++){var shot=new Shot(p,Weapon.ANTARES,"fireball",10,20,false,false);if(!targets.isEmpty())shot.targets.add(targets.get(i%targets.size()).getUUID());state(p).shots.add(shot);}}
    static void star(ServerPlayer p,boolean ultimate,double q){
        double amount=ultimate?8:45*q;
        if(!ultimate&&ArmoryEffects.has(p,"return_star")){amount*=1.2;ArmoryEffects.remove(p,"return_star");}
        var shot=new Shot(p,Weapon.EVERNIGHT_STAR,ultimate?"ultimate":"skill",amount,ultimate?100:5+15*(q-0.1)/0.9,true,ultimate);
        if(ultimate)starTargets(p).forEach(t->shot.targets.add(t.getUUID()));state(p).shots.add(shot);
    }
    static void field(ServerPlayer p){var f=new Field(p.position(),now(p));state(p).field=f;f.tick(p,now(p));}
    static final class Field {
        final Vec3 center;final long start;long enemyAt,allyAt;
        Field(Vec3 c,long n){center=c;start=n;enemyAt=allyAt=n;}
        boolean tick(ServerPlayer p,long n){if(n>=start+300)return false;
            boolean hostile=n>=enemyAt, friendly=n>=allyAt;if(hostile)enemyAt=n+10;if(friendly)allyAt=n+60;
            if(hostile||friendly)for(var t:p.serverLevel().getEntitiesOfClass(LivingEntity.class,new AABB(center.x-5,center.y,center.z-5,center.x+5,center.y+4,center.z+5))){
                if(t.getBoundingBox().distanceToSqr(new Vec3(center.x,Math.max(center.y,Math.min(center.y+4,t.getY())),center.z))>25||!visible(p.serverLevel(),center.add(0,0.1,0),t))continue;
                if(enemy(p,t)){if(hostile){potion(p,t,MobEffects.MOVEMENT_SLOWDOWN,20,1,null);potion(p,t,MobEffects.POISON,20,3,null);}}
                else if(friendly&&(!(t instanceof net.minecraft.world.entity.player.Player)||t==p||p.isAlliedTo(t))){
                    long last=t.getPersistentData().getLong("chronos_core_upgrade");if(n-last>=60||!ArmoryEffects.has(t,"core_guard")){ArmoryEffects.mark(p,t,"core_guard",Math.min(5,ArmoryEffects.level(t,"core_guard")+1),70);t.getPersistentData().putLong("chronos_core_upgrade",n);}
                }
            }return true;
        }
    }
    static final class Shot {
        final UUID instance;final Weapon weapon;final String skill;final double damage,maxDistance;final boolean returns,chain;
        boolean violetAction;final Vec3 origin;Vec3 pos,direction;double distance;final long start;long returnAt=-1;final Set<UUID> hit=new HashSet<>(),contacted=new HashSet<>();final Deque<UUID> targets=new ArrayDeque<>();final Deque<Vec3> path=new ArrayDeque<>();
        Shot(ServerPlayer p,Weapon w,String skill,double damage,double range,boolean returns,boolean chain){this.instance=ArmoryCombat.instance(skill.equals("automatic")?p.getOffhandItem():active(p));weapon=w;this.skill=skill;this.damage=damage;maxDistance=range;this.returns=returns;this.chain=chain;origin=p.position();pos=p.getEyePosition();direction=p.getLookAngle();start=now(p);}
        static Shot automatic(ServerPlayer p,LivingEntity t){var s=new Shot(p,Weapon.EVERNIGHT_STAR,"automatic",8,20,true,false);s.targets.add(t.getUUID());return s;}
        boolean tick(ServerPlayer p,long n){
            if(returnAt>=0){if(n-returnAt>=20)return false;if(path.isEmpty()||pos.distanceToSqr(p.getEyePosition())<1){if(!hit.isEmpty()&&!skill.equals("automatic")&&(Objects.equals(instance,ArmoryCombat.instance(p.getMainHandItem()))||Objects.equals(instance,ArmoryCombat.instance(p.getOffhandItem()))))ArmoryEffects.mark(p,p,"return_star",1,100);return false;}for(int i=0;i<4&&!path.isEmpty();i++)pos=path.removeLast();particle(p);return true;}
            if(n-start>=60||distance>=maxDistance)return beginReturn(n);
            LivingEntity target=null;
            while(!targets.isEmpty()) {var e=p.serverLevel().getEntity(targets.peek());if(e instanceof LivingEntity t&&enemy(p,t)&&(!chain||t.distanceToSqr(origin)<=576)){target=t;break;}targets.remove();if(!chain)break;}
            if(chain&&target==null)return beginReturn(n);
            if(target!=null)direction=target.getBoundingBox().getCenter().subtract(pos).normalize();
            Vec3 next=pos.add(direction.scale(1.25));
            var block=p.level().clip(new ClipContext(pos,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(!p.serverLevel().hasChunkAt(net.minecraft.core.BlockPos.containing(next)))return false;
            boolean blocked=block.getType()!=HitResult.Type.MISS;if(blocked)next=block.getLocation();
            path.addLast(pos);var contacts=pathTargets(p,pos,next,0.25);
            for(var t:contacts){if(chain&&(target==null||t!=target))continue;if(!contacted.add(t.getUUID()))continue;
                boolean ready=state(p).violet&&holds(p,Weapon.CYANFROST_VIOLETVOLT)&&!ArmoryEffects.has(p,"aurora");
                var result=ArmoryCombat.damage(p,t,weapon,skill,damage,weapon==Weapon.ANTARES?"magic":"physical",null,instance,violetAction);
                if(ready&&!skill.equals("automatic")&&result.settlement().map(settled->settled.successfulHit()).orElse(false))violetAction=true;
                if(result.settlement().map(settled->settled.successfulHit()).orElse(false))hit.add(t.getUUID());
                if(chain)targets.remove();if(weapon==Weapon.ANTARES)return false;
            }
            distance+=pos.distanceTo(next);pos=next;particle(p);
            if(blocked){if(chain&&!targets.isEmpty()){targets.remove();return true;}return beginReturn(n);}return true;
        }
        boolean beginReturn(long n){if(!returns)return false;returnAt=n;return true;}
        void particle(ServerPlayer p){p.serverLevel().sendParticles(weapon==Weapon.ANTARES?ParticleTypes.FLAME:ParticleTypes.END_ROD,pos.x,pos.y,pos.z,2,0.02,0.02,0.02,0);}
    }
}
