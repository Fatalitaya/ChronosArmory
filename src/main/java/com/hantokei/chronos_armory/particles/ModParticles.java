package com.hantokei.chronos_armory.particles;

import com.hantokei.chronos_armory.ChronosArmory;
import com.hantokei.chronos_armory.pojo.NullBladeParticleOption;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, ChronosArmory.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BRIMSTONE =
            PARTICLE_TYPES.register("brimstone_layer", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, ParticleType<NullBladeParticleOption>> NULL_BLADE =
            PARTICLE_TYPES.register("null_blade", () ->
                    new ParticleType<NullBladeParticleOption>(false) {
                        @Override
                        public MapCodec<NullBladeParticleOption> codec() {
                            return NullBladeParticleOption.CODEC;
                        }

                        @Override
                        public StreamCodec<? super RegistryFriendlyByteBuf, NullBladeParticleOption> streamCodec() {
                            return NullBladeParticleOption.STREAM_CODEC.cast();
                        }
                    }
            );
}
