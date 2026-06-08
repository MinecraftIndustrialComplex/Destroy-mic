package petrolpark.mc.destroy.core.chemistry.data;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.chemistry.legacy.IItemReactant;
import petrolpark.mc.destroy.chemistry.legacy.LegacyReaction;
import petrolpark.mc.destroy.chemistry.legacy.LegacyReaction.ReactionBuilder;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.ReactionResult;

/**
 * Datapack-serialisable description of a single chemical reaction. Loaded from
 * {@code data/<ns>/destroy/reactions/<id>.json} by {@code ReactionDataReloadListener}.
 *
 * <p>Each definition translates 1:1 into a {@link ReactionBuilder} call chain at reload time.
 * Generic Reactions (functional-group transforms) are NOT exposed through this surface — they
 * remain Java-side because their product structure depends on dynamic graph rewriting.</p>
 *
 * <p>Molecules are looked up via {@link LegacySpecies#getMolecule(String)} at apply-time; unknown
 * IDs cause the whole entry to be skipped with a logger warning rather than failing the reload.</p>
 */
public record ReactionDefinition(
    List<ReactantEntry> reactants,
    List<ProductEntry> products,
    List<CatalystEntry> catalysts,
    List<ItemReactantEntry> itemReactants,
    Optional<Boolean> requiresUv,
    Optional<KineticsEntry> kinetics,
    Optional<ReactionResultDefinition> result,
    Optional<ReverseEntry> reverse
) {

    public static final Codec<ReactionDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.list(ReactantEntry.CODEC).optionalFieldOf("reactants", List.of()).forGetter(ReactionDefinition::reactants),
        Codec.list(ProductEntry.CODEC).optionalFieldOf("products", List.of()).forGetter(ReactionDefinition::products),
        Codec.list(CatalystEntry.CODEC).optionalFieldOf("catalysts", List.of()).forGetter(ReactionDefinition::catalysts),
        Codec.list(ItemReactantEntry.CODEC).optionalFieldOf("item_reactants", List.of()).forGetter(ReactionDefinition::itemReactants),
        Codec.BOOL.optionalFieldOf("requires_uv").forGetter(ReactionDefinition::requiresUv),
        KineticsEntry.CODEC.optionalFieldOf("kinetics").forGetter(ReactionDefinition::kinetics),
        ReactionResultDefinition.CODEC.optionalFieldOf("result").forGetter(ReactionDefinition::result),
        ReverseEntry.CODEC.optionalFieldOf("reverse").forGetter(ReactionDefinition::reverse)
    ).apply(i, ReactionDefinition::new));

    /**
     * Apply this definition to the global reaction registry under the given full id
     * ({@code namespace:path}). Returns true on success, false if a referenced molecule was missing
     * (and the reaction is therefore skipped — caller can log).
     */
    public boolean apply(ResourceLocation id) {
        ReactionBuilder builder = new ReactionBuilder(id.getNamespace()).id(id.getPath());

        for (ReactantEntry r : reactants) {
            LegacySpecies sp = LegacySpecies.getMolecule(r.molecule);
            if (sp == null) {
                Destroy.LOGGER.warn("Skipping reaction {}: reactant molecule '{}' not found", id, r.molecule);
                return false;
            }
            builder.addReactant(sp, r.ratio.orElse(1), r.order.orElse(r.ratio.orElse(1)));
        }
        for (ProductEntry p : products) {
            LegacySpecies sp = LegacySpecies.getMolecule(p.molecule);
            if (sp == null) {
                Destroy.LOGGER.warn("Skipping reaction {}: product molecule '{}' not found", id, p.molecule);
                return false;
            }
            builder.addProduct(sp, p.ratio.orElse(1));
        }
        for (CatalystEntry c : catalysts) {
            LegacySpecies sp = LegacySpecies.getMolecule(c.molecule);
            if (sp == null) {
                Destroy.LOGGER.warn("Skipping reaction {}: catalyst molecule '{}' not found", id, c.molecule);
                return false;
            }
            builder.addCatalyst(sp, c.order.orElse(0));
        }
        for (ItemReactantEntry ie : itemReactants) {
            IItemReactant itemReactant = ie.toItemReactant(id);
            if (itemReactant == null) return false;
            builder.addItemReactant(itemReactant, ie.moles);
        }
        if (requiresUv.orElse(false)) builder.requireUV();
        kinetics.ifPresent(k -> {
            if (k.activationEnergy.isPresent()) builder.activationEnergy(k.activationEnergy.get());
            if (k.preexponentialFactor.isPresent()) builder.preexponentialFactor(k.preexponentialFactor.get());
            if (k.enthalpyChange.isPresent()) builder.enthalpyChange(k.enthalpyChange.get());
        });
        result.ifPresent(r -> {
            ReactionResult.Factory factory = r.factory();
            if (factory != null) builder.withResult(r.moles(), factory);
        });

        // Reverse reaction. {@link ReactionBuilder#reverseReaction} auto-derives the
        // reverse's reactants/products/catalysts by swapping the forward's, and falls
        // back to Hess's-Law-consistent activation energy + enthalpy if not specified.
        // The lambda below only needs to apply the override kinetics + result the
        // datapack author chose to set on the reverse half.
        //
        // <p>Note: builder.reverseReaction throws if the parent reaction was created
        // via {@code generatedReactionBuilder()} — but datapack reactions always go
        // through the standard {@code ReactionBuilder(namespace).id(path)} path, so
        // this constraint never fires here. Hypothetical / R-group generic reactions
        // remain Java-only by design (see class javadoc).</p>
        reverse.ifPresent(rev -> builder.reverseReaction(rb -> {
            rev.kinetics().ifPresent(k -> {
                k.activationEnergy().ifPresent(rb::activationEnergy);
                k.preexponentialFactor().ifPresent(rb::preexponentialFactor);
                k.enthalpyChange().ifPresent(rb::enthalpyChange);
            });
            rev.result().ifPresent(r -> {
                ReactionResult.Factory factory = r.factory();
                if (factory != null) rb.withResult(r.moles(), factory);
            });
        }));

        LegacyReaction reaction = builder.build();
        reaction.markAsDatapack();
        return true;
    }

    public record ReactantEntry(String molecule, Optional<Integer> ratio, Optional<Integer> order) {
        public static final Codec<ReactantEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("molecule").forGetter(ReactantEntry::molecule),
            Codec.INT.optionalFieldOf("ratio").forGetter(ReactantEntry::ratio),
            Codec.INT.optionalFieldOf("order").forGetter(ReactantEntry::order)
        ).apply(i, ReactantEntry::new));
    }

    public record ProductEntry(String molecule, Optional<Integer> ratio) {
        public static final Codec<ProductEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("molecule").forGetter(ProductEntry::molecule),
            Codec.INT.optionalFieldOf("ratio").forGetter(ProductEntry::ratio)
        ).apply(i, ProductEntry::new));
    }

    public record CatalystEntry(String molecule, Optional<Integer> order) {
        public static final Codec<CatalystEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("molecule").forGetter(CatalystEntry::molecule),
            Codec.INT.optionalFieldOf("order").forGetter(CatalystEntry::order)
        ).apply(i, CatalystEntry::new));
    }

    public record ItemReactantEntry(
        Optional<ResourceLocation> item,
        Optional<ResourceLocation> tag,
        boolean catalyst,
        float moles
    ) {
        public static final Codec<ItemReactantEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("item").forGetter(ItemReactantEntry::item),
            ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(ItemReactantEntry::tag),
            Codec.BOOL.optionalFieldOf("catalyst", false).forGetter(ItemReactantEntry::catalyst),
            Codec.FLOAT.fieldOf("moles").forGetter(ItemReactantEntry::moles)
        ).apply(i, ItemReactantEntry::new));

        /** Resolve to a concrete {@link IItemReactant}; null + warning on bad input. */
        public IItemReactant toItemReactant(ResourceLocation reactionId) {
            if (item.isPresent() == tag.isPresent()) {
                Destroy.LOGGER.warn(
                    "Skipping reaction {}: item_reactants entry must specify exactly one of 'item' or 'tag'",
                    reactionId);
                return null;
            }
            if (item.isPresent()) {
                ResourceLocation rl = item.get();
                Item resolved = BuiltInRegistries.ITEM.get(rl);
                if (resolved == Items.AIR) {
                    Destroy.LOGGER.warn("Skipping reaction {}: item '{}' not found", reactionId, rl);
                    return null;
                }
                java.util.function.Supplier<Item> supplier = () -> resolved;
                return catalyst
                    ? new IItemReactant.SimpleItemCatalyst(supplier)
                    : new IItemReactant.SimpleItemReactant(supplier);
            } else {
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tag.get());
                return catalyst
                    ? new IItemReactant.SimpleItemTagCatalyst(tagKey)
                    : new IItemReactant.SimpleItemTagReactant(tagKey);
            }
        }
    }

    public record KineticsEntry(
        Optional<Float> activationEnergy,
        Optional<Float> preexponentialFactor,
        Optional<Float> enthalpyChange
    ) {
        public static final Codec<KineticsEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("activation_energy").forGetter(KineticsEntry::activationEnergy),
            Codec.FLOAT.optionalFieldOf("preexponential_factor").forGetter(KineticsEntry::preexponentialFactor),
            Codec.FLOAT.optionalFieldOf("enthalpy_change").forGetter(KineticsEntry::enthalpyChange)
        ).apply(i, KineticsEntry::new));
    }

    /**
     * Reverse-reaction override entries. The reverse reaction's reactants / products /
     * catalysts are <b>automatically derived</b> by {@code ReactionBuilder.reverseReaction}
     * (forward reactants ↔ products; non-reactant orders become reverse catalysts), so
     * datapack authors only need to specify what they want to override on the reverse half:
     * <ul>
     *   <li>{@code kinetics}: any subset of {@code activation_energy} /
     *       {@code preexponential_factor} / {@code enthalpy_change}. Anything omitted
     *       falls back to the Hess's-Law-consistent derivation from the forward
     *       reaction (e.g. reverse enthalpy = −forward enthalpy when neither is fixed).</li>
     *   <li>{@code result}: a separate {@link ReactionResultDefinition} the reverse
     *       half produces (rare — typically only the forward direction precipitates).</li>
     * </ul>
     * The entry's presence alone (even with {@code {}}) is enough to mark the reaction
     * reversible — empty body means "auto-derive everything", matching the Java-side
     * {@code .reversible()} shortcut.
     */
    public record ReverseEntry(
        Optional<KineticsEntry> kinetics,
        Optional<ReactionResultDefinition> result
    ) {
        public static final Codec<ReverseEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            KineticsEntry.CODEC.optionalFieldOf("kinetics").forGetter(ReverseEntry::kinetics),
            ReactionResultDefinition.CODEC.optionalFieldOf("result").forGetter(ReverseEntry::result)
        ).apply(i, ReverseEntry::new));
    }
}
