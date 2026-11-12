package com.hantokei.chronos_armory;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;

@Mod(ChronosArmory.MODID)
public final class ChronosArmory {
    public static final String MODID = "chronos_armory";

    public ChronosArmory(IEventBus modBus) {
        ArmoryItems.ITEMS.register(modBus);
        com.hantokei.chronos_armory.particles.ModParticles.PARTICLE_TYPES.register(modBus);
        com.hantokei.chronos_armory.sounds.ModSounds.register(modBus);
        ArmoryItems.TABS.register(modBus);
        modBus.addListener(ArmoryNetwork::register);
        var gameBus=net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
        gameBus.addListener(ArmoryAbilities::register);
        gameBus.addListener(ArmoryPassives::register);
        gameBus.addListener(ArmoryPassives::eat);
        gameBus.addListener(ArmoryRuntime::tick);
        gameBus.addListener(ArmoryRuntime::leave);
        gameBus.addListener(ArmoryRuntime::dimension);
        gameBus.addListener(ArmoryRuntime::clone);
        gameBus.addListener(ArmoryRuntime::attack);
        gameBus.addListener(ArmoryRuntime::target);
        gameBus.addListener(ArmoryRuntime::died);
        gameBus.addListener(ArmoryRuntime::stopped);
    }
}
