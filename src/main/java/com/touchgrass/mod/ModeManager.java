package com.touchgrass.mod;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;

import java.util.*;

/**
 * Owns the whole "forced grass mode" experience: teleports the player into
 * the custom Touch Grass dimension, builds the sunset scene once, locks
 * weather, spawns a golem and illusion animals that actively react to the
 * player, hides the HUD, and returns the player to their exact previous
 * state when the break ends.
 */
public class ModeManager {

    public static final String MODE_TAG      = "touchgrass_active";
    public static final String ILLUSION_TAG  = "touchgrass_illusion";

    public static final RegistryKey<net.minecraft.world.World> TOUCH_GRASS_DIMENSION =
            RegistryKey.of(RegistryKeys.WORLD,
                    Identifier.of("touchgrass", "touch_grass_realm"));

    // Scene origin in the dedicated dimension. Fixed coords never collide with
    // anything a player has built because this dimension is only for this purpose.
    private static final BlockPos PLATFORM_ORIGIN = new BlockPos(0, 5, 0);

    private static BlockPos getSceneOrigin(ServerWorld scene) {
        int centerX = PLATFORM_ORIGIN.getX();
        int centerZ = PLATFORM_ORIGIN.getZ();
        int searchRadius = 32;

        for (int radius = 0; radius <= searchRadius; radius++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {

                    // Only inspect the outer edge of this search ring.
                    if (radius > 0
                            && x != centerX - radius
                            && x != centerX + radius
                            && z != centerZ - radius
                            && z != centerZ + radius) {
                        continue;
                    }

                    ChunkPos chunkPos = new ChunkPos(new BlockPos(x, 0, z));
                    scene.getChunk(chunkPos.x, chunkPos.z);

                    int y = scene.getTopY(
                            Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                            x,
                            z
                    );

                    BlockPos feet = new BlockPos(x, y, z);
                    BlockPos ground = feet.down();

                    boolean grassGround =
                            scene.getBlockState(ground).isOf(Blocks.GRASS_BLOCK);

                    boolean feetClear =
                            scene.getBlockState(feet).isAir();

                    boolean headClear =
                            scene.getBlockState(feet.up()).isAir();

                    if (grassGround && feetClear && headClear) {
                        return feet;
                    }
                }
            }
        }

        // Fallback if no ideal grass location was found nearby.
        int fallbackY = scene.getTopY(
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                centerX,
                centerZ
        );

        return new BlockPos(centerX, fallbackY, centerZ);
    }

    private static final int  TICKS_PER_MINUTE = 20 * 60;
    private static final Random RANDOM = new Random();

    // -- Per-player state -----------------------------------------------------
    private static final Map<UUID, PlayerState>                      saved          = new HashMap<>();
    private static final Map<UUID, Integer>                          remainingTicks = new HashMap<>();
    private static final Map<UUID, List<net.minecraft.entity.Entity>> spawned        = new HashMap<>();

    private record PlayerState(BlockPos pos, float yaw, float pitch,
                                String dimension, GameMode mode) {}

    // -- Golem dialogue lines shown on the chat rail during the break ----------
    private static final String[] GOLEM_LINES = {
        "§2Grass Golem: §f\"Water's nice this time of day, huh.\"",
        "§2Grass Golem: §f\"Take your time. The pixels will still be there.\"",
        "§2Grass Golem: §f\"The cat seems to like you. High praise.\"",
        "§2Grass Golem: §f\"You ever just watch the light move? Try it.\"",
        "§2Grass Golem: §f\"No rush. Breathe for a bit.\"",
        "§2Grass Golem: §f\"The poppies weren't here yesterday. Or maybe they were.\"",
    };
    private static final Map<UUID, Integer> golemLineIndex = new HashMap<>();

    // -------------------------------------------------------------------------

    public static boolean isInForcedMode(ServerPlayerEntity player) {
        return player.getCommandTags().contains(MODE_TAG);
    }

    public static void enterForcedMode(ServerPlayerEntity player) {
        if (isInForcedMode(player)) return;

        ServerWorld fromWorld = player.getServerWorld();
        saved.put(player.getUuid(), new PlayerState(
                player.getBlockPos(),
                player.getYaw(), player.getPitch(),
                fromWorld.getRegistryKey().getValue().toString(),
                player.interactionManager.getGameMode()));

        ServerWorld scene = player.getServer().getWorld(TOUCH_GRASS_DIMENSION);
        if (scene == null) {
            player.sendMessage(Text.literal(
                    "§c[Touch Grass] Dimension failed to load — make sure the data/ files " +
                    "under data/touchgrass/dimension* are packaged into the jar."), false);
            return;
        }

        // Find the generated terrain surface before building the scene.
        BlockPos sceneOrigin = getSceneOrigin(scene);

        System.out.println("[touchgrass] SPAWN CHECK origin=" + sceneOrigin
                + " ground=" + scene.getBlockState(sceneOrigin.down())
                + " feet=" + scene.getBlockState(sceneOrigin)
                + " head=" + scene.getBlockState(sceneOrigin.up()));
        System.out.println("[touchgrass] DEBUG sceneOrigin = " + sceneOrigin
                + ", bottomY = " + scene.getBottomY()
                + ", topY = " + scene.getTopYInclusive());
        // ensureSceneBuilt temporarily disabled while testing natural terrain

        // Lock clear weather for the duration — no rain breaking the mood.
        scene.setWeather(0, 6000 * 20, false, false);

        // Face the player west toward the sunset.
        player.teleport(scene,
                sceneOrigin.getX() + 0.5,
                sceneOrigin.getY(),
                sceneOrigin.getZ() + 0.5,
                Collections.emptySet(),
                90F, 0F,
                false);

        player.addCommandTag(MODE_TAG);
        player.changeGameMode(GameMode.ADVENTURE); // no block breaking/placing
        remainingTicks.put(player.getUuid(),
                TouchGrassConfig.INSTANCE.forcedModeDurationMinutes * TICKS_PER_MINUTE);
        golemLineIndex.put(player.getUuid(), 1);

        spawnIllusionAnimals(scene, player, sceneOrigin);
        spawnGolem(scene, player, sceneOrigin);
        setHudHidden(player, true);

        player.sendMessage(Text.literal(
                "§6[Touch Grass] §fOkay, that's enough. Sit here, watch the sunset, " +
                "and hang out with the animals for a bit."), false);
        player.networkHandler.sendPacket(
                new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(20, 100, 30));

        player.networkHandler.sendPacket(
                new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
                        Text.literal("§eTouch Grass Mode")));
        player.networkHandler.sendPacket(
                new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
                        Text.literal("§7Attacking is disabled. Just breathe.")));

        // Background music — configurable vanilla registry id, never a bundled file.
        SoundEvent music = Registries.SOUND_EVENT.get(
                Identifier.of(TouchGrassConfig.INSTANCE.musicEventId));
        if (music == null) music = SoundEvents.MUSIC_OVERWORLD_MEADOW.value();
        scene.playSound(null, sceneOrigin, music, SoundCategory.MUSIC, 1.0F, 1.0F);

        // Gentle water ambience on entry.
        scene.playSound(null, sceneOrigin,
                SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundCategory.AMBIENT, 0.5F, 1.0F);
    }

    public static void tickForcedMode(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        int left = remainingTicks.getOrDefault(id, 0) - 1;
        remainingTicks.put(id, left);

        ServerWorld scene = player.getServerWorld();

        // Gentle water splash every ~15 s to reinforce the seaside ambience.
        if (left % (20 * 15) == 0 && left > 0) {
            scene.playSound(null, player.getBlockPos(),
                    SoundEvents.ENTITY_FISHING_BOBBER_SPLASH,
                    SoundCategory.AMBIENT, 0.4F, 0.9F + RANDOM.nextFloat() * 0.2F);
        }

        // Every ~8 s pick a random animal and make it react to the player.
        if (left % (20 * 8) == 0 && left > 0) {
            reactRandomAnimal(scene, player);
        }

        // Golem cycles through its dialogue lines every ~40 s.
        if (left % (20 * 40) == 0 && left > 0) {
            int idx = golemLineIndex.getOrDefault(id, 0);
            player.sendMessage(Text.literal(GOLEM_LINES[idx % GOLEM_LINES.length]), false);
            golemLineIndex.put(id, idx + 1);
        }

        if (left <= 0) {
            exitForcedMode(player);
        }
    }

    public static void exitForcedMode(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        PlayerState state = saved.remove(id);
        remainingTicks.remove(id);
        golemLineIndex.remove(id);
        player.removeCommandTag(MODE_TAG);
        setHudHidden(player, false);

        if (state != null) {
            RegistryKey<net.minecraft.world.World> key =
                    RegistryKey.of(RegistryKeys.WORLD,
                            Identifier.of(state.dimension()));
            ServerWorld returnWorld = player.getServer().getWorld(key);
            if (returnWorld != null) {
                player.teleport(returnWorld,
                        state.pos().getX() + 0.5,
                        state.pos().getY(),
                        state.pos().getZ() + 0.5,
                        Collections.emptySet(),
                        state.yaw(), state.pitch(),
                        false);
            }
            player.changeGameMode(state.mode());
        }

        List<net.minecraft.entity.Entity> mobs = spawned.remove(id);
        if (mobs != null) mobs.forEach(net.minecraft.entity.Entity::discard);

        player.sendMessage(
                Text.literal("§a[Touch Grass] §fWelcome back. Hope that helped."), false);
    }

    // -------------------------------------------------------------------------
    // Scene construction
    // -------------------------------------------------------------------------

    private static boolean sceneBuilt = false;

    /**
     * Builds the sunset plains-and-sea scene once. The dimension stays empty
     * superflat otherwise, so this small footprint never collides with anything.
     *
     * Layout (south = positive Z = toward the sea):
     *   Z < -8  grass + flowers + occasional short grass
     *   -8..4   grass platform where the player stands
     *   Z > 4   water with sand floor
     */
    private static void ensureSceneBuilt(ServerWorld scene, BlockPos sceneOrigin) {
        if (sceneBuilt) return;
        int radius = 20;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos base = sceneOrigin.add(x, 0, z);

                if (z > 4) {
                    // Sea
                    scene.setBlockState(base, Blocks.WATER.getDefaultState());
                    scene.setBlockState(base.down(), Blocks.SAND.getDefaultState());
                    scene.setBlockState(base.down(2), Blocks.SANDSTONE.getDefaultState());
                } else {
                    // Grass land
                    scene.setBlockState(base, Blocks.GRASS_BLOCK.getDefaultState());
                    scene.setBlockState(base.down(), Blocks.DIRT.getDefaultState());

                    int hash = (x * 31 + z * 17) & 0x7FFFFFFF;

                    if (z < -8) {
                        // Further inland: denser flora
                        if (hash % 5 == 0)       scene.setBlockState(base.up(), Blocks.POPPY.getDefaultState());
                        else if (hash % 7 == 0)  scene.setBlockState(base.up(), Blocks.DANDELION.getDefaultState());
                        else if (hash % 11 == 0) scene.setBlockState(base.up(), Blocks.SUNFLOWER.getDefaultState());
                        else if (hash % 3 == 0)  scene.setBlockState(base.up(), Blocks.SHORT_GRASS.getDefaultState());
                    } else {
                        // Middle zone: sparser, the player can walk around
                        if (hash % 9 == 0)       scene.setBlockState(base.up(), Blocks.SHORT_GRASS.getDefaultState());
                        else if (hash % 13 == 0) scene.setBlockState(base.up(), Blocks.POPPY.getDefaultState());
                    }
                }
            }
        }

        // Small wooden jetty pointing out toward the sea (cosmetic, walkable in Adventure).
        for (int dz = 1; dz <= 5; dz++) {
            BlockPos plank = sceneOrigin.add(0, 0, dz);
            scene.setBlockState(plank, Blocks.OAK_PLANKS.getDefaultState());
        }
        // Fence posts on either side of the jetty for detail.
        for (int dz = 1; dz <= 4; dz += 3) {
            scene.setBlockState(sceneOrigin.add(-1, 0, dz), Blocks.OAK_FENCE.getDefaultState());
            scene.setBlockState(sceneOrigin.add( 1, 0, dz), Blocks.OAK_FENCE.getDefaultState());
        }

        sceneBuilt = true;
    }

    // -------------------------------------------------------------------------
    // Animal behaviour
    // -------------------------------------------------------------------------

    private static void spawnIllusionAnimals(
            ServerWorld world,
            ServerPlayerEntity player,
            BlockPos sceneOrigin
    ) {
        List<net.minecraft.entity.Entity> list =
                spawned.computeIfAbsent(player.getUuid(), k -> new ArrayList<>());

        // Spread animals out around the player so they can wander freely.
        WolfEntity   dog    = EntityType.WOLF.create(world, SpawnReason.TRIGGERED);
        CatEntity    cat    = EntityType.CAT.create(world, SpawnReason.TRIGGERED);
        ParrotEntity parrot = EntityType.PARROT.create(world, SpawnReason.TRIGGERED);

        int[][] offsets = {{-3, 2}, {3, 2}, {0, -3}};
        int i = 0;
        for (var mob : List.of(dog, cat, parrot)) {
            if (mob == null) { i++; continue; }
            int[] off = offsets[i];

            int spawnX = sceneOrigin.getX() + off[0];
            int spawnZ = sceneOrigin.getZ() + off[1];
            int spawnY = world.getTopY(
                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    spawnX,
                    spawnZ
            );

            mob.refreshPositionAndAngles(
                    spawnX + 0.5,
                    spawnY,
                    spawnZ + 0.5,
                    (float) RANDOM.nextInt(360), 0F);
            mob.addCommandTag(ILLUSION_TAG);
            if (mob instanceof TameableEntity tameable) {
                // Cosmetic tame — bypasses the feeding mechanic so it persists
                // exactly as set and vanishes cleanly when the mode ends.
                tameable.setOwnerUuid(player.getUuid());
                tameable.setSitting(false);
            }
            world.spawnEntity(mob);
            list.add(mob);
            i++;
        }

        // Have each animal immediately drift toward the player at a relaxed walk.
        for (var entity : list) {
            if (entity instanceof net.minecraft.entity.mob.MobEntity mob) {
                mob.getNavigation().startMovingTo(player, 0.5);
            }
        }
    }

    /**
     * Called every ~8 s during forced mode to make one random animal react.
     * Behaviour varies depending on how close the animal is to the player:
     * - nearby  → 50 % sit and look, 50 % make an ambient sound then unsit
     * - distant → nudge it to walk back toward the player
     */
    private static void reactRandomAnimal(ServerWorld world, ServerPlayerEntity player) {
        List<net.minecraft.entity.Entity> mobs = spawned.get(player.getUuid());
        if (mobs == null || mobs.isEmpty()) return;

        var pick = mobs.get(RANDOM.nextInt(mobs.size()));
        if (!(pick instanceof TameableEntity tameable) || !tameable.isAlive()) return;

        double distSq = tameable.squaredDistanceTo(player);

        if (distSq < 25) {          // within ~5 blocks
            if (RANDOM.nextBoolean()) {
                tameable.setSitting(true);
                tameable.getLookControl().lookAt(player, 30F, 30F);
            } else {
                tameable.setSitting(false);
                SoundEvent sound =
                        tameable instanceof WolfEntity ? SoundEvents.ENTITY_WOLF_AMBIENT :
                        tameable instanceof CatEntity ? SoundEvents.ENTITY_CAT_AMBIENT :
                        tameable instanceof ParrotEntity ? SoundEvents.ENTITY_PARROT_AMBIENT :
                        null;

                if (sound != null) {
                    world.playSound(null, tameable.getBlockPos(),
                            sound, SoundCategory.NEUTRAL, 1.0F,
                            0.9F + RANDOM.nextFloat() * 0.2F);
                }
            }
        } else if (distSq < 100) {  // 5–10 blocks — saunter back
            tameable.setSitting(false);
            tameable.getNavigation().startMovingTo(player, 0.45);
        } else {                     // wandered far — trot back a bit faster
            tameable.setSitting(false);
            tameable.getNavigation().startMovingTo(player, 0.7);
        }
    }

    private static void spawnGolem(
            ServerWorld world,
            ServerPlayerEntity player,
            BlockPos sceneOrigin
    ) {
        IronGolemEntity golem = EntityType.IRON_GOLEM.create(world, SpawnReason.TRIGGERED);
        if (golem == null) return;
        int spawnX = sceneOrigin.getX() + 1;
        int spawnZ = sceneOrigin.getZ() - 3;
        int spawnY = world.getTopY(
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                spawnX,
                spawnZ
        );

        golem.refreshPositionAndAngles(
                spawnX + 0.5,
                spawnY,
                spawnZ + 0.5,
                180F, 0F);
        golem.setCustomName(Text.literal("§2Grass Golem"));
        golem.setCustomNameVisible(true);
        golem.addCommandTag(ILLUSION_TAG);
        golem.setPlayerCreated(true); // stops it wandering off to guard a village
        world.spawnEntity(golem);
        spawned.computeIfAbsent(player.getUuid(), k -> new ArrayList<>()).add(golem);

        // Opening line shown on entry.
        player.sendMessage(Text.literal(GOLEM_LINES[0]), false);
    }

    // -------------------------------------------------------------------------
    // HUD toggle
    // -------------------------------------------------------------------------

    private static void setHudHidden(ServerPlayerEntity player, boolean hidden) {
        ServerPlayNetworking.send(player,
                new TouchGrassNetworking.ModeChangePayload(hidden));
    }
}
