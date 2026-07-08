package com.touchgrass.mod.worldgen;

import com.touchgrass.mod.ModeManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;

/**
 * One-time coastal terrain shaping for the Touch Grass realm.
 *
 * <p>The custom noise settings produce gentle rolling terrain that sits
 * roughly at y 64–67 everywhere. Because vanilla density functions offer no
 * X/Z coordinate reader, "ocean to the west" cannot be expressed in pure
 * JSON. This post-processor fills that gap: the first time the realm loads
 * inside a given world save it carves the western chunk strip into ocean and
 * smooths a sandy beach transition at the boundary. Subsequent calls —
 * including all player entries after the first — are no-ops.
 *
 * <p><b>Multiplayer safety:</b> completion is stored via
 * {@link PersistentState}, which is saved into the world's NBT data. The
 * operation therefore runs at most once per world save, regardless of how
 * many players enter the realm or how many times the server restarts.
 *
 * <p>Call {@link #ensureCoastalShape(ServerWorld)} from
 * {@code ModeManager.enterForcedMode()} <em>before</em> the scene origin is
 * located. The first call blocks the server tick briefly while generating
 * chunks; every subsequent call returns instantly.
 */
public final class CoastalTerrainModifier {

    private CoastalTerrainModifier() {}

    // Persistent-state key stored in the realm's world data.
    private static final String STATE_ID = "touchgrass_coastal_shaped";

    // Chunk-coordinate boundaries (inclusive).
    // Chunks at chunkX ≤ OCEAN_EAST_CX become open ocean.
    // Chunk at chunkX == BEACH_CX gets a tapered sandy beach on its east half.
    private static final int OCEAN_WEST_CX  = -7;
    private static final int OCEAN_EAST_CX  = -2;   // west of this → ocean
    private static final int BEACH_CX       = -1;   // beach transition chunk
    private static final int LAND_EAST_CX   =  8;   // pre-generate this far east
    private static final int CHUNK_Z_RANGE  =  6;   // ±Z chunks to pre-generate

    // Block Y constants derived from sea_level = 62.
    private static final int SEA_LEVEL     = 62;
    private static final int OCEAN_FLOOR_A = SEA_LEVEL - 2;  // 60 – upper sand
    private static final int OCEAN_FLOOR_B = SEA_LEVEL - 3;  // 59 – lower sand
    private static final int OCEAN_FLOOR_C = SEA_LEVEL - 4;  // 58 – sandstone

    // Flag notification flags for setBlockState: update clients + neighbours.
    private static final int NOTIFY = Block.NOTIFY_ALL;

    // -------------------------------------------------------------------------

    /**
     * Ensures the coastal shape has been applied to the dimension.
     * Safe to call from any server-thread context; completes synchronously on
     * first invocation, returns immediately on all subsequent calls.
     */
    public static void ensureCoastalShape(ServerWorld scene) {
        if (!scene.getRegistryKey().equals(ModeManager.TOUCH_GRASS_DIMENSION)) return;

        CoastalState state = scene.getPersistentStateManager()
                .getOrCreate(CoastalState.TYPE, STATE_ID);

        if (state.isShaped()) return;

        System.out.println("[touchgrass] Running one-time coastal terrain shaping…");

        // ── Step 1: force-generate all needed chunks ──────────────────────────
        for (int cx = OCEAN_WEST_CX; cx <= LAND_EAST_CX; cx++) {
            for (int cz = -CHUNK_Z_RANGE; cz <= CHUNK_Z_RANGE; cz++) {
                scene.getChunk(cx, cz);
            }
        }

        // ── Step 2: carve ocean columns ───────────────────────────────────────
        for (int cx = OCEAN_WEST_CX; cx <= OCEAN_EAST_CX; cx++) {
            for (int cz = -CHUNK_Z_RANGE; cz <= CHUNK_Z_RANGE; cz++) {
                carveFullOcean(scene, cx, cz);
            }
        }

        // ── Step 3: beach transition chunk ────────────────────────────────────
        // West half of chunk BEACH_CX → shallow ocean floor.
        // East half (lx ≥ 8) → sandy beach at y = SEA_LEVEL + 1.
        for (int cz = -CHUNK_Z_RANGE; cz <= CHUNK_Z_RANGE; cz++) {
            carveBeachChunk(scene, BEACH_CX, cz);
        }

        // ── Step 4: shore strip on the land side ─────────────────────────────
        // West columns of chunk 0 (lx 0..4) are lowered to beach height to
        // avoid an abrupt cliff at the ocean boundary.
        for (int cz = -CHUNK_Z_RANGE; cz <= CHUNK_Z_RANGE; cz++) {
            smoothLandShore(scene, 0, cz);
        }

        // ── Done ─────────────────────────────────────────────────────────────
        state.setShaped(true);
        state.markDirty();

        System.out.println("[touchgrass] Coastal terrain shaping complete.");
    }

    // -------------------------------------------------------------------------
    // Carving helpers
    // -------------------------------------------------------------------------

    /** Sinks every column in a chunk to open ocean (water + sand floor). */
    private static void carveFullOcean(ServerWorld scene, int cx, int cz) {
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = cx * 16 + lx;
                int wz = cz * 16 + lz;
                int topY = scene.getTopY(Heightmap.Type.WORLD_SURFACE, wx, wz);

                // Clear terrain above sea floor.
                for (int y = topY; y > OCEAN_FLOOR_A; y--) {
                    BlockState fill = (y <= SEA_LEVEL) ? Blocks.WATER.getDefaultState()
                                                       : Blocks.AIR.getDefaultState();
                    scene.setBlockState(new BlockPos(wx, y, wz), fill, NOTIFY);
                }
                // Lay ocean floor.
                scene.setBlockState(new BlockPos(wx, OCEAN_FLOOR_A, wz), Blocks.SAND.getDefaultState(),      NOTIFY);
                scene.setBlockState(new BlockPos(wx, OCEAN_FLOOR_B, wz), Blocks.SAND.getDefaultState(),      NOTIFY);
                scene.setBlockState(new BlockPos(wx, OCEAN_FLOOR_C, wz), Blocks.SANDSTONE.getDefaultState(), NOTIFY);
            }
        }
    }

    /**
     * Beach transition chunk: west half → shallow ocean, east half → sand
     * beach at y = SEA_LEVEL + 1 (one block above water, no flooding).
     */
    private static void carveBeachChunk(ServerWorld scene, int cx, int cz) {
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = cx * 16 + lx;
                int wz = cz * 16 + lz;
                int topY = scene.getTopY(Heightmap.Type.WORLD_SURFACE, wx, wz);
                int beachY = SEA_LEVEL + 1; // y = 63, just above water

                if (lx < 8) {
                    // Western half: shallow ocean (same as carveFullOcean).
                    for (int y = topY; y > OCEAN_FLOOR_A; y--) {
                        BlockState fill = (y <= SEA_LEVEL) ? Blocks.WATER.getDefaultState()
                                                           : Blocks.AIR.getDefaultState();
                        scene.setBlockState(new BlockPos(wx, y, wz), fill, NOTIFY);
                    }
                    scene.setBlockState(new BlockPos(wx, OCEAN_FLOOR_A, wz), Blocks.SAND.getDefaultState(),      NOTIFY);
                    scene.setBlockState(new BlockPos(wx, OCEAN_FLOOR_B, wz), Blocks.SAND.getDefaultState(),      NOTIFY);
                    scene.setBlockState(new BlockPos(wx, OCEAN_FLOOR_C, wz), Blocks.SANDSTONE.getDefaultState(), NOTIFY);
                } else {
                    // Eastern half: sandy beach, cleared above beachY.
                    for (int y = topY; y > beachY; y--) {
                        scene.setBlockState(new BlockPos(wx, y, wz), Blocks.AIR.getDefaultState(), NOTIFY);
                    }
                    scene.setBlockState(new BlockPos(wx, beachY,     wz), Blocks.SAND.getDefaultState(), NOTIFY);
                    scene.setBlockState(new BlockPos(wx, beachY - 1, wz), Blocks.SAND.getDefaultState(), NOTIFY);
                    scene.setBlockState(new BlockPos(wx, beachY - 2, wz), Blocks.SANDSTONE.getDefaultState(), NOTIFY);
                }
            }
        }
    }

    /**
     * Smooths the westernmost columns of the land chunk (lx 0..4) down to
     * sandy beach height so there's no abrupt cliff at the ocean boundary.
     */
    private static void smoothLandShore(ServerWorld scene, int cx, int cz) {
        for (int lx = 0; lx <= 4; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = cx * 16 + lx;
                int wz = cz * 16 + lz;
                int topY = scene.getTopY(Heightmap.Type.WORLD_SURFACE, wx, wz);
                int targetY = SEA_LEVEL + 1; // y = 63

                if (topY <= targetY) continue; // already at or below beach height

                // Shave terrain down to beach height.
                for (int y = topY; y > targetY; y--) {
                    scene.setBlockState(new BlockPos(wx, y, wz), Blocks.AIR.getDefaultState(), NOTIFY);
                }
                scene.setBlockState(new BlockPos(wx, targetY,     wz), Blocks.SAND.getDefaultState(), NOTIFY);
                scene.setBlockState(new BlockPos(wx, targetY - 1, wz), Blocks.SAND.getDefaultState(), NOTIFY);
            }
        }
    }

    // -------------------------------------------------------------------------
    // PersistentState — tracks completion across server restarts
    // -------------------------------------------------------------------------

    public static final class CoastalState extends PersistentState {

        private boolean shaped = false;

        /**
         * {@link PersistentState.Type} used by
         * {@code getPersistentStateManager().getOrCreate()}.
         * The third argument (DataFixTypes) is null because this data is
         * trivially versioned and never needs DFU migration.
         */
        public static final PersistentState.Type<CoastalState> TYPE =
                new PersistentState.Type<>(
                        CoastalState::new,
                        CoastalState::fromNbt,
                        null
                );

        public CoastalState() {}

        private static CoastalState fromNbt(NbtCompound nbt,
                                             RegistryWrapper.WrapperLookup lookup) {
            CoastalState s = new CoastalState();
            s.shaped = nbt.getBoolean("shaped");
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt,
                                     RegistryWrapper.WrapperLookup lookup) {
            nbt.putBoolean("shaped", shaped);
            return nbt;
        }

        public boolean isShaped()              { return shaped; }
        public void    setShaped(boolean value) { this.shaped = value; }
    }
}
