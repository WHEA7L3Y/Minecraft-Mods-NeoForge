package com.whea7l3y.policecuffs;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = PoliceCuffsMod.MODID, value = Dist.CLIENT)
public final class PoliceCuffsClient {
    private static final KeyMapping ESCORT_KEY = new KeyMapping(
            "key.policecuffs.escort", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, KeyMapping.Category.MISC);

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ESCORT_KEY);
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        while (ESCORT_KEY.consumeClick()) {
            if (Minecraft.getInstance().player != null) PacketDistributor.sendToServer(new EscortRequest(1));
        }
    }

    @SubscribeEvent
    public static void renderCuffedArms(RenderPlayerEvent.Pre<?> event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Entity entity = minecraft.level.getEntity(event.getRenderState().id);
        if (!(entity instanceof Player player) || !PoliceCuffsMod.isCuffed(player)) return;
        PlayerModel model = event.getRenderer().getModel();
        model.leftArm.xRot = 0.90F;
        model.rightArm.xRot = 0.90F;
        model.leftArm.yRot = 0.48F;
        model.rightArm.yRot = -0.48F;
        model.leftArm.zRot = 0.12F;
        model.rightArm.zRot = -0.12F;
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
    }
}
