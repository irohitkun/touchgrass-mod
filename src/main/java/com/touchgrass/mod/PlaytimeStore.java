package com.touchgrass.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists per-player playtime (ticks since last forced break) to
 * config/touchgrass/playtime.json, so quitting and relaunching Minecraft
 * — or restarting a server — doesn't reset the clock.
 *
 * Loaded once at server start, saved every ~2 minutes and on shutdown.
 */
public class PlaytimeStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of("config", "touchgrass", "playtime.json");

    private static Map<String, Long> ticksByPlayer = new HashMap<>();
    private static boolean dirty = false;

    public static synchronized void load() {
        ticksByPlayer = new HashMap<>();
        if (!Files.exists(FILE)) return;
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            Map<String, Long> loaded =
                    GSON.fromJson(reader, new TypeToken<Map<String, Long>>() {}.getType());
            if (loaded != null) ticksByPlayer = loaded;
            System.out.println("[touchgrass] Playtime loaded for " + ticksByPlayer.size() + " player(s).");
        } catch (IOException e) {
            System.err.println("[touchgrass] Failed to load playtime.json: " + e.getMessage());
        }
    }

    public static synchronized void save() {
        if (!dirty) return;
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(ticksByPlayer, writer);
            }
            dirty = false;
        } catch (IOException e) {
            System.err.println("[touchgrass] Failed to save playtime.json: " + e.getMessage());
        }
    }

    public static synchronized long getTicks(UUID id) {
        return ticksByPlayer.getOrDefault(id.toString(), 0L);
    }

    public static synchronized void setTicks(UUID id, long ticks) {
        ticksByPlayer.put(id.toString(), ticks);
        dirty = true;
    }
}
