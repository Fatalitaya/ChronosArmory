package com.hantokei.chronos_armory.particles.custom;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

public class BrimstoneParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected BrimstoneParticle(ClientLevel level, double x, double y, double z,
                                double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        this.setSpriteFromAge(sprites);
        this.lifetime = 60;
        this.scale(1.0f);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType spt, ClientLevel level,
                                       double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new BrimstoneParticle(level, x, y, z, vx, vy, vz, this.sprites);
        }
    }
}
