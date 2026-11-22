package com.whea7l3y.policecuffs;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
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
        Minecraft minecraft = Minecraft.getInstance();
        while (ESCORT_KEY.consumeClick()) {
            if (minecraft.player != null) ClientPacketDistributor.sendToServer(new EscortRequest(1));
        }

        // The local detainee is normally authoritative for their own movement.
        // Predict escort motion on their client too, so it is visible in real time.
        if (minecraft.level == null || minecraft.player == null) return;
        var prisoner = minecraft.player;
        UUID officerId = prisoner.getData(PoliceCuffsMod.ESCORT_OFFICER);
        if (!PoliceCuffsMod.isCuffed(prisoner) || PoliceCuffsMod.NO_ESCORT.equals(officerId)) return;

        Player officer = null;
        for (Player candidate : minecraft.level.players()) {
            if (candidate.getUUID().equals(officerId)) {
                officer = candidate;
                break;
            }
        }
        if (officer == null || officer.level() != prisoner.level()) return;

        Vec3 destination = PoliceCuffsMod.escortPosition(officer);
        Vec3 delta = PoliceCuffsMod.escortStep(prisoner.position(), destination);
        if (delta.lengthSqr() > 0.0) {
            prisoner.move(MoverType.PLAYER, delta);
        }
        prisoner.setDeltaMovement(Vec3.ZERO);
        prisoner.setSprinting(false);
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
        model.leftSleeve.xRot = model.leftArm.xRot;
        model.leftSleeve.yRot = model.leftArm.yRot;
        model.leftSleeve.zRot = model.leftArm.zRot;
        model.rightSleeve.xRot = model.rightArm.xRot;
        model.rightSleeve.yRot = model.rightArm.yRot;
        model.rightSleeve.zRot = model.rightArm.zRot;
    }
}
