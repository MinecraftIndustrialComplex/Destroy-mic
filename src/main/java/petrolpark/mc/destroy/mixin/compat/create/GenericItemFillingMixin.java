package petrolpark.mc.destroy.mixin.compat.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.fluids.transfer.GenericItemFilling;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;

import petrolpark.mc.destroy.content.product.fireretardant.FireproofingHelper;

/**
 * Bridges Create's Spout fluid-filling pipeline to Destroy's flame retardant application
 * recipe. Without this hook, the {@code destroy:flame_retardant_application} recipe type
 * is JEI-visible but unreachable in-world: Spout asks {@link GenericItemFilling} whether
 * a given item can be filled, and stock Create only answers "yes" for items that expose
 * a {@link net.neoforged.neoforge.fluids.capability.IFluidHandlerItem}. Items that should
 * accept a fireproof coating (regular tools, armor, etc.) have no such capability, so
 * Spout would silently no-op.
 *
 * <p>Three injections, mirrored from upstream 1.20.1:</p>
 * <ol>
 *   <li>{@code canItemBeFilled @At RETURN} — if Create said "no" but the item can take
 *       fireproofing, override to "yes".</li>
 *   <li>{@code getRequiredAmountForItem @At RETURN} — if Create returned -1 (no recipe)
 *       but Destroy has a matching flame retardant recipe for this fluid, return the
 *       recipe's required mB.</li>
 *   <li>{@code fillItem @At HEAD} — short-circuit BEFORE Create's capability dispatch
 *       when the item is neither a glass bottle nor capability-bearing, so the recipe
 *       runs through {@link FireproofingHelper#fillItem}.</li>
 * </ol>
 *
 * <p>1.21 NeoForge port notes vs. upstream:
 * <ul>
 *   <li>{@code FluidStack} package moved from {@code net.minecraftforge.fluids} to
 *       {@code net.neoforged.neoforge.fluids}; descriptors in {@code @Inject method} are
 *       therefore implicit (matched by name — only one overload).</li>
 *   <li>The LazyOptional capability API is gone; capability lookup is now a nullable
 *       direct return via {@link net.minecraft.world.item.ItemStack#getCapability(net.neoforged.neoforge.capabilities.ItemCapability)}.</li>
 * </ul>
 */
@Mixin(GenericItemFilling.class)
public class GenericItemFillingMixin {

    @Shadow
    private static boolean canFillGlassBottleInternally(FluidStack availableFluid) {
        throw new AssertionError(); // shadowed — never actually invoked
    }

    @Inject(method = "canItemBeFilled", at = @At("RETURN"), remap = false, cancellable = true)
    private static void destroy$canItemBeFilled(Level world, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (FireproofingHelper.canApply(world, stack)) cir.setReturnValue(true);
    }

    @Inject(method = "getRequiredAmountForItem", at = @At("RETURN"), remap = false, cancellable = true)
    private static void destroy$getRequiredAmountForItem(Level world, ItemStack stack, FluidStack availableFluid, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() != -1) return;
        int required = FireproofingHelper.getRequiredAmountForItem(world, stack, availableFluid);
        if (required > 0) cir.setReturnValue(required);
    }

    @Inject(method = "fillItem", at = @At("HEAD"), remap = false, cancellable = true)
    private static void destroy$fillItem(Level world, int requiredAmount, ItemStack stack, FluidStack availableFluid, CallbackInfoReturnable<ItemStack> cir) {
        FluidStack toFill = availableFluid.copy();
        toFill.setAmount(requiredAmount);
        ItemStack single = stack.copy();
        single.setCount(1);
        // Skip when Create has a real path: it's a glass bottle handled internally, OR the
        // item exposes a fluid handler capability that Create will fill normally.
        boolean glassBottleInternal = stack.getItem() == Items.GLASS_BOTTLE && canFillGlassBottleInternally(toFill);
        boolean hasFluidHandler = single.getCapability(Capabilities.FluidHandler.ITEM) != null;
        if (glassBottleInternal || hasFluidHandler) return;
        ItemStack result = FireproofingHelper.fillItem(world, requiredAmount, stack, availableFluid);
        if (!result.isEmpty()) cir.setReturnValue(result);
    }
}
