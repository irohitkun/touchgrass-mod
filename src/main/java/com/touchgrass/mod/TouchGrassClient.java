package com.touchgrass.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class TouchGrassClient implements ClientModInitializer {

    private static KeyBinding touchGrassKey;
    private static boolean touchGrassModeActive = false;

    public static boolean isTouchGrassModeActive() {
        return touchGrassModeActive;
    }

    @Override
    public void onInitializeClient() {
        touchGrassKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.touchgrass.trigger",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G, // default: G — rebind anytime in Controls menu
                "category.touchgrass"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (touchGrassKey.wasPressed()) {
                if (client.player != null) {
                    client.player.networkHandler.sendChatCommand("touchgrass now");
                }
            }
        });

        // Receive the server's signal to hide or restore the HUD.
        // Uses the 1.21.x CustomPayload receiver API.
        ClientPlayNetworking.registerGlobalReceiver(
                TouchGrassNetworking.ModeChangePayload.ID,
                (payload, context) ->
                        context.client().execute(() ->
                                touchGrassModeActive = payload.active()
                        )
        );
    }
}
