package petrolpark.mc.destroy.core.chemistry.data;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyPackets;
import petrolpark.mc.destroy.chemistry.legacy.LegacyElement;

/**
 * Server → client packet: sync the datapack-defined element set so multiplayer clients can
 * (a) parse FROWNS strings referencing custom elements, (b) render mixtures containing those
 * elements via {@code MoleculeRenderer}.
 *
 * <p>On the client, after applying the definitions to {@link LegacyElement#ELEMENTS}, the handler
 * resolves each element's {@code modelPath} into a {@link PartialModel} (lazy; the actual
 * baked model is fetched on first render from the client's resource manager — so the resource
 * pack containing the atom JSON model must be active before the molecule is rendered).</p>
 */
public record SyncElementsS2CPacket(Map<ResourceLocation, ElementDefinition> elements)
    implements ClientboundPacketPayload {

    @SuppressWarnings("unused")
    private static final Codec<Map.Entry<ResourceLocation, ElementDefinition>> ENTRY_CODEC =
        RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(Map.Entry::getKey),
            ElementDefinition.CODEC.fieldOf("def").forGetter(Map.Entry::getValue)
        ).apply(i, Map::entry));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncElementsS2CPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public SyncElementsS2CPacket decode(RegistryFriendlyByteBuf buffer) {
                int count = buffer.readVarInt();
                Map<ResourceLocation, ElementDefinition> map = new LinkedHashMap<>(count);
                for (int i = 0; i < count; i++) {
                    ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buffer);
                    ElementDefinition def = ByteBufCodecs.fromCodec(ElementDefinition.CODEC).decode(buffer);
                    map.put(id, def);
                }
                return new SyncElementsS2CPacket(map);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, SyncElementsS2CPacket packet) {
                buffer.writeVarInt(packet.elements.size());
                for (Map.Entry<ResourceLocation, ElementDefinition> e : packet.elements.entrySet()) {
                    ResourceLocation.STREAM_CODEC.encode(buffer, e.getKey());
                    ByteBufCodecs.fromCodec(ElementDefinition.CODEC).encode(buffer, e.getValue());
                }
            }
        };

    @Override
    public BasePacketPayload.PacketTypeProvider getTypeProvider() {
        return DestroyPackets.SYNC_ELEMENTS;
    }

    @Override
    public void handle(LocalPlayer player) {
        LegacyElement.clearDatapackElements();
        int ok = 0;
        int skipped = 0;
        for (Map.Entry<ResourceLocation, ElementDefinition> entry : elements.entrySet()) {
            try {
                if (entry.getValue().apply(entry.getKey())) ok++;
                else skipped++;
            } catch (Throwable t) {
                Destroy.LOGGER.warn("Failed to apply synced datapack element {}: {}",
                    entry.getKey(), t.getMessage());
                skipped++;
            }
        }
        // Attach PartialModel for each fresh datapack element so the molecule renderer can use it.
        for (LegacyElement element : LegacyElement.values()) {
            if (!element.isDatapack()) continue;
            ResourceLocation modelPath = element.getModelPath();
            if (modelPath != null) element.setPartial(PartialModel.of(modelPath));
        }
        Destroy.LOGGER.info("Received {} datapack element(s) from server; {} applied, {} skipped.",
            elements.size(), ok, skipped);
    }
}
