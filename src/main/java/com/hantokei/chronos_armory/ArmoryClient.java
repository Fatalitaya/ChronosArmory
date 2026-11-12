package com.hantokei.chronos_armory;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ArmoryClient {
    static final KeyMapping SKILL=new KeyMapping("key.chronos_armory.skill",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_R,"key.categories.chronos_armory");
    static final KeyMapping ULTIMATE=new KeyMapping("key.chronos_armory.ultimate",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_V,"key.categories.chronos_armory");
    static boolean held;
    @EventBusSubscriber(modid=ChronosArmory.MODID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void models(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional e) {
            e.register(ArmoryWeaponRenderer.NULL_GUI);
        }
        @SubscribeEvent public static void particles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent e) {
            e.registerSpriteSet(com.hantokei.chronos_armory.particles.ModParticles.BRIMSTONE.get(), com.hantokei.chronos_armory.particles.custom.BrimstoneParticle.Provider::new);
            e.registerSpriteSet(com.hantokei.chronos_armory.particles.ModParticles.NULL_BLADE.get(), com.hantokei.chronos_armory.particles.custom.NullBladeParticle.Provider::new);
        }
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e){e.register(SKILL);e.register(ULTIMATE);}
    }
    @EventBusSubscriber(modid=ChronosArmory.MODID,value=Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent
        public static void tooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent e) {
            if (!(e.getItemStack().getItem() instanceof ArmoryWeaponItem weapon)) return;
            var lines = e.getToolTip();
            lines.add(net.minecraft.network.chat.Component.translatable("tooltip.chronos_armory.controls",
                    SKILL.getTranslatedKeyMessage(), ULTIMATE.getTranslatedKeyMessage(), weapon.ultimateCharge())
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
            if (!net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                lines.add(net.minecraft.network.chat.Component.translatable("tooltip.chronos_armory.details")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
                return;
            }
            for (int i = 0; i < 4; i++) {
                var text = net.minecraft.network.chat.Component.translatable(weapon.getDescriptionId() + ".tooltip.skill" + i)
                        .withStyle(i < 2 ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.LIGHT_PURPLE);
                for (var line : Minecraft.getInstance().font.getSplitter().splitLines(
                        text, 300, text.getStyle())) {
                    lines.add(net.minecraft.network.chat.Component.literal(line.getString()).setStyle(text.getStyle()));
                }
            }
        }
        @SubscribeEvent public static void render(net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre e){if(ArmoryNetwork.HIDDEN.contains(e.getEntity().getUUID()))e.setCanceled(true);}
        @SubscribeEvent public static void tick(ClientTickEvent.Post e){
            var mc=Minecraft.getInstance();if(mc.player==null||mc.getConnection()==null){held=false;ArmoryNetwork.HIDDEN.clear();return;}
            boolean down=mc.screen==null&&SKILL.isDown();
            if(down&&!held)PacketDistributor.sendToServer(new ArmoryNetwork.Input(0));
            if(!down&&held)PacketDistributor.sendToServer(new ArmoryNetwork.Input(2));held=down;
            while(ULTIMATE.consumeClick())if(mc.screen==null)PacketDistributor.sendToServer(new ArmoryNetwork.Input(1));
        }
    }
}
