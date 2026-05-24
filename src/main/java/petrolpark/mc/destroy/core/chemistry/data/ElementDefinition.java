package petrolpark.mc.destroy.core.chemistry.data;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.chemistry.legacy.LegacyElement;
import petrolpark.mc.destroy.core.chemistry.MoleculeRenderer.Geometry;

/**
 * Datapack-serialisable description of a single chemical element. Loaded from
 * {@code data/<ns>/destroy/elements/<id>.json}.
 *
 * <p>Each definition becomes a {@link LegacyElement} datapack-mode constructor call at reload
 * time. The element's 3D model is resolved lazily on the client via
 * {@code PartialModel.of(modelPath)}; the resource pack ships the model + texture at the
 * stated path.</p>
 *
 * <p>VSEPR geometry can be overridden per-connection-count (e.g. force V_SHAPE on 2-connection
 * atoms like oxygen does). When omitted, the default connection-count → geometry mapping in
 * {@link LegacyElement#getGeometry} applies.</p>
 */
public record ElementDefinition(
    String symbol,
    float mass,
    float electronegativity,
    double[] valencies,
    Optional<ResourceLocation> model,
    Optional<GeometryOverride> geometryOverride
) {

    public static final Codec<ElementDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("symbol").forGetter(ElementDefinition::symbol),
        Codec.FLOAT.fieldOf("mass").forGetter(ElementDefinition::mass),
        Codec.FLOAT.optionalFieldOf("electronegativity", 0f).forGetter(ElementDefinition::electronegativity),
        Codec.DOUBLE.listOf().xmap(
            l -> l.stream().mapToDouble(Double::doubleValue).toArray(),
            arr -> java.util.stream.DoubleStream.of(arr).boxed().toList()
        ).fieldOf("valencies").forGetter(ElementDefinition::valencies),
        ResourceLocation.CODEC.optionalFieldOf("model").forGetter(ElementDefinition::model),
        GeometryOverride.CODEC.optionalFieldOf("geometry_override").forGetter(ElementDefinition::geometryOverride)
    ).apply(i, ElementDefinition::new));

    /**
     * Apply this definition by constructing a datapack-flagged {@link LegacyElement} and
     * registering it into {@code LegacyElement.ELEMENTS}. Returns true on success, false if the
     * id collides with a built-in element (datapacks may not shadow Java-side elements).
     */
    public boolean apply(ResourceLocation id) {
        LegacyElement existing = LegacyElement.ELEMENTS.get(id);
        if (existing != null && !existing.isDatapack()) {
            Destroy.LOGGER.warn("Skipping element {}: id collides with a built-in element", id);
            return false;
        }

        ResourceLocation modelPath = model.orElseGet(
            () -> ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "chemistry/atom/" + id.getPath()));

        java.util.function.Function<Integer, Geometry> geomFn = geometryOverride
            .map(GeometryOverride::toFunction)
            .orElse(null);

        // Constructor self-registers into ELEMENTS, replacing any prior datapack entry with the
        // same id (LinkedHashMap.put behaviour).
        new LegacyElement(id, symbol, mass, electronegativity, valencies, geomFn, modelPath);
        return true;
    }

    /**
     * Optional per-connection-count VSEPR override. Each non-null field tells the renderer to
     * pick that specific geometry when an atom of this element has that many bonded connections.
     * Null fields fall through to the standard VSEPR rule.
     *
     * <p>Example: oxygen with 2 connections should be {@code V_SHAPE} not {@code LINEAR} (water
     * shape, ether shape). Encoded as {@code { "two": "v_shape" }}.</p>
     */
    public record GeometryOverride(
        Optional<Geometry> one,
        Optional<Geometry> two,
        Optional<Geometry> three,
        Optional<Geometry> four,
        Optional<Geometry> five,
        Optional<Geometry> six
    ) {
        private static final Codec<Geometry> GEOMETRY_CODEC = Codec.STRING.xmap(
            s -> Geometry.valueOf(s.toUpperCase()),
            g -> g.name().toLowerCase()
        );

        public static final Codec<GeometryOverride> CODEC = RecordCodecBuilder.create(i -> i.group(
            GEOMETRY_CODEC.optionalFieldOf("one").forGetter(GeometryOverride::one),
            GEOMETRY_CODEC.optionalFieldOf("two").forGetter(GeometryOverride::two),
            GEOMETRY_CODEC.optionalFieldOf("three").forGetter(GeometryOverride::three),
            GEOMETRY_CODEC.optionalFieldOf("four").forGetter(GeometryOverride::four),
            GEOMETRY_CODEC.optionalFieldOf("five").forGetter(GeometryOverride::five),
            GEOMETRY_CODEC.optionalFieldOf("six").forGetter(GeometryOverride::six)
        ).apply(i, GeometryOverride::new));

        public java.util.function.Function<Integer, Geometry> toFunction() {
            return n -> switch (n) {
                case 1 -> one.orElse(null);
                case 2 -> two.orElse(null);
                case 3 -> three.orElse(null);
                case 4 -> four.orElse(null);
                case 5 -> five.orElse(null);
                case 6 -> six.orElse(null);
                default -> null;
            };
        }
    }
}
