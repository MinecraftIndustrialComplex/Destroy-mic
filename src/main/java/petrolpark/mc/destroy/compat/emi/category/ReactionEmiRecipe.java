package petrolpark.mc.destroy.compat.emi.category;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.WidgetHolder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import petrolpark.mc.destroy.chemistry.legacy.IItemReactant;
import petrolpark.mc.destroy.chemistry.legacy.LegacyReaction;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.reactionresult.PrecipitateReactionResult;
import petrolpark.mc.destroy.compat.emi.MoleculeEmiStack;

/**
 * EMI recipe entry for a single {@link LegacyReaction}. Mirrors JEI's
 * {@link petrolpark.mc.destroy.compat.jei.category.ReactionCategory#setRecipe} layout
 * but expressed through EMI's widget model.
 *
 * <p><b>Key fix vs EMI auto-bridge from JEI</b>: the molar-amount field on each
 * {@link MoleculeEmiStack} carries the <em>raw stoichiometric mole count</em> (e.g.
 * 15:30:60 for borax precipitation), not the GCD-reduced display ratio EMI's
 * auto-bridge defaults to (1:2:4). EMI renders amounts directly from the stack's
 * {@code getAmount()} so users see the same numbers the JEI side shows.</p>
 */
public class ReactionEmiRecipe implements EmiRecipe {

    private static final int WIDTH = 156;
    private static final int HEIGHT = 60;
    private static final int SLOT_SIZE = 18;
    private static final int Y_REACTANT = 14;
    private static final int Y_PRODUCT = 14;
    private static final int X_REACTANT_BASE = 4;
    private static final int X_PRODUCT_BASE = WIDTH - X_REACTANT_BASE - SLOT_SIZE;

    /** Comparator for sorting reactant/product molecules within a row — heavier first
     *  (mass-descending), R-groups last. Matches JEI's sort in
     *  {@code ReactionCategory.getSpeciesWeightForSorting}. */
    private static final Comparator<LegacySpecies> SPECIES_SORT =
        Comparator.comparing(petrolpark.mc.destroy.compat.jei.category.ReactionCategory::getSpeciesWeightForSorting);

    private final EmiRecipeCategory category;
    private final LegacyReaction reaction;
    private final ResourceLocation id;

    private final List<EmiIngredient> inputs;
    private final List<EmiStack> outputs;
    private final List<EmiIngredient> catalysts;

    /**
     * @param category   The EMI category this recipe is displayed under.
     * @param reaction   The {@link LegacyReaction} to render. For generic reactions this
     *                   should be the <i>example</i> reaction (R-group instantiated form)
     *                   that {@link petrolpark.mc.destroy.chemistry.legacy.genericreaction.GenericReaction#getExampleReaction}
     *                   produces, not the abstract pattern.
     * @param id         The EMI recipe id — must be unique across all recipes in the
     *                   category, since EMI uses ids for dedup and "recently viewed"
     *                   recall. <b>Important for generic reactions:</b> their example
     *                   reactions are all built through {@code generatedReactionBuilder()}
     *                   which sets {@code namespace="novel"} and never assigns an
     *                   {@code id}, so {@code reaction.getFullId()} returns the same
     *                   {@code "novel:null"} for every generic. Passing the
     *                   {@code GenericReaction.id} (e.g. {@code "destroy:alkene_hydration"})
     *                   gives each one a unique key — otherwise EMI dedupes them down to
     *                   one entry and the player only sees a single generic reaction tile.
     */
    public ReactionEmiRecipe(EmiRecipeCategory category, LegacyReaction reaction, ResourceLocation id) {
        this.category = category;
        this.reaction = reaction;
        this.id = id;

        float molarMultiplier = computeMolarMultiplier(reaction);
        this.inputs = collectInputs(reaction, molarMultiplier);
        this.outputs = collectOutputs(reaction, molarMultiplier);
        this.catalysts = collectCatalysts(reaction);
    }

    /**
     * Pick the display multiplier for molar amounts. EMI doesn't have JEI's per-hover
     * "focused on item" mode, so we commit to one view per recipe:
     * <ul>
     *   <li><b>Precipitate present</b> → multiplier = {@code requiredMoles} of the first
     *       precipitate. This makes the recipe read as <i>"how many molecules per
     *       1 output item"</i>, matching the way players ask "how much do I need to make
     *       1 borax".</li>
     *   <li><b>No precipitate but has item reactant</b> → multiplier = {@code molesPerItem},
     *       reading as <i>"how many molecules per 1 input item consumed"</i>.</li>
     *   <li><b>Neither</b> → multiplier = 1, raw stoichiometric ratios.</li>
     * </ul>
     * Mirrors JEI's {@code ReactionCategory.setRecipe} focus-driven multiplier choice
     * (lines 174–178 there) but applied unconditionally since EMI has no focus state.
     */
    private static float computeMolarMultiplier(LegacyReaction reaction) {
        if (reaction.hasResult()) {
            Collection<PrecipitateReactionResult> precipitates = reaction.getResult().getAllPrecipitates();
            if (!precipitates.isEmpty()) {
                float req = precipitates.iterator().next().getRequiredMoles();
                if (req > 0f) return req;
            }
        }
        if (reaction.consumesItem() && reaction.getMolesPerItem() > 0f) {
            return reaction.getMolesPerItem();
        }
        return 1f;
    }

    /** Scale a stoichiometric ratio by the display multiplier and round to a positive
     *  long. EMI's slot amount is integer-valued so fractional values (e.g. 1.5 from a
     *  stoich=3 × multiplier=0.5 combo) lose ≤0.5 precision per slot. The internal
     *  reaction simulation is unaffected — this only changes the displayed number. */
    private static long scaleAmount(int stoich, float multiplier) {
        if (stoich <= 0) return 1L;
        return Math.max(1L, Math.round((double) stoich * multiplier));
    }

    /** Build inputs: molecule reactants (with their molar ratios as amount) + non-catalyst
     *  item reactants. */
    private static List<EmiIngredient> collectInputs(LegacyReaction reaction, float molarMultiplier) {
        List<EmiIngredient> list = new ArrayList<>();
        // Molecule reactants — sorted heavier-first to match JEI rendering.
        reaction.getReactants().stream()
            .sorted(SPECIES_SORT)
            .forEach(species -> {
                long amount = scaleAmount(reaction.getReactantMolarRatio(species), molarMultiplier);
                list.add(new MoleculeEmiStack(species, amount));
            });
        // Non-catalyst item reactants — e.g. zinc dust as a sacrificial reactant in some
        // reactions. Catalysts go into the separate getCatalysts() list.
        for (IItemReactant itemReactant : reaction.getItemReactants()) {
            if (itemReactant.isCatalyst()) continue;
            List<ItemStack> displayed = itemReactant.getDisplayedItemStacks();
            if (displayed.isEmpty()) continue;
            // Multiple displayed stacks → present as a tag-like rotating ingredient.
            list.add(EmiIngredient.of(displayed.stream().map(EmiStack::of).toList()));
        }
        return list;
    }

    /** Build outputs: molecule products (with molar ratio amounts) + precipitate items
     *  produced by the reaction result. */
    private static List<EmiStack> collectOutputs(LegacyReaction reaction, float molarMultiplier) {
        List<EmiStack> list = new ArrayList<>();
        reaction.getProducts().stream()
            .sorted(SPECIES_SORT)
            .forEach(species -> {
                long amount = scaleAmount(reaction.getProductMolarRatio(species), molarMultiplier);
                list.add(new MoleculeEmiStack(species, amount));
            });
        if (reaction.hasResult()) {
            for (PrecipitateReactionResult precipitate : reaction.getResult().getAllPrecipitates()) {
                // 1 output item per slot — the multiplier already scaled the inputs to
                // "per 1 output item", so showing the precipitate itself with amount=1
                // gives the user the correct "X mol of input → 1 item" reading.
                list.add(EmiStack.of(precipitate.getPrecipitate()));
            }
        }
        return list;
    }

    /** Catalysts: orderless modifiers (item-tag catalysts + non-reactant molecule
     *  catalysts that appear in the rate equation but aren't consumed). */
    private static List<EmiIngredient> collectCatalysts(LegacyReaction reaction) {
        List<EmiIngredient> list = new ArrayList<>();
        // Molecule catalysts — molecules that appear in the rate equation (getOrders) but
        // not in the reactant set.
        reaction.getOrders().keySet().stream()
            .filter(m -> !reaction.getReactants().contains(m))
            .sorted(SPECIES_SORT)
            .forEach(m -> list.add(new MoleculeEmiStack(m, 1L)));
        // Item-tag catalysts — e.g. platinum dust for Andrussow process.
        for (IItemReactant itemReactant : reaction.getItemReactants()) {
            if (!itemReactant.isCatalyst()) continue;
            List<ItemStack> displayed = itemReactant.getDisplayedItemStacks();
            if (displayed.isEmpty()) continue;
            list.add(EmiIngredient.of(displayed.stream().map(EmiStack::of).toList()));
        }
        return list;
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return category;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return inputs;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    @Override
    public List<EmiIngredient> getCatalysts() {
        return catalysts;
    }

    @Override
    public int getDisplayWidth() {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        // Reactants on the left, products on the right. EMI's SlotWidget handles
        // tooltip / hover natively via the EmiIngredient. {@code recipeContext()}
        // links the slot to this recipe so right-click "find recipes that produce X"
        // works as expected.
        int x = X_REACTANT_BASE;
        for (EmiIngredient input : inputs) {
            SlotWidget slot = widgets.addSlot(input, x, Y_REACTANT);
            slot.recipeContext(this);
            x += SLOT_SIZE;
        }

        // Arrow in the middle.
        widgets.addTexture(dev.emi.emi.api.render.EmiTexture.EMPTY_ARROW,
            (WIDTH - 24) / 2, Y_REACTANT + 1);

        // Products on the right side, flowing rightward from the arrow.
        x = (WIDTH / 2) + 12 + 4;
        for (EmiStack output : outputs) {
            widgets.addSlot(output, x, Y_PRODUCT).recipeContext(this);
            x += SLOT_SIZE;
        }
    }

    public LegacyReaction getReaction() {
        return reaction;
    }
}
