package petrolpark.mc.destroy.chemistry.legacy;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nullable;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

import petrolpark.mc.destroy.core.chemistry.MoleculeRenderer.Geometry;

/**
 * Chemical element definition for Destroy's legacy chemistry engine. Each element carries its
 * atomic symbol, mass, electronegativity, common valencies, optional VSEPR geometry override,
 * and a {@link ResourceLocation} pointing to the 3D model used by {@link MoleculeRenderer}.
 *
 * <p>Built-in elements are declared as {@code public static final} constants below — their
 * static initialisers self-register into {@link #ELEMENTS} via the constructor. Datapack-loaded
 * elements register through the same constructor at reload time (via
 * {@code ElementDataReloadListener}) and carry {@link #datapack} = true so they can be cleared
 * on the next reload without disturbing the built-in set.</p>
 *
 * <p>This was a Java enum prior to Phase 2b; the refactor preserves the {@link #name()},
 * {@link #values()}, and {@link #fromSymbol(String)} surface so call sites compile unchanged.</p>
 */
public class LegacyElement implements Comparable<LegacyElement> {

    /** Registry of every known element keyed by full ID. Populated by the constants below at
     * class-init and by {@code ElementDataReloadListener} for datapack-loaded entries.
     * MUST be declared BEFORE the static-final constants so they have somewhere to register.*/
    public static final Map<ResourceLocation, LegacyElement> ELEMENTS = new LinkedHashMap<>();

    /** Declaration-order counter; replaces the auto-generated enum ordinal. Used by
     * {@link #compareTo} so {@code Comparator.naturalOrder()} sorts produce the same
     * empirical-formula ordering as the original enum (R/C/H/S/N/O/B/F/Na/Cl/K/...).*/
    private static int NEXT_ORDINAL = 0;
    private final int ordinal;

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("destroy", path);
    }

    private static ResourceLocation atomModel(String path) {
        return ResourceLocation.fromNamespaceAndPath("destroy", "chemistry/atom/" + path);
    }

    // In the order they should appear in empirical formulae
    public static final LegacyElement R_GROUP    = new LegacyElement("R_GROUP",    rl("r_group"),   "R",  0.0001f, 2.5f,  new double[]{1, 2, 3}, null, null);
    public static final LegacyElement CARBON     = new LegacyElement("CARBON",     rl("carbon"),    "C",  12.01f,  2.5f,  new double[]{4},          null, atomModel("carbon"));
    public static final LegacyElement HYDROGEN   = new LegacyElement("HYDROGEN",   rl("hydrogen"),  "H",  1.01f,   2.1f,  new double[]{1},          null, atomModel("hydrogen"));
    public static final LegacyElement SULFUR     = new LegacyElement("SULFUR",     rl("sulfur"),    "S",  32.07f,  2.5f,  new double[]{2, 0, 4, 6}, null, atomModel("sulfur"));
    public static final LegacyElement NITROGEN   = new LegacyElement("NITROGEN",   rl("nitrogen"),  "N",  14.01f,  3.0f,  new double[]{3, 4},       null, atomModel("nitrogen"));
    public static final LegacyElement OXYGEN     = new LegacyElement("OXYGEN",     rl("oxygen"),    "O",  16.00f,  3.5f,  new double[]{0, 1.5d, 2}, i -> i == 2 ? Geometry.V_SHAPE : null, atomModel("oxygen"));
    public static final LegacyElement BORON      = new LegacyElement("BORON",      rl("boron"),     "B",  10.81f,  2.04f, new double[]{3d},         null, atomModel("boron"));
    public static final LegacyElement FLUORINE   = new LegacyElement("FLUORINE",   rl("fluorine"),  "F",  19.00f,  4.0f,  new double[]{1},          null, atomModel("fluorine"));
    public static final LegacyElement SODIUM     = new LegacyElement("SODIUM",     rl("sodium"),    "Na", 23.00f,  0.9f,  new double[]{1},          null, atomModel("sodium"));
    public static final LegacyElement CHLORINE   = new LegacyElement("CHLORINE",   rl("chlorine"),  "Cl", 35.45f,  3.0f,  new double[]{1},          null, atomModel("chlorine"));
    public static final LegacyElement POTASSIUM  = new LegacyElement("POTASSIUM",  rl("potassium"), "K",  39.10f,  0.8f,  new double[]{1},          null, atomModel("potassium"));
    public static final LegacyElement CALCIUM    = new LegacyElement("CALCIUM",    rl("calcium"),   "Ca", 40.08f,  1.0f,  new double[]{2},          null, atomModel("calcium"));
    public static final LegacyElement CHROMIUM   = new LegacyElement("CHROMIUM",   rl("chromium"),  "Cr", 52.00f,  1.66f, new double[]{2d, 3d, 6d}, null, atomModel("chromium"));
    public static final LegacyElement IRON       = new LegacyElement("IRON",       rl("iron"),      "Fe", 55.85f,  1.8f,  new double[]{0, 2, 3},    null, atomModel("iron"));
    public static final LegacyElement NICKEL     = new LegacyElement("NICKEL",     rl("nickel"),    "Ni", 58.69f,  1.8f,  new double[]{1},          null, atomModel("nickel"));
    public static final LegacyElement COPPER     = new LegacyElement("COPPER",     rl("copper"),    "Cu", 63.55f,  1.9f,  new double[]{1, 2},       null, atomModel("copper"));
    public static final LegacyElement ZINC       = new LegacyElement("ZINC",       rl("zinc"),      "Zn", 65.38f,  1.6f,  new double[]{1},          null, atomModel("zinc"));
    public static final LegacyElement ZIRCONIUM  = new LegacyElement("ZIRCONIUM",  rl("zirconium"), "Zr", 91.22f,  1.4f,  new double[]{1},          null, atomModel("zirconium"));
    public static final LegacyElement IODINE     = new LegacyElement("IODINE",     rl("iodine"),    "I",  126.90f, 2.7f,  new double[]{1},          null, atomModel("iodine"));
    public static final LegacyElement PLATINUM   = new LegacyElement("PLATINUM",   rl("platinum"),  "Pt", 195.08f, 2.2f,  new double[]{1},          null, atomModel("platinum"));
    public static final LegacyElement GOLD       = new LegacyElement("GOLD",       rl("gold"),      "Au", 196.97f, 2.4f,  new double[]{0, 4},       null, atomModel("gold"));
    public static final LegacyElement MERCURY    = new LegacyElement("MERCURY",    rl("mercury"),   "Hg", 200.59f, 1.9f,  new double[]{2},          null, atomModel("mercury"));
    public static final LegacyElement LEAD       = new LegacyElement("LEAD",       rl("lead"),      "Pb", 207.20f, 1.8f,  new double[]{2, 4},       null, atomModel("lead"));
    public static final LegacyElement ARGON      = new LegacyElement("ARGON",      rl("argon"),     "Ar", 39.95f,  0f,    new double[]{0},          null, atomModel("argon"));

    private final String javaName;
    private final ResourceLocation id;
    public final String symbol;
    public final Float mass;
    public final Float electronegativity;
    public final double[] valencies;

    /** Optional per-element geometry override. If non-null, queried before the default
     * connection-count → {@link Geometry} mapping in {@link #getGeometry(int)}. Return {@code null}
     * to fall through to the default. Used by OXYGEN to force V_SHAPE for 2-connection oxygens
     * (e.g. in water / ether) instead of the default LINEAR.*/
    @Nullable
    private final Function<Integer, Geometry> geometryOverride;

    /** Resource location of the 3D model used by {@link MoleculeRenderer} for atoms of this
     * element. Null for R_GROUP (R-group atoms have their own model selection logic per
     * {@code rGroupNumber}). For datapack-loaded elements this is read from JSON and resolved
     * lazily on the client via {@link PartialModel#of}.*/
    @Nullable
    private final ResourceLocation modelPath;

    /** Client-side only: the ball model this element renders as. Assigned by
     * {@link petrolpark.mc.destroy.client.DestroyPartials} (for built-ins) or by the
     * {@code SyncElementsS2CPacket} client handler (for datapack-loaded elements).
     * Stays {@code null} on dedicated servers.*/
    @Nullable
    private PartialModel partial;

    /** Whether this element was loaded from a datapack (vs. a built-in static constant).
     * Datapack-loaded elements are cleared and rebuilt on every reload; built-in ones persist.*/
    private final boolean datapack;

    /** Built-in constructor — self-registers into {@link #ELEMENTS} keyed by {@link #id}.*/
    private LegacyElement(String javaName, ResourceLocation id, String symbol, Float mass,
                          Float electronegativity, double[] valencies,
                          @Nullable Function<Integer, Geometry> geometryOverride,
                          @Nullable ResourceLocation modelPath) {
        this.ordinal = NEXT_ORDINAL++;
        this.javaName = javaName;
        this.id = id;
        this.symbol = symbol;
        this.mass = mass;
        this.electronegativity = electronegativity;
        this.valencies = valencies;
        this.geometryOverride = geometryOverride;
        this.modelPath = modelPath;
        this.datapack = false;
        ELEMENTS.put(id, this);
    }

    /** Datapack constructor — same shape but marks {@link #datapack} true. Called by
     * {@code ElementDataReloadListener} during {@code /reload} after clearing the previous
     * datapack set. {@code javaName} is uppercased path so existing
     * {@code Lang.asId(element.name())}-style model lookups still produce a kebab-case lower
     * filename.*/
    public LegacyElement(ResourceLocation id, String symbol, Float mass, Float electronegativity,
                          double[] valencies,
                          @Nullable Function<Integer, Geometry> geometryOverride,
                          @Nullable ResourceLocation modelPath) {
        this.ordinal = NEXT_ORDINAL++;
        this.javaName = id.getPath().toUpperCase();
        this.id = id;
        this.symbol = symbol;
        this.mass = mass;
        this.electronegativity = electronegativity;
        this.valencies = valencies;
        this.geometryOverride = geometryOverride;
        this.modelPath = modelPath;
        this.datapack = true;
        ELEMENTS.put(id, this);
    }

    @Override
    public int compareTo(LegacyElement other) {
        return Integer.compare(this.ordinal, other.ordinal);
    }

    @Override
    public String toString() {
        return javaName;
    }

    /** Replacement for the former enum's auto-generated {@code values()}. Returns the live
     * registry view so freshly-loaded datapack elements are visible to iteration.*/
    public static Collection<LegacyElement> values() {
        return ELEMENTS.values();
    }

    /** Replacement for the former enum's auto-generated {@code name()}. Returns the upper-case
     * field name for built-ins (e.g. {@code "CARBON"}); datapack elements return the uppercased
     * path of their resource location.*/
    public String name() {
        return javaName;
    }

    /** The full namespace:path identifier of this element. Distinguishes datapack elements
     * across namespaces (built-ins all live under {@code destroy:}).*/
    public ResourceLocation getId() {
        return id;
    }

    /** Whether this element was loaded from a datapack. */
    public boolean isDatapack() {
        return datapack;
    }

    /** Per-element 3D model resource. Null for R_GROUP. */
    @Nullable
    public ResourceLocation getModelPath() {
        return modelPath;
    }

    /** Remove every datapack-sourced element from {@link #ELEMENTS}. Caller is responsible for
     * triggering downstream cleanup (mixtures referencing dropped elements may now serialize
     * weirdly until reload completes).*/
    public static void clearDatapackElements() {
        Iterator<Map.Entry<ResourceLocation, LegacyElement>> it = ELEMENTS.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().datapack) it.remove();
        }
    }

    @Nullable
    public PartialModel getPartial() {
        return partial;
    }

    public void setPartial(PartialModel partial) {
        this.partial = partial;
    }

    public String getSymbol() {
        return symbol;
    }

    public Float getMass() {
        return mass;
    }

    public Float getElectronegativity() {
        return electronegativity;
    }

    public Boolean isValidValency(double valency) {
        for (double v : valencies) {
            if (Math.abs(v - valency) < 0.000001d) return true;
        }
        return false;
    }

    public double getNextLowestValency(double valency) {
        for (double validValency : valencies) {
            if (validValency >= valency) return validValency;
        }
        return 0;
    }

    public double getMaxValency() {
        double currentMax = valencies[0];
        for (double valency : valencies) {
            if (valency > currentMax) currentMax = valency;
        }
        return currentMax;
    }

    public static LegacyElement fromSymbol(String symbol) {
        for (LegacyElement element : values()) {
            if (element.symbol.equals(symbol)) return element;
        }
        throw new IllegalArgumentException("Unknown Element of symbol " + symbol);
    }

    /** Map the number of connections on an atom of this element to a {@link Geometry} for the
     * molecule renderer. Consults the per-element {@link #geometryOverride} first; if null or
     * returns null, falls back to the standard VSEPR-ish connection-count mapping:
     * <ul>
     * <li>0/1/2 connections → {@link Geometry#LINEAR}</li>
     * <li>3 connections → {@link Geometry#TRIGONAL_PLANAR}</li>
     * <li>4 connections → {@link Geometry#TETRAHEDRAL}</li>
     * <li>5/6 connections → {@link Geometry#OCTAHEDRAL} (5 is TODO for trigonal bipyramidal)</li>
     * </ul>
     *
     * <p><strong>Client-side only</strong>: calls this method trigger loading of
     * {@link petrolpark.mc.destroy.core.chemistry.MoleculeRenderer} which imports client-only
     * classes. Server code must not invoke this.</p>
     */
    public Geometry getGeometry(int connections) {
        Geometry geometry = null;
        if (geometryOverride != null) geometry = geometryOverride.apply(connections);
        if (geometry != null) return geometry;
        switch (connections) {
            case 0:
            case 1:
            case 2:
                return Geometry.LINEAR;
            case 3:
                return Geometry.TRIGONAL_PLANAR;
            case 4:
                return Geometry.TETRAHEDRAL;
            case 5:
                // TODO add trigonal bipyramidal geometry
            case 6:
                return Geometry.OCTAHEDRAL;
            default:
                return Geometry.OCTAHEDRAL;
        }
    }
}
