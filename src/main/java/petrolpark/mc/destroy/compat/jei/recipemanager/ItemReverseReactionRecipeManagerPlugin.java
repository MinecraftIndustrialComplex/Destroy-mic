package petrolpark.mc.destroy.compat.jei.recipemanager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.advanced.IRecipeManagerPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.chemistry.legacy.LegacyReaction;
import petrolpark.mc.destroy.compat.jei.category.GenericReactionCategory;
import petrolpark.mc.destroy.compat.jei.category.ReactionCategory;
import petrolpark.mc.destroy.core.chemistry.recipe.ReactionRecipe;

/**
 * Surfaces reversible Reactions in JEI when the user focuses on an item ingredient that the
 * reaction's reactant/precipitate set could match — fixes a JEI default-behavior gap where
 * reversible Reactions wouldn't show under the "Item produces" recipe page if the reaction was
 * defined with the item on the input side.
*/
public class ItemReverseReactionRecipeManagerPlugin implements IRecipeManagerPlugin {

    public static final List<RecipeType<?>> TYPES = List.of(ReactionCategory.TYPE, GenericReactionCategory.TYPE);

    @Override
    public <V> List<RecipeType<?>> getRecipeTypes(IFocus<V> focus) {
        return TYPES;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, V> List<T> getRecipes(IRecipeCategory<T> recipeCategory, IFocus<V> focus) {
        List<T> recipes = new ArrayList<>();
        focus.checkedCast(VanillaTypes.ITEM_STACK)
            .map(IFocus::getTypedValue)
            .map(ITypedIngredient::getIngredient)
            .ifPresent(stack -> {
                Stream<? extends ReactionRecipe> recipesToCheck;
                String holderIdPrefix;
                if (recipeCategory instanceof GenericReactionCategory) {
                    recipesToCheck = GenericReactionCategory.RECIPES.values().stream();
                    holderIdPrefix = "reverse_generic_reaction_";
                } else if (recipeCategory instanceof ReactionCategory) {
                    recipesToCheck = ReactionCategory.RECIPES.values().stream();
                    holderIdPrefix = "reverse_reaction_";
                } else {
                    return;
                }
                int[] counter = { 0 };
                recipesToCheck.filter(recipe -> {
                    LegacyReaction reaction = recipe.getReaction();
                    boolean searchCatalysts = focus.getRole() == RecipeIngredientRole.CATALYST;
                    boolean searchInputs = searchCatalysts
                        || focus.getRole() == RecipeIngredientRole.INPUT
                        || (focus.getRole() == RecipeIngredientRole.OUTPUT && reaction.displayAsReversible());
                    boolean searchOutputs = searchCatalysts
                        || focus.getRole() == RecipeIngredientRole.OUTPUT
                        || (focus.getRole() == RecipeIngredientRole.INPUT && reaction.displayAsReversible());

                    // Reactants and catalysts
                    if (reaction.getItemReactants().stream().anyMatch(ir ->
                        ((ir.isCatalyst() && searchCatalysts) || (!ir.isCatalyst() && searchInputs))
                            && ir.isItemValid(stack))) return true;

                    // Precipitate outputs (upstream had this as a no-op expression with the
                    // `anyMatch` result thrown away; the missing `return` is restored so item
                    // outputs of irreversible reactions actually surface in reverse lookup).
                    if (searchOutputs && reaction.hasResult()
                        && reaction.getResult().getAllPrecipitates().stream()
                            .anyMatch(p -> ItemStack.matches(p.getPrecipitate(), stack))) return true;

                    return false;
                }).forEach(r -> {
                    // Wrap each ReactionRecipe in a synthetic RecipeHolder — Create's
                    // CreateRecipeCategory<R> implements IRecipeCategory<RecipeHolder<R>>, so
                    // JEI's setRecipe(Object) bridge does `checkcast RecipeHolder` on every
                    // recipe the plugin returns. A bare ReactionRecipe here crashes the layout
                    // build with the in-game "该配方已崩溃 / destroy:reaction" overlay because
                    // the cast fails. Holder id only needs to be unique within this list.
                    recipes.add((T) new RecipeHolder<>(
                        Destroy.asResource(holderIdPrefix + counter[0]++), r));
                });
            });
        return recipes;
    }

    @Override
    public <T> List<T> getRecipes(IRecipeCategory<T> recipeCategory) {
        return List.of();
    }
}
