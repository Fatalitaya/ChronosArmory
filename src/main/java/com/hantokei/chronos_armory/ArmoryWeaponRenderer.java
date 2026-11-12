package com.hantokei.chronos_armory;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public final class ArmoryWeaponRenderer extends GeoItemRenderer<ArmoryWeaponItem> {
    public static final net.minecraft.client.resources.model.ModelResourceLocation NULL_GUI =
            net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                    ResourceLocation.fromNamespaceAndPath(ChronosArmory.MODID, "item/null_katana_gui"));

    @Override
    protected void renderInGui(net.minecraft.world.item.ItemDisplayContext context,
            com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers,
            int light, int overlay, float partialTick) {
        if (!((ArmoryWeaponItem) currentItemStack.getItem()).assetName().equals("null_katana")) {
            super.renderInGui(context, pose, buffers, light, overlay, partialTick);
            return;
        }
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        minecraft.getItemRenderer().render(currentItemStack, net.minecraft.world.item.ItemDisplayContext.GUI,
                false, pose, buffers, light, overlay, minecraft.getModelManager().getModel(NULL_GUI));
        pose.popPose();
    }

    public ArmoryWeaponRenderer(boolean glowing) {
        super(new GeoModel<ArmoryWeaponItem>() {
            private ResourceLocation resource(String path) {
                return ResourceLocation.fromNamespaceAndPath(ChronosArmory.MODID, path);
            }
            @Override
            public ResourceLocation getModelResource(ArmoryWeaponItem item) {
                return resource("geo/" + item.assetName() + ".geo.json");
            }
            @Override
            public ResourceLocation getTextureResource(ArmoryWeaponItem item) {
                return resource("textures/item/" + item.assetName() + ".png");
            }
            @Override
            public ResourceLocation getAnimationResource(ArmoryWeaponItem item) {
                return resource("animations/" + item.assetName() + ".animation.json");
            }
        });
        if (glowing) addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this));
    }
}
