"""Exercise gameplay in the disposable ChronosValidation world through AutoJS."""
import json, time, urllib.request

def evaluate(code, side="server"):
    request=urllib.request.Request("http://127.0.0.1:25585/mcp",json.dumps({"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"eval_js","arguments":{"side":side,"code":code,"timeout_ms":10000}}}).encode(),{"Content-Type":"application/json"})
    result=json.loads(urllib.request.urlopen(request,timeout=15).read())
    body=json.loads(result["result"]["content"][0]["text"])
    if not body.get("ok"):raise AssertionError(body)
    return json.loads(body["value"])

PRE='var p=AutoJS.server().getPlayerList().getPlayers().getFirst(),C=Java.loadClass("com.hantokei.chronos_armory.ArmoryCombat"),R=Java.loadClass("com.hantokei.chronos_armory.ArmoryRuntime"),A=Java.loadClass("com.hantokei.chronos_armory.ArmoryAbilities"),W=Java.loadClass("com.hantokei.chronos_armory.Weapon"),Items=Java.loadClass("com.hantokei.chronos_armory.ArmoryItems"),Stack=Java.loadClass("net.minecraft.world.item.ItemStack"),Slot=Java.loadClass("net.minecraft.world.entity.EquipmentSlot"),Attr=Java.loadClass("net.minecraft.world.entity.ai.attributes.Attributes");'
def run(code):return evaluate(PRE+code)
def equip(name):
    run('p.setItemSlot(Slot.MAINHAND,new Stack(Items.'+name+'.get()));JSON.stringify({ok:true})');time.sleep(.6)

def clear_skill(name):
    return run('var UUID=Java.loadClass("java.util.UUID"),CS=Java.loadClass("io.github.fatalitaya.combatnexus.combat.ability.api.AbilityCooldownSlot$Skill"),CC=Java.loadClass("io.github.fatalitaya.combatnexus.combat.ability.api.AbilityCooldownCommand"),CO=Java.loadClass("io.github.fatalitaya.combatnexus.combat.ability.api.AbilityCooldownCommand$Operation");C.world(p).abilities().cooldown(new CC(UUID.randomUUID(),p.getUUID(),C.source(p,"test"),new CS(C.id("'+name.lower()+'/skill")),CO.CLEAR,0));JSON.stringify({ok:true})')

if __name__=="__main__":
    location=run('JSON.stringify({path:String(AutoJS.server().getWorldPath(Java.loadClass("net.minecraft.world.level.storage.LevelResource").ROOT))})')
    assert 'ChronosValidation' in location['path'], 'Only run in the disposable ChronosValidation world' 
    run('p.removeAllEffects();p.setGameMode(Java.loadClass("net.minecraft.world.level.GameType").CREATIVE);p.teleportTo(0,-60,0);p.setYRot(0);p.setXRot(0);p.serverLevel().setDayTime(18000);JSON.stringify({ok:true})')
    for name,damage,speed in [('NULL',9,3),('ANTARES',14,1.75),('MOON_MARROW',19,1.2),('DRAGONSPINE_BREAKER',15,1.4),('BIG_APPLE',20,.7),('EVERNIGHT_STAR',8,1.6),('CYANFROST_VIOLETVOLT',10,1.5)]:
        equip(name)
        result=run('JSON.stringify({damage:p.getAttributeValue(Attr.ATTACK_DAMAGE),speed:p.getAttributeValue(Attr.ATTACK_SPEED),grants:C.world(p).abilities().grants(p.getUUID()).size()})')
        assert abs(result['damage']-damage)<.001 and abs(result['speed']-speed)<.001 and result['grants']==2,(name,result)
        print(name,result,flush=True)
    for name in ['NULL','ANTARES','MOON_MARROW','DRAGONSPINE_BREAKER','BIG_APPLE','EVERNIGHT_STAR','CYANFROST_VIOLETVOLT']:
        equip(name);clear_skill(name)
        setup=run('p.connection.teleport(0,-60,0,0,0);p.serverLevel().setDayTime(18000);p.serverLevel().getEntitiesOfClass(Java.loadClass("net.minecraft.world.entity.Mob"),p.getBoundingBox().inflate(32)).forEach(function(m){m.discard()});var z=Java.loadClass("net.minecraft.world.entity.EntityType").ZOMBIE.create(p.serverLevel());z.setPos(0,-60,2);z.setNoAi(true);z.getAttribute(Attr.MAX_HEALTH).setBaseValue(1000);z.setHealth(1000);p.serverLevel().addFreshEntity(z);R.addCharge(p,W.'+name+',2000);var g=C.world(p).abilities().grants(p.getUUID()).stream().filter(function(g){return String(g.ability()).endsWith("/ultimate")}).findFirst().get(),Req=Java.loadClass("io.github.fatalitaya.combatnexus.combat.ability.api.AbilityRequest"),Target=Java.loadClass("io.github.fatalitaya.combatnexus.combat.ability.api.AbilityTarget$Self"),Params=Java.loadClass("io.github.fatalitaya.combatnexus.combat.ability.api.AbilityParameters"),UUID=Java.loadClass("java.util.UUID");var result=C.world(p).abilities().execute(new Req(UUID.randomUUID(),g,new Target(),Params.EMPTY));JSON.stringify({id:String(z.getUUID()),execution:result.execution().isPresent()?String(result.execution().get().execution()):null,result:String(result),charge:R.charge(p,W.'+name+')})')
        assert setup['charge']==0 and setup['execution'],(name,setup)
        time.sleep(.8)
        result=run('var uuid=Java.loadClass("java.util.UUID"),z=p.serverLevel().getEntity(uuid.fromString("'+setup['id']+'"));JSON.stringify({health:z===null?0:z.getHealth(),execution:String(C.world(p).abilities().execution(uuid.fromString("'+setup['execution']+'"))),x:p.getX(),y:p.getY(),z:p.getZ()})')
        print(name,'ultimate',{'health':result['health'],'completed':'status=COMPLETED' in result['execution']},flush=True)
        assert 'status=COMPLETED' in result['execution'],(name,result)
        if name in ['NULL','MOON_MARROW','BIG_APPLE','EVERNIGHT_STAR']:assert result['health']<1000,(name,result)
        run('var z=p.serverLevel().getEntity(Java.loadClass("java.util.UUID").fromString("'+setup['id']+'"));if(z!==null)z.discard();JSON.stringify({ok:true})')

    # Active skills: isolated targets, continuation/reinput, flight and ground field.
    for index,name in enumerate(['NULL','ANTARES','MOON_MARROW','DRAGONSPINE_BREAKER','BIG_APPLE','EVERNIGHT_STAR','CYANFROST_VIOLETVOLT']):
        equip(name);clear_skill(name)
        x=0
        run('p.stopFallFlying();p.connection.teleport('+str(x)+',-60,0,0,0);p.serverLevel().setDayTime(18000);JSON.stringify({ok:true})')
        time.sleep(.6)
        setup=run('p.setOnGround(true);p.serverLevel().getEntitiesOfClass(Java.loadClass("net.minecraft.world.entity.Mob"),p.getBoundingBox().inflate(32)).forEach(function(m){m.discard()});var z=Java.loadClass("net.minecraft.world.entity.EntityType").ZOMBIE.create(p.serverLevel());z.setPos('+str(x)+',-60,2);z.setNoAi(true);z.getAttribute(Attr.MAX_HEALTH).setBaseValue(1000);z.setHealth(1000);p.serverLevel().addFreshEntity(z);var g=C.world(p).abilities().grants(p.getUUID()).stream().filter(function(g){return String(g.ability()).endsWith("/skill")}).findFirst().get(),Req=Java.loadClass("io.github.fatalitaya.combatnexus.combat.ability.api.AbilityRequest"),Target=Java.loadClass("io.github.fatalitaya.combatnexus.combat.ability.api.AbilityTarget$Self"),Params=Java.loadClass("io.github.fatalitaya.combatnexus.combat.ability.api.AbilityParameters"),UUID=Java.loadClass("java.util.UUID");var result=C.world(p).abilities().execute(new Req(UUID.randomUUID(),g,new Target(),Params.EMPTY));JSON.stringify({id:String(z.getUUID()),execution:result.execution().isPresent()?String(result.execution().get().execution()):null,result:String(result)})')
        assert setup['execution'],(name,setup)
        if name=='CYANFROST_VIOLETVOLT':
            run('var UUID=Java.loadClass("java.util.UUID"),RI=Java.loadClass("io.github.fatalitaya.combatnexus.combat.ability.api.AbilityReinput"),Target=Java.loadClass("io.github.fatalitaya.combatnexus.combat.ability.api.AbilityTarget$Self"),Params=Java.loadClass("io.github.fatalitaya.combatnexus.combat.ability.api.AbilityParameters");C.world(p).abilities().reinput(new RI(UUID.randomUUID(),p.getUUID(),UUID.fromString("'+setup['execution']+'"),new Target(),Params.EMPTY));JSON.stringify({ok:true})')
        time.sleep(.6)
        result=run('var uuid=Java.loadClass("java.util.UUID"),z=p.serverLevel().getEntity(uuid.fromString("'+setup['id']+'"));JSON.stringify({health:z.getHealth(),effects:String(C.world(p).effects().snapshot(z.getUUID())),flying:p.isFallFlying(),y:p.getY(),execution:String(C.world(p).abilities().execution(uuid.fromString("'+setup['execution']+'")))})')
        assert 'status=COMPLETED' in result['execution'],(name,result)
        if name in ['NULL','DRAGONSPINE_BREAKER','EVERNIGHT_STAR','CYANFROST_VIOLETVOLT']:assert result['health']<1000,(name,result)
        if name=='ANTARES':assert 'rewind_slow' in result['effects'],result
        if name=='MOON_MARROW':assert result['y']>-40 and result['flying'],result
        if name=='BIG_APPLE':assert 'slowness' in result['effects'],result
        print(name,'skill',{'health':result['health'],'completed':True,'flying':result['flying']},flush=True)
        run('p.serverLevel().getEntity(Java.loadClass("java.util.UUID").fromString("'+setup['id']+'")).discard();JSON.stringify({ok:true})')
