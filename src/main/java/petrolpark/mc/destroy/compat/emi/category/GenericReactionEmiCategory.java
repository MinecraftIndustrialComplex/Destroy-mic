package petrolpark.mc.destroy.compat.emi.category;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyItems;

/**
 * EMI category for organic / functional-group-pattern reactions — mirror of JEI's
 * {@link petrolpark.mc.destroy.compat.jei.category.GenericReactionCategory}. Each
 * "recipe" displays an <i>example</i> concrete reaction representing the pattern
 * (e.g. one primary-amine + one carboxylic-acid → one amide for the esterification
 * pattern). Shares the underlying {@link petrolpark.mc.destroy.core.chemistry.recipe.ReactionRecipe.GenericReactionRecipe}
 * → {@code LegacyReaction.getExampleReaction()} bridge with the JEI side.
 *
 * <p>Without this category EMI users can't see Destroy's organic-chemistry chapter at
 * all — the JEI auto-bridge fails on GenericReactionCategory because of its
 * {@link petrolpark.mc.destroy.compat.jei.MoleculeJEIIngredient} custom-typed slots.</p>
 */
public class GenericReactionEmiCategory extends EmiRecipeCategory {

    public static final ResourceLocation ID = Destroy.asResource("generic_reaction");

    public GenericReactionEmiCategory() {
        // Same ABS icon as the regular Reaction category — they're sibling chemistry
        // categories and the player is expected to land here from the same nav point.
        super(ID, EmiStack.of(DestroyItems.ABS.asStack()));
    }

    /** See {@link ReactionEmiCategory#getName} — reuse the JEI translation key
     * {@code destroy.recipe.generic_reaction} for cross-viewer consistency. */
    @Override
    public Component getName() {
        return Component.translatable("destroy.recipe.generic_reaction");
    }
}
