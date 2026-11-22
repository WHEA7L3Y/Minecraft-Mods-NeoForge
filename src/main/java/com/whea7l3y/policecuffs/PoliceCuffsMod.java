package com.whea7l3y.policecuffs;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Mod(PoliceCuffsMod.MODID)
public class PoliceCuffsMod {
    public static final String MODID = "policecuffs";
    private static final Logger LOGGER = LoggerFactory.getLogger("Police Cuffs");
    private static final Identifier SPEED_MODIFIER_ID = Identifier.fromNamespaceAndPath(MODID, "cuffed_speed");
    // A sync-only state: the prisoner knows who is escorting them, so their own
    // client can predict smooth movement instead of waiting for teleport packets.
    public static final UUID NO_ESCORT = new UUID(0L, 0L);
    public static final double ESCORT_DISTANCE = 1.5;
    public static final double ESCORT_MAX_STEP = 0.45; // blocks per tick (9 m/s)


    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredItem<Item> HANDCUFFS = ITEMS.registerSimpleItem("handcuffs", properties -> properties.stacksTo(1));
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);
    public static final Supplier<AttachmentType<Boolean>> CUFFED = ATTACHMENT_TYPES.register(
            "cuffed", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL.fieldOf("cuffed"))
                    .sync(ByteBufCodecs.BOOL)
                    .build());
    public static final Supplier<AttachmentType<UUID>> ESCORT_OFFICER = ATTACHMENT_TYPES.register(
            "escort_officer", () -> AttachmentType.builder(() -> NO_ESCORT)
                    .sync(UUIDUtil.STREAM_CODEC)
                    .build());

    // Prisoner UUID -> officer UUID. Escort state does not persist across server restarts.
    private static final Map<UUID, UUID> ESCORTS = new HashMap<>();

    public PoliceCuffsMod(IEventBus modBus) {
        ITEMS.register(modBus);
        ATTACHMENT_TYPES.register(modBus);
        modBus.addListener(PoliceCuffsMod::addCreative);
        modBus.addListener(PoliceCuffsMod::registerNetwork);
        NeoForge.EVENT_BUS.register(PoliceCuffsMod.class);
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) event.accept(HANDCUFFS);
    }

    private static void registerNetwork(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(EscortRequest.TYPE, EscortRequest.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer officer) toggleEscort(officer);
        });
    }

    public static boolean isCuffed(Player player) {
        return player.getData(CUFFED);
    }

    // Diagnostic: verify recipes really loaded on the dedicated/integrated server,
    // not merely included in the mod JAR.
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        var recipes = event.getServer().getRecipeManager();
        var shapedId = ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath(MODID, "handcuffs"));
        var shapelessId = ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath(MODID, "handcuffs_flexible"));
        boolean shapedLoaded = recipes.byKey(shapedId).isPresent();
        boolean shapelessLoaded = recipes.byKey(shapelessId).isPresent();
        LOGGER.info("Comprobacion de recetas Police Cuffs: handcuffs={}, handcuffs_flexible={}",
                shapedLoaded ? "OK" : "NO CARGADA", shapelessLoaded ? "OK" : "NO CARGADA");
        if (!shapedLoaded || !shapelessLoaded) {
            LOGGER.error("Falta una receta del mod. Revisa el archivo logs/latest.log del servidor.");
        }
    }

    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (isCuffed(player)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().is(HANDCUFFS.get())
                || !(event.getTarget() instanceof Player prisoner)) return;
        if (player instanceof ServerPlayer officer && prisoner instanceof ServerPlayer captive) {
            if (officer != captive && !officer.isSpectator() && !captive.isSpectator()
                    && officer.distanceToSqr(captive) < 16.0) {
                boolean next = !isCuffed(captive);
                captive.setData(CUFFED, next);
                if (!next) stopEscort(captive);
                updateSpeed(captive);
                officer.sendSystemMessage(Component.literal(next ? "Has esposado a " + captive.getName().getString()
                        : "Has liberado a " + captive.getName().getString()));
                captive.sendSystemMessage(Component.literal(next ? "Te han esposado. No puedes interactuar."
                        : "Ya no llevas esposas."));
            }
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInteractEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isCuffed(event.getEntity())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isCuffed(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isCuffed(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isCuffed(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (isCuffed(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() != null && isCuffed(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer prisoner)) return;
        AttributeInstance attribute = prisoner.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null && (isCuffed(prisoner) != (attribute.getModifier(SPEED_MODIFIER_ID) != null))) {
            updateSpeed(prisoner);
        }
        UUID officerId = ESCORTS.get(prisoner.getUUID());
        if (officerId == null) return;
        ServerPlayer officer = prisoner.level().getServer().getPlayerList().getPlayer(officerId);
        if (!isCuffed(prisoner) || !prisoner.isAlive() || officer == null || !officer.isAlive()
                || officer.isRemoved() || officer.level() != prisoner.level()
                || officer.distanceToSqr(prisoner) > 100.0) {
            stopEscort(prisoner);
            return;
        }

        // Move a limited distance each tick, WITH vanilla collision handling.
        // Calling connection.teleport() every tick floods the client with teleport
        // handshakes and prevents the detainee's visible movement from updating.
        Vec3 destination = escortPosition(officer);
        Vec3 delta = escortStep(prisoner.position(), destination);
        if (delta.lengthSqr() > 0.0) {
            prisoner.move(net.minecraft.world.entity.MoverType.PLAYER, delta);
        }
        prisoner.setDeltaMovement(Vec3.ZERO);
        prisoner.setSprinting(false);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID left = event.getEntity().getUUID();
        var detainees = ESCORTS.entrySet().stream()
                .filter(entry -> entry.getKey().equals(left) || entry.getValue().equals(left))
                .map(Map.Entry::getKey).toList();
        for (UUID id : detainees) {
            ServerPlayer prisoner = event.getEntity().level().getServer().getPlayerList().getPlayer(id);
            if (prisoner != null) stopEscort(prisoner);
            else ESCORTS.remove(id);
        }
    }

    /** The detainee is in FRONT of the officer, at the same ground-level height. */
    public static Vec3 escortPosition(Player officer) {
        Vec3 direction = officer.getLookAngle();
        Vec3 flat = new Vec3(direction.x, 0.0, direction.z);
        if (flat.lengthSqr() < 1.0E-5) {
            double radians = Math.toRadians(officer.getYRot());
            flat = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
        }
        return officer.position().add(flat.normalize().scale(ESCORT_DISTANCE));
    }

    /** Smooth, bounded following motion; never jump immediately to the target. */
    public static Vec3 escortStep(Vec3 current, Vec3 target) {
        Vec3 delta = target.subtract(current);
        double lengthSq = delta.lengthSqr();
        if (lengthSq < 0.0004) return Vec3.ZERO;
        return lengthSq > ESCORT_MAX_STEP * ESCORT_MAX_STEP
                ? delta.normalize().scale(ESCORT_MAX_STEP)
                : delta;
    }

    public static void toggleEscort(ServerPlayer officer) {
        if (isCuffed(officer) || officer.isSpectator()) return;
        UUID officerId = officer.getUUID();
        UUID currentlyHeld = null;
        for (Map.Entry<UUID, UUID> entry : ESCORTS.entrySet()) {
            if (entry.getValue().equals(officerId)) {
                currentlyHeld = entry.getKey();
                break;
            }
        }
        if (currentlyHeld != null) {
            ServerPlayer captive = officer.level().getServer().getPlayerList().getPlayer(currentlyHeld);
            if (captive != null) stopEscort(captive);
            else ESCORTS.remove(currentlyHeld);
            officer.sendSystemMessage(Component.literal("Has dejado de escoltar al detenido."));
            return;
        }
        ServerPlayer selected = null;
        double closest = 9.0;
        for (ServerPlayer candidate : officer.level().players()) {
            if (candidate == officer || !isCuffed(candidate) || ESCORTS.containsKey(candidate.getUUID())) continue;
            double distanceSq = officer.distanceToSqr(candidate);
            if (distanceSq >= closest) continue;
            Vec3 fromCaptiveToOfficer = officer.position().subtract(candidate.position());
            Vec3 looking = candidate.getLookAngle();
            if (fromCaptiveToOfficer.x * looking.x + fromCaptiveToOfficer.z * looking.z > -0.25) continue;
            closest = distanceSq;
            selected = candidate;
        }
        if (selected == null) {
            officer.sendSystemMessage(Component.literal("Ponte detrás de un jugador esposado (máximo 3 bloques)."));
            return;
        }
        ESCORTS.put(selected.getUUID(), officerId);
        selected.setData(ESCORT_OFFICER, officerId); // synchronize to captive's client
        updateSpeed(selected);
        officer.sendSystemMessage(Component.literal("Escoltando a " + selected.getName().getString() + ". Pulsa G para soltarlo."));
        selected.sendSystemMessage(Component.literal("Un policía te está escoltando."));
    }

    private static void stopEscort(ServerPlayer prisoner) {
        if (ESCORTS.remove(prisoner.getUUID()) != null) {
            prisoner.setData(ESCORT_OFFICER, NO_ESCORT);
            prisoner.setDeltaMovement(Vec3.ZERO);
            updateSpeed(prisoner);
        }
    }

    private static void updateSpeed(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(SPEED_MODIFIER_ID);
        if (isCuffed(player)) {
            double reduction = ESCORTS.containsKey(player.getUUID()) ? -1.0 : -0.85;
            speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID, reduction,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
