# Touch Grass Mode — Fabric mod for Minecraft 1.21.2

A Fabric mod that tracks your playtime and forces a mandatory sunset break.
After a configurable limit it teleports you into a dedicated peaceful dimension:
golden-hour plains, a sea view, a small wooden jetty, poppies and sunflowers,
a friendly Grass Golem with rotating dialogue, and three illusion animals
(wolf, cat, parrot) that actually walk around and react to you. Attacking is
disabled, the HUD is hidden, ambient water sounds and vanilla music play, and
when the break ends you are returned to your exact position, facing, dimension,
and game mode.

---

## Setup — step by step

### What you need before you start
- **JDK 21** — download from [adoptium.net](https://adoptium.net/) and install it.
  Verify with `java -version` in a terminal; it should say `21.x.x`.
- **An internet connection** — Fabric Loom downloads Minecraft's own files on
  first build. This only happens once; after that you can build offline.
- The contents of this zip extracted to a folder on your computer.

### Building the mod
1. Open a terminal (Command Prompt / PowerShell on Windows, Terminal on Mac/Linux).
2. `cd` into the folder where you extracted this zip (the one that contains
   `build.gradle`).
3. Run:
   ```
   ./gradlew build
   ```
   On Windows use `gradlew.bat build` instead. The first run takes a few minutes
   because Loom downloads Minecraft's mappings. Subsequent builds are fast.
4. When it finishes you'll see `BUILD SUCCESSFUL`. Your jar is at:
   ```
   build/libs/touchgrass-mod-1.1.0.jar
   ```
   (Ignore any `-sources.jar` next to it — that one is not the mod.)

> **Tip — IntelliJ IDEA:** open the folder as a Gradle project and click the
> green ▶ button next to `build` in the Gradle panel instead of using the
> terminal. Either way works.

### Installing the mod
1. Install [Fabric Loader for 1.21.2](https://fabricmc.net/use/installer/) if
   you haven't already. Run the installer and choose version **1.21.2**.
2. Install [Fabric API for 1.21.2](https://modrinth.com/mod/fabric-api) — drop
   the `fabric-api-*.jar` into your `mods/` folder. Touch Grass Mode depends on
   Fabric API at runtime.
3. Drop `touchgrass-mod-1.1.0.jar` into the same `mods/` folder.
4. Launch Minecraft with the **fabric-loader-1.21.2** profile.

Default `mods/` folder locations:
| OS | Path |
|----|------|
| Windows | `%APPDATA%\.minecraft\mods` |
| macOS | `~/Library/Application Support/minecraft/mods` |
| Linux | `~/.minecraft/mods` |

### Configuring the mod (no recompile needed)
On first launch the mod writes a config file:
```
.minecraft/config/touchgrass/config.json
```
Open it with any text editor and change the values, then restart the game:

```json
{
  "reminderThresholdMinutes": 100,
  "reminderRepeatMinutes": 15,
  "titleReminderThresholdMinutes": 115,
  "forcedThresholdMinutes": 120,
  "forcedModeDurationMinutes": 10,
  "allowEarlyExit": false,
  "musicEventId": "minecraft:music.overworld.meadow",
  "persistPlaytime": true
}
```

| Field | What it does |
|-------|-------------|
| `reminderThresholdMinutes` | Minutes before the first soft chat reminder fires |
| `reminderRepeatMinutes` | How often (minutes) the soft reminder repeats after that |
| `titleReminderThresholdMinutes` | Minutes before forced mode at which the big title warning appears |
| `forcedThresholdMinutes` | Total minutes of play before the break is forced |
| `forcedModeDurationMinutes` | How long the break lasts |
| `allowEarlyExit` | Set to `true` to let players run `/touchgrass exit` |
| `musicEventId` | Any `minecraft:music.*` sound id — vanilla only, no bundled audio |
| `persistPlaytime` | `true` = playtime survives restarts (strongly recommended) |

### In-game commands
```
/touchgrass now              — trigger the break immediately
/touchgrass exit             — leave early (only works if allowEarlyExit is true)
/touchgrass set forcedMinutes <n> — change the forced threshold for this session
```

The **G** key (rebindable in Controls → Touch Grass) also triggers the break
manually, same as `/touchgrass now`.

---

## How the break works

1. A soft chat message appears after `reminderThresholdMinutes`.
2. A full title-screen warning appears `titleReminderThresholdMinutes` minutes in.
3. At `forcedThresholdMinutes` the break is forced automatically.
4. You are teleported to the Touch Grass dimension: permanent golden-hour sunset,
   grassy plains with flowers and sunflowers, a small wooden jetty, a sea to the
   south, no monsters.
5. Your HUD is hidden. Attacking is disabled. A vanilla music track plays and
   gentle water ambience loops every ~15 seconds.
6. A Grass Golem stands near the jetty and offers a line of dialogue on arrival,
   then a new line every ~40 seconds.
7. A wolf, cat, and parrot spawn spread around you. Every ~8 seconds one of them
   approaches you, sits nearby, looks at you, or makes a sound depending on how
   close it is. They use normal vanilla AI between those moments so they wander
   and feel alive rather than frozen.
8. After `forcedModeDurationMinutes` you are returned to your exact position,
   facing direction, dimension, and game mode.

---

## What changed from the original 1.20.1 scaffold

### Required for 1.21.2 (breaking changes)

| Change | Detail |
|--------|--------|
| Java 21 | `build.gradle` target changed from 17 to 21; `touchgrass.mixins.json` compatibility level updated to `JAVA_21` |
| Fabric Loom 1.8 | Plugin version bumped from 1.6 |
| All dependency versions | `gradle.properties` updated to matching 1.21.2 set |
| `fabric.mod.json` | Minecraft constraint changed from `1.20.x` to `~1.21.2`; loader from `>=0.15.0` to `>=0.16.0` |
| Custom networking payload | Old `PacketByteBufs` API was removed in 1.21. `TouchGrassNetworking` is now a `CustomPayload` record with its own `PacketCodec`. `TouchGrassMod` registers it with `PayloadTypeRegistry.playS2C()`. Client receiver updated to the new `(payload, context) ->` signature |
| Flat generator `structure_overrides` | Added `"structure_overrides": []` to the dimension JSON — required field in 1.21.x or the world refuses to load |

### Improvements from the review

| Change | Detail |
|--------|--------|
| JSON config file | `TouchGrassConfig` now loads from `config/touchgrass/config.json` on startup and writes defaults on first run. Edit the file instead of recompiling |
| Playtime restart-proof logging | `PlaytimeStore` now logs how many players' data was loaded on server start so you can confirm persistence is working |
| Richer scene | Added sunflowers, denser inland flora, a small oak plank jetty with fence posts, sandstone under the seabed, and a slightly larger footprint (radius 20 vs 16) |
| Active animal behaviour | Animals spawn spread out and immediately walk toward you. The tick reaction now has three tiers: within 5 blocks (sit/look or ambient sound), 5–10 blocks (saunter back), beyond 10 blocks (trot back). Vanilla AI runs between ticks so they feel alive |
| Golem dialogue | 6 rotating lines, one on arrival then one every ~40 seconds during the break |
| Ambient water pitch variation | Water splash sound pitch randomised slightly each time so it doesn't feel looped |

---

## Known rough edges

- **Existing worlds:** the dimension registers when the world is created with the
  mod already installed. Adding the mod to an existing world requires running
  `/reload` or regenerating the world.
- **No sleep / respawn anchor** in the Touch Grass dimension — intentional,
  it is a temporary scene, not a base.
- **`sceneBuilt` flag** resets between server restarts. The scene rebuilds on
  the first forced-mode entry each session, which is harmless (same blocks,
  same positions).
- **Animal reactivity** rides on top of vanilla TameableEntity AI. They navigate
  toward you and react every ~8s but won't do anything wilder than vanilla tamed
  mobs can do.

## Shaders and resource packs

The mod creates its mood with vanilla tools only: fixed golden-hour time,
locked clear weather, scene composition, ambient sounds, and reactive NPCs.
Shaders (Iris + Complementary Reimagined, BSL, etc.) and resource packs are
entirely your choice layered on top — bundling them would raise licensing issues
the mod shouldn't own.

## Music

`ModeManager` triggers a vanilla sound-registry id (default
`minecraft:music.overworld.meadow`), configurable in `config.json`. No audio
file is bundled — every install already ships vanilla music.
