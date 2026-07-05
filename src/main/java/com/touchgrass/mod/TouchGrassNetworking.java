package com.touchgrass.mod;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public class TouchGrassNetworking {

    /**
     * Payload sent server → client whenever the player enters or exits forced mode.
     * true = entering (hide HUD), false = exiting (restore HUD).
     *
     * Uses the 1.21.x CustomPayload API instead of the old PacketByteBufs approach.
     */
    public record ModeChangePayload(boolean active) implements CustomPayload {

        public static final Id<ModeChangePayload> ID =
                new Id<>(Identifier.of("touchgrass", "mode_change"));

        public static final PacketCodec<PacketByteBuf, ModeChangePayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> buf.writeBoolean(value.active()),
                        buf -> new ModeChangePayload(buf.readBoolean())
                );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
