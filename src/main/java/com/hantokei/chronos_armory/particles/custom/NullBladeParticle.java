package com.hantokei.chronos_armory.particles.custom;

import com.hantokei.chronos_armory.pojo.NullBladeParticleOption;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class NullBladeParticle extends TextureSheetParticle {

    private final float fixedRoll;
    private final SpriteSet sprites;

    protected NullBladeParticle(ClientLevel level, double x, double y, double z,
                                float roll, SpriteSet sprites) {
        super(level, x, y, z);
        this.lifetime = 15;
        this.gravity = 0;
        this.fixedRoll = roll;
        this.quadSize = 1.5f;
        this.sprites = sprites;
        this.hasPhysics = false;
        // 关键：构造时立即初始化第0帧，防止首帧渲染时 sprite 为 null
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cameraPos = camera.getPosition();
        float x = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cameraPos.x);
        float y = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cameraPos.y);
        float z = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cameraPos.z);

        // 绕 Y 轴旋转实现水平径向朝向
        Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(fixedRoll));

        // 修复：顶点定义在 XY 平面（竖直），而非原来躺平的 XZ 平面
        // 这样粒子才能竖立显示"从右上到右下的光"特效
        Vector3f[] vertices = new Vector3f[]{
                new Vector3f(-1, -1, 0),  // 左下
                new Vector3f(-1,  1, 0),  // 左上
                new Vector3f( 1,  1, 0),  // 右上
                new Vector3f( 1, -1, 0)   // 右下
        };

        float size = this.getQuadSize(partialTicks);
        for (int i = 0; i < 4; i++) {
            vertices[i].mul(size);
            vertices[i].rotate(rotation);
            vertices[i].add(x, y, z);
        }

        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();

        int light = this.getLightColor(partialTicks);

        buffer.addVertex(vertices[0].x(), vertices[0].y(), vertices[0].z())
                .setUv(u1, v1).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
        buffer.addVertex(vertices[1].x(), vertices[1].y(), vertices[1].z())
                .setUv(u1, v0).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
        buffer.addVertex(vertices[2].x(), vertices[2].y(), vertices[2].z())
                .setUv(u0, v0).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
        buffer.addVertex(vertices[3].x(), vertices[3].y(), vertices[3].z())
                .setUv(u0, v1).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
    }

    @Override
    public void tick() {
        super.tick();
        // 按 age 顺序推进序列帧动画，从第0帧开始正确播放
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    // Provider 改为接收自定义 ParticleOptions，正确读取 roll 值
    public static class Provider implements ParticleProvider<NullBladeParticleOption> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(NullBladeParticleOption options, ClientLevel level,
                                       double x, double y, double z,
                                       double dx, double dy, double dz) {
            // 直接从 options 读取精确的 roll 值，完全绕开参数编码精度问题
            return new NullBladeParticle(level, x, y, z, options.roll(), this.sprites);
        }
    }
}
