package dev.funtime.pe.protocol;

import dev.funtime.pe.world.BedrockItemMapper;
import dev.funtime.pe.world.JavaItemStack;

import java.io.IOException;

/**
 * Codec for the compact Bedrock item stack shape used by inventory and
 * equipment packets. Extra item components are deliberately not fabricated;
 * they will be added when the 26.50 item palette/recipe registry is decoded.
 */
public final class BedrockItemStackCodec {
    private BedrockItemStackCodec() {
    }

    public static JavaItemStack read(BedrockBuffer.Reader reader) throws IOException {
        final int runtimeId = reader.readVarInt();
        if (runtimeId == 0) {
            return JavaItemStack.empty();
        }
        final int count = reader.readUnsignedShortLittleEndian();
        final int damage = reader.readUnsignedShortLittleEndian();
        // Bedrock carries a block runtime id after the item metadata. It is
        // needed for block items but is not the Java-facing item identity.
        reader.readVarInt();
        return new JavaItemStack(BedrockItemMapper.name(runtimeId), count, damage);
    }

    public static void write(BedrockBuffer buffer, JavaItemStack stack) {
        final JavaItemStack safe = stack == null ? JavaItemStack.empty() : stack;
        final int runtimeId = safe.isEmpty() ? 0 : BedrockItemMapper.runtimeId(safe.itemId());
        buffer.writeVarInt(runtimeId);
        if (runtimeId == 0) {
            return;
        }
        buffer.writeShortLittleEndian(safe.count());
        buffer.writeShortLittleEndian(safe.damage());
        buffer.writeVarInt(0);
    }
}