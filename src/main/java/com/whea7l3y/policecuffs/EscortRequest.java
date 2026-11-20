package com.whea7l3y.policecuffs;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record EscortRequest(int action) implements CustomPacketPayload {
    public static final Type<EscortRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath(PoliceCuffsMod.MODID, "escort"));
    public static final StreamCodec<ByteBuf, EscortRequest> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, EscortRequest::action, EscortRequest::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
