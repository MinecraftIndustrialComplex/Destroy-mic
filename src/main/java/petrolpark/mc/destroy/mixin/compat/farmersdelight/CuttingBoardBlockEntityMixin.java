package petrolpark.mc.destroy.mixin.compat.farmersdelight;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import petrolpark.mc.destroy.DestroyAdvancementTrigger;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;

/**
 * Awards {@link DestroyAdvancementTrigger#CUT_ONIONS} when a player slices an onion on a FD
 * cutting board. We snapshot the stored stack at HEAD (the call consumes it) and check at
 * RETURN whether the cut actually succeeded and the snapshot was an onion.
 *
 * <p>Gated by {@code requireMultipleMods("CuttingBoardBlockEntityMixin", "farmersdelight")} in
 * {@link petrolpark.mc.destroy.mixin.plugin.DestroyMixinPlugin}, so the mixin is skipped when
 * FD is absent — otherwise the {@code vectorwing.*} class references would NPE at
 * pre-process.</p>
 */
@Mixin(CuttingBoardBlockEntity.class)
public abstract class CuttingBoardBlockEntityMixin {

    @Unique
    private ItemStack destroy$preCutBoardStack;

    @Shadow
    public abstract ItemStack getStoredItem();

    @Inject(method = "processStoredItemUsingTool", at = @At("HEAD"), remap = false)
    private void destroy$capturePreCut(ItemStack tool, Player player, CallbackInfoReturnable<Boolean> cir) {
        destroy$preCutBoardStack = getStoredItem().copy();
    }

    @Inject(method = "processStoredItemUsingTool", at = @At("RETURN"), remap = false)
    private void destroy$awardCutOnion(ItemStack tool, Player player, CallbackInfoReturnable<Boolean> cir) {
        ItemStack snapshot = destroy$preCutBoardStack;
        destroy$preCutBoardStack = null;
        if (!Boolean.TRUE.equals(cir.getReturnValue()) || snapshot == null || player == null) return;
        Level level = ((BlockEntity) (Object) this).getLevel();
        if (level == null || level.isClientSide()) return;
        if (snapshot.is(ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "foods/onion")))) {
            DestroyAdvancementTrigger.CUT_ONIONS.award(level, player);
        }
    }
}
