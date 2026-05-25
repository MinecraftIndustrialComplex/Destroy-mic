package petrolpark.mc.destroy.core.chemistry.data;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;

import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.chemistry.legacy.LegacyElement;

/**
 * Reload listener for datapack-defined chemical elements. Loads
 * {@code data/<ns>/destroy/elements/<name>.json} and registers each into
 * {@link LegacyElement#ELEMENTS}.
 *
 * <p>MUST register BEFORE {@code MoleculeDataReloadListener}: datapack molecules' FROWNS
 * strings reference elements by symbol via {@link LegacyElement#fromSymbol}, so elements must
 * exist when the molecule listener applies.</p>
 */
public class ElementDataReloadListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    public static volatile Map<ResourceLocation, ElementDefinition> LAST_LOADED = Map.of();

    public ElementDataReloadListener() {
        super(GSON, "destroy/elements");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager,
                          ProfilerFiller profiler) {
        LegacyElement.clearDatapackElements();

        Map<ResourceLocation, ElementDefinition> loaded = new LinkedHashMap<>();
        int ok = 0;
        int skipped = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                ElementDefinition def = ElementDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                    .getOrThrow(msg -> new JsonParseException("Decode error: " + msg));
                if (def.apply(id)) {
                    loaded.put(id, def);
                    ok++;
                } else {
                    skipped++;
                }
            } catch (Throwable t) {
                Destroy.LOGGER.warn("Failed to load datapack element {}: {}", id, t.getMessage());
                skipped++;
            }
        }
        LAST_LOADED = loaded;
        Destroy.LOGGER.info("Loaded {} datapack element(s); {} skipped.", ok, skipped);

        try {
            CatnipServices.NETWORK.sendToAllClients(new SyncElementsS2CPacket(loaded));
        } catch (NullPointerException e) {
            // Expected during server startup before player network infrastructure is up.
        }
    }
}
