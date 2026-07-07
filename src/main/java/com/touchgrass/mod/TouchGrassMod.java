package com.touchgrass.mod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.server.command.CommandManager.literal;

public class TouchGrassMod implements ModInitializer {

    public static final String MOD_ID = "touchgrass";

    private final Map<UUID, Long> ticksPlayed = new HashMap<>();
    private final Map<UUID, Long> lastReminderTick = new HashMap<>();
    private final Set<UUID> titleReminderFired = ConcurrentHashMap.newKeySet();

    private static final int TICKS_PER_MINUTE = 20 * 60;
    private static final int SAVE_INTERVAL_TICKS = TICKS_PER_MINUTE * 2;
    private int saveCountdown = SAVE_INTERVAL_TICKS;

    @Override
    public void onInitialize() {
        // Register the S2C custom payload type so 1.21.x knows how to encode/decode it.
        PayloadTypeRegistry.playS2C().register(
                TouchGrassNetworking.ModeChangePayload.ID,
                TouchGrassNetworking.ModeChangePayload.CODEC
        );

        if (TouchGrassConfig.INSTANCE.persistPlaytime) {
            ServerLifecycleEvents.SERVER_STARTED.register(server -> PlaytimeStore.load());
            ServerLifecycleEvents.SERVER_STOPPING.register(server -> PlaytimeStore.save());
        }

        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
                ModeManager.resetServerState()
        );

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();

            if (TouchGrassConfig.INSTANCE.persistPlaytime) {
                ticksPlayed.put(
                        player.getUuid(),
                        PlaytimeStore.getTicks(player.getUuid())
                );
            }

            ModeManager.handleJoin(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ModeManager.handleDisconnect(handler.getPlayer())
        );

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            TouchGrassConfig cfg = TouchGrassConfig.INSTANCE;

            ModeManager.tickScene(server);

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID id = player.getUuid();

                if (ModeManager.isInForcedMode(player)) {
                    ModeManager.tickForcedMode(player);
                    continue;
                }

                long ticks = ticksPlayed.merge(id, 1L, Long::sum);
                long minutes = ticks / TICKS_PER_MINUTE;

                if (minutes >= cfg.forcedThresholdMinutes) {
                    ModeManager.enterForcedMode(player);
                    ticksPlayed.put(id, 0L);
                    if (cfg.persistPlaytime) PlaytimeStore.setTicks(id, 0L);
                    lastReminderTick.remove(id);
                    titleReminderFired.remove(id);
                    continue;
                }

                if (minutes >= cfg.titleReminderThresholdMinutes && titleReminderFired.add(id)) {
                    int minutesLeft = cfg.forcedThresholdMinutes - (int) minutes;
                    player.networkHandler.sendPacket(new TitleS2CPacket(
                            Text.literal("§6Time's almost up")));
                    player.networkHandler.sendPacket(new SubtitleS2CPacket(
                            Text.literal("§7Forced break in about " + minutesLeft + " min")));
                } else if (minutes >= cfg.reminderThresholdMinutes) {
                    long lastReminder = lastReminderTick.getOrDefault(id, -1L);
                    long sinceLast = lastReminder < 0 ? Long.MAX_VALUE : ticks - lastReminder;
                    if (sinceLast >= (long) cfg.reminderRepeatMinutes * TICKS_PER_MINUTE) {
                        player.sendMessage(Text.literal(
                                "§a[Touch Grass] §fYou've been playing for " + minutes +
                                " minutes. Maybe step outside soon \uD83C\uDF31"), false);
                        lastReminderTick.put(id, ticks);
                    }
                }

                if (cfg.persistPlaytime) PlaytimeStore.setTicks(id, ticks);
            }

            if (cfg.persistPlaytime && --saveCountdown <= 0) {
                saveCountdown = SAVE_INTERVAL_TICKS;
                PlaytimeStore.save();
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("touchgrass")
                .then(literal("now").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    ModeManager.enterForcedMode(player);
                    return 1;
                }))
                .then(literal("exit").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    if (TouchGrassConfig.INSTANCE.allowEarlyExit) {
                        ModeManager.exitForcedMode(player);
                    } else {
                        player.sendMessage(
                                Text.literal("§c[Touch Grass] §fEarly exit is disabled. Ride it out."), false);
                    }
                    return 1;
                }))
                .then(literal("set").then(literal("forcedMinutes")
                    .then(CommandManager.argument("minutes", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                            TouchGrassConfig.INSTANCE.forcedThresholdMinutes = minutes;
                            ctx.getSource().sendFeedback(() -> Text.literal(
                                "Forced touch-grass mode now triggers after " + minutes + " minutes."), true);
                            return 1;
                        })))));
        });
    }
}
