# 🌱 Touch Grass Mod

A Fabric mod for Minecraft 1.21.2 that reminds you to take a break after long play sessions and, if configured, sends you into a peaceful Touch Grass experience for a while.

Because apparently closing Minecraft voluntarily was too difficult, so now the mod handles the intervention.

> [!WARNING]
> Touch Grass Mode is currently in early development.
>
> The latest release is an **alpha build** and contains known issues. It is mainly intended for testing and development feedback.

## 📥 Download

### Latest Release: v1.1.0 Alpha

Download the latest build from the Releases page:

**[Download Touch Grass Mode v1.1.0](../../releases/tag/v1.1.0)**

### Requirements

- Minecraft 1.21.2
- Fabric Loader 0.16.9 or newer
- Fabric API
- Java 21

## 🌅 What is Touch Grass Mode?

Touch Grass Mode tracks how long you have been playing Minecraft.

As your playtime increases, the mod can:

1. Send a subtle reminder in chat.
2. Display a stronger warning as the configured limit approaches.
3. Automatically activate Touch Grass Mode after the configured playtime limit.
4. Move the player into a dedicated peaceful dimension.
5. Return the player to their previous location after the break ends.

The goal is to create a small, relaxing interruption to long Minecraft sessions rather than simply showing another notification that everyone will immediately ignore.

## ✨ Current Features

- Configurable playtime tracking
- Persistent playtime between game restarts
- Subtle chat reminders
- On-screen warning before forced mode
- Automatic Touch Grass Mode activation
- Manual activation with the `G` key
- Dedicated Touch Grass dimension
- Fixed sunset atmosphere
- Clear weather during the break
- Adventure Mode during the experience
- Player attacks disabled during Touch Grass Mode
- Wolf, cat, and parrot companions
- Grass Golem companion with periodic dialogue
- Background music using vanilla Minecraft sound events
- Ambient environmental sounds
- Configurable break duration
- Optional early exit
- Automatic return to the player's:
  - previous dimension
  - previous position
  - previous viewing direction
  - previous game mode

## 🎮 Commands

### Enter Touch Grass Mode manually

```text
/touchgrass now
