package petrolpark.mc.destroy.config;

import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;

import org.apache.commons.lang3.tuple.Pair;

import net.createmod.catnip.config.ConfigBase;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber
public class DestroyConfigs {
    
    private static final Map<ModConfig.Type, ConfigBase> CONFIGS = new EnumMap<>(ModConfig.Type.class);

	private static DestroyClientConfigs client;
	private static DestroyCommonConfigs common;
	private static DestroyServerConfigs server;

	public static DestroyClientConfigs client() {
		return client;
	};

	public static DestroyCommonConfigs common() {
		return common;
	};

	public static DestroyServerConfigs server() {
		return server;
	};

	/**
	 * Read an int-valued config, returning the supplied fallback if the config isn't loaded yet.
	 *
	 * <p>Other mods (e.g. FramedBlocks' camo-factory discovery on {@code DataMapsUpdatedEvent})
	 * iterate every block in the registry and call {@code getCapability(FluidHandler.ITEM)}
	 * during world-load resource reload — BEFORE NeoForge has applied the server config. Any
	 * Destroy item capability that touches a {@code ConfigValue.get()} in that path crashes with
	 * {@code IllegalStateException: Cannot get config value before config is loaded.} (see
	 * BEAKER / ROUND_BOTTOMED_FLASK / MEASURING_CYLINDER capacity readers).</p>
	 *
	 * <p>This helper swallows that specific exception and substitutes the fallback (typically the
	 * config's own default). Once the config finishes loading the supplier returns the real value
	 * on subsequent invocations.</p>
	 */
	public static int safeInt(java.util.function.IntSupplier reader, int fallback) {
		try {
			return reader.getAsInt();
		} catch (IllegalStateException e) {
			return fallback;
		}
	}

	public static ConfigBase byType(ModConfig.Type type) {
		return CONFIGS.get(type);
	};

	private static <T extends ConfigBase> T register(Supplier<T> factory, ModConfig.Type side) {
		Pair<T, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(builder -> {
			T config = factory.get();
			config.registerAll(builder);
			return config;
		});

		T config = specPair.getLeft();
		config.specification = specPair.getRight();
		CONFIGS.put(side, config);
		return config;
	};

	public static void register(ModLoadingContext context, ModContainer container) {
		client = register(DestroyClientConfigs::new, ModConfig.Type.CLIENT);
		common = register(DestroyCommonConfigs::new, ModConfig.Type.COMMON);
		server = register(DestroyServerConfigs::new, ModConfig.Type.SERVER);

		DestroyAllConfigs.link(client, common, server);

		for (Entry<ModConfig.Type, ConfigBase> pair : CONFIGS.entrySet()) container.registerConfig(pair.getKey(), pair.getValue().specification);

		// CStress stress = server().kinetics.stressValues;
		// BlockStressValues.IMPACTS.registerProvider(stress::getImpact);
		// BlockStressValues.CAPACITIES.registerProvider(stress::getCapacity);
	};

	@SubscribeEvent
	public static void onLoad(ModConfigEvent.Loading event) {
		for (ConfigBase config : CONFIGS.values()) if (config.specification == event.getConfig().getSpec()) config.onLoad();
	};

	@SubscribeEvent
	public static void onReload(ModConfigEvent.Reloading event) {
		for (ConfigBase config : CONFIGS.values()) if (config.specification == event.getConfig().getSpec()) config.onReload();
	};
};
