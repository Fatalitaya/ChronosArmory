package com.hantokei.chronos_armory;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ArmoryNetwork {
    public record Input(int action) implements CustomPacketPayload {
        public static final Type<Input> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ChronosArmory.MODID,"skill_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Input> CODEC=ByteBufCodecs.VAR_INT.map(Input::new,Input::action).cast();
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Hidden(java.util.UUID player,boolean hidden) implements CustomPacketPayload {
        public static final Type<Hidden> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ChronosArmory.MODID,"hidden"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Hidden> CODEC=StreamCodec.composite(net.minecraft.core.UUIDUtil.STREAM_CODEC,Hidden::player,ByteBufCodecs.BOOL,Hidden::hidden,Hidden::new);
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static final java.util.Set<java.util.UUID> HIDDEN=new java.util.HashSet<>();
    public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("1").playToClient(Hidden.TYPE,Hidden.CODEC,(packet,context)->context.enqueueWork(()->{if(packet.hidden)HIDDEN.add(packet.player);else HIDDEN.remove(packet.player);}));event.registrar("1").playToServer(Input.TYPE,Input.CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer p&&packet.action>=0&&packet.action<=2)ArmoryAbilities.input(p,packet.action);}));}
}
