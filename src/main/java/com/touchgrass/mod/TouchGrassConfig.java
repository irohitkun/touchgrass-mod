package com.touchgrass.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Config loaded from config/touchgrass/config.json on server start.
 * If the file doesn't exist, defaults are written so the player can find
 * and edit the file without recompiling. All fields are documented inline.
 *
 * Swap for Cloth Config later for a real in-game settings screen.
 */
public class TouchGrassConfig {

    /** Minutes of continuous playtime before the first subtle chat reminder. */
    public int reminderThresholdMinutes = 100;

    /** How often (minutes) to repeat the subtle reminder after it first fires. */
    public int reminderRepeatMinutes = 15;

    /** Minutes before forced mode at which a stronger on-screen title warning fires (once). */
    public int titleReminderThresholdMinutes = 115;

    /** Minutes of continuous playtime before forced mode kicks in. */
    public int forcedThresholdMinutes = 120;

    /** How long forced mode lasts (real minutes) before releasing the player. */
    public int forcedModeDurationMinutes = 10;

    /** Whether players can use /touchgrass exit to leave early. */
    public boolean allowEarlyExit = false;

    /**
     * Vanilla sound event id played as background music during forced mode.
     * Must be a registry id that already ships with vanilla — never point
     * this at a bundled audio file. Any minecraft:music.* id works here.
     */
    public String musicEventId = "minecraft:music.overworld.meadow";

    /** Whether playtime survives server/game restarts (written to playtime.json). */
    public boolean persistPlaytime = true;

    // -------------------------------------------------------------------------

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of("config", "touchgrass", "config.json");

    public static final TouchGrassConfig INSTANCE = load();

    private static TouchGrassConfig load() {
        if (Files.exists(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                TouchGrassConfig cfg = GSON.fromJson(r, TouchGrassConfig.class);
                if (cfg != null) {
                    System.out.println("[touchgrass] Config loaded from " + FILE);
                    return cfg;
                }
            } catch (IOException e) {
                System.err.println("[touchgrass] Failed to read config.json, using defaults: " + e.getMessage());
            }
        }

        // Write defaults so the player knows what's available to edit.
        TouchGrassConfig defaults = new TouchGrassConfig();
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(defaults, w);
            }
            System.out.println("[touchgrass] Default config written to " + FILE + " — edit it to customise the mod.");
        } catch (IOException e) {
            System.err.println("[touchgrass] Could not write default config.json: " + e.getMessage());
        }
        return defaults;
    }
}
