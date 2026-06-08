package petrolpark.mc.destroy.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;

import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.compat.emi.category.GenericReactionEmiCategory;
import petrolpark.mc.destroy.compat.emi.category.ReactionEmiCategory;
import petrolpark.mc.destroy.compat.emi.category.ReactionEmiRecipe;
import petrolpark.mc.destroy.compat.jei.category.GenericReactionCategory;
import petrolpark.mc.destroy.compat.jei.category.ReactionCategory;

/**
 * EMI entry point for Destroy.
 *
 * <p>EMI's {@code @EmiEntrypoint} annotation is consulted by EMI's class scanner at
 * startup, so the entire {@code compat/emi/*} class graph is only loaded when EMI is
 * installed. With EMI absent, none of these classes resolve and the plugin is dead
 * code — no NoClassDefFoundError risk from the {@code compileOnly} dependency.</p>
 *
 * <p><b>Why a hand-written EMI plugin instead of relying on EMI's JEI auto-bridge</b>:
 * <ul>
 *   <li>EMI normalises ingredient amounts by GCD for compact display, turning Destroy's
 *       raw stoichiometric mole ratios (e.g. 15:30:60 for borax precipitation) into
 *       1:2:4. {@link MoleculeEmiStack#getAmount} carries the raw mol count so EMI's
 *       normaliser leaves it intact and users see the same numbers JEI shows.</li>
 *   <li>Destroy's custom JEI ingredient type
 *       ({@link petrolpark.mc.destroy.compat.jei.MoleculeJEIIngredient}) doesn't bridge
 *       through EMI's compatibility layer, so categories rendering molecules (Reaction,
 *       Generic Reaction) appear blank or missing entirely under EMI. Native EMI stacks
 *       fix this.</li>
 * </ul>
 *
 * <p><b>Other categories</b> (electrolysis, distillation, centrifugation, ageing,
 * mixing, basin, glassblowing, sieving, charging, tapping, mutation, mixable explosive,
 * cartography, vat material, arc furnace, element tank filling, mixture conversion,
 * flame retardant, extrusion, obliteration) use plain ItemStack / FluidStack
 * ingredients and EMI's JEI auto-bridge picks them up without help. Add a native
 * category here only when the auto-bridge stops working for a specific case.</p>
 */
@EmiEntrypoint
public class DestroyEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        // ─── Categories ──────────────────────────────────────────────────────────────
        ReactionEmiCategory reactionCategory = new ReactionEmiCategory();
        GenericReactionEmiCategory genericReactionCategory = new GenericReactionEmiCategory();
        registry.addCategory(reactionCategory);
        registry.addCategory(genericReactionCategory);

        // ─── Recipes ─────────────────────────────────────────────────────────────────
        // Mirror of JEI: every reaction in ReactionCategory.RECIPES (built-in + datapack
        // entries already merged at JEI's static-init time) becomes a ReactionEmiRecipe.
        // {@link ReactionEmiRecipe} accepts either a regular LegacyReaction or a
        // GenericReactionRecipe (which exposes the example reaction via getReaction()),
        // so the same class handles both categories — only the category instance differs.
        //
        // Each entry is built under its own try/catch so a single malformed reaction
        // (corrupt molecule registry entry, missing translation key, etc.) can't abort
        // the whole register() call midway — EMI silently swallows entrypoint exceptions
        // and the user-visible failure mode is "EMI plugin half-broken: a few categories
        // work, most don't".
        ReactionCategory.RECIPES.forEach((reaction, recipe) -> {
            try {
                // Built-in / data-pack reactions all have a real id (e.g. "destroy:iron_dissolution").
                // identityHashCode is the last-ditch fallback so EMI never sees null.
                String fullId = reaction.getFullId();
                net.minecraft.resources.ResourceLocation id = fullId != null
                    ? net.minecraft.resources.ResourceLocation.parse(fullId)
                    : petrolpark.mc.destroy.Destroy.asResource("reaction_" + System.identityHashCode(reaction));
                registry.addRecipe(new ReactionEmiRecipe(reactionCategory, reaction, id));
            } catch (Throwable t) {
                petrolpark.mc.destroy.Destroy.LOGGER.error(
                    "Failed to register EMI reaction recipe for {}", reaction.getFullId(), t);
            }
        });
        GenericReactionCategory.RECIPES.forEach((genericReaction, recipe) -> {
            if (recipe == null) return;  // GenericReactionRecipe.create can return null
            try {
                // CRUCIAL: use the GenericReaction's own id, NOT the example reaction's.
                // All generic-reaction example reactions are built through
                // LegacyReaction.generatedReactionBuilder which fixes namespace="novel" and
                // never sets an id, so getFullId() returns "novel:null" for every generic.
                // EMI dedupes recipes by id → without this the player only ever sees one
                // generic recipe in the category instead of the ~30 there should be.
                registry.addRecipe(new ReactionEmiRecipe(genericReactionCategory, recipe.getReaction(), genericReaction.id));
            } catch (Throwable t) {
                petrolpark.mc.destroy.Destroy.LOGGER.error(
                    "Failed to register EMI generic-reaction recipe for {}",
                    genericReaction != null && genericReaction.id != null ? genericReaction.id : "<null reaction>", t);
            }
        });

        // ─── Workstations ────────────────────────────────────────────────────────────
        // Same three blocks the JEI side declares as Reaction catalysts. Wrapped because
        // a missing block registration (e.g. Create absent or our DestroyBlocks not yet
        // initialised) would otherwise NPE here and break the rest of register().
        try {
            EmiStack mixer = EmiStack.of(com.simibubi.create.AllBlocks.MECHANICAL_MIXER.get());
            EmiStack basin = EmiStack.of(com.simibubi.create.AllBlocks.BASIN.get());
            EmiStack vat = EmiStack.of(petrolpark.mc.destroy.DestroyBlocks.VAT_CONTROLLER.get());
            for (var cat : new dev.emi.emi.api.recipe.EmiRecipeCategory[] { reactionCategory, genericReactionCategory }) {
                registry.addWorkstation(cat, mixer);
                registry.addWorkstation(cat, basin);
                registry.addWorkstation(cat, vat);
            }
        } catch (Throwable t) {
            petrolpark.mc.destroy.Destroy.LOGGER.error("Failed to register EMI reaction workstations", t);
        }

        // ─── Search index: register every non-novel non-hypothetical molecule ────────
        // so players can type a chemical name in EMI's search bar and find it the same
        // way they find items. Hypotheticals (R-group anchors used for pattern matching
        // only) and PROTON aren't physical species and shouldn't appear in the index.
        // Per-species try/catch: getId() parses molecule.getFullID() through
        // ResourceLocation.parse — a malformed id would throw and otherwise abort the
        // whole loop, hiding every later molecule from search.
        for (LegacySpecies species : LegacySpecies.MOLECULES.values()) {
            try {
                if (species.isHypothetical()) continue;
                if (species == petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules.PROTON) continue;
                if (species.isNovel()) continue;  // novel = unnamed user-synthesised, not searchable
                registry.addEmiStack(new MoleculeEmiStack(species));
            } catch (Throwable t) {
                petrolpark.mc.destroy.Destroy.LOGGER.error(
                    "Failed to register EMI search stack for molecule {}",
                    species != null ? species.getFullID() : "<null>", t);
            }
        }
    }
}
