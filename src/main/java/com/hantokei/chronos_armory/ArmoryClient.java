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
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e){e.register(SKILL);e.register(ULTIMATE);}
    }
    @EventBusSubscriber(modid=ChronosArmory.MODID,value=Dist.CLIENT)
    public static final class Events {
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
