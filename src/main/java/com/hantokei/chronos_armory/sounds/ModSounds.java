package com.hantokei.chronos_armory.sounds;

import com.hantokei.chronos_armory.ChronosArmory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, ChronosArmory.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> NULL_BLADE_SOUND =
            SOUND_EVENTS.register("null_blade_skill_sound",
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ChronosArmory.MODID, "null_blade_skill_sound")));

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
