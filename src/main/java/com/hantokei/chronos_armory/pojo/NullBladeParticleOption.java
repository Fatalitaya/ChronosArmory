package com.hantokei.chronos_armory.pojo;

import com.hantokei.chronos_armory.particles.ModParticles;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record NullBladeParticleOption(float roll) implements ParticleOptions {
    public static final MapCodec<NullBladeParticleOption> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.FLOAT.fieldOf("roll").forGetter(NullBladeParticleOption::roll)
            ).apply(instance, NullBladeParticleOption::new));

    public static final StreamCodec<ByteBuf, NullBladeParticleOption> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT,
                    NullBladeParticleOption::roll,
                    NullBladeParticleOption::new
            );

    @Override
    public ParticleType<?> getType() {
        return ModParticles.NULL_BLADE.get();
    }
}
