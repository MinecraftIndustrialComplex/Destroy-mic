package petrolpark.mc.destroy.core.chemistry.storage;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.createmod.catnip.data.Iterate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import com.simibubi.create.foundation.utility.CreateLang;

import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.chemistry.legacy.ClientMixture;
import petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import petrolpark.mc.destroy.config.DestroyAllConfigs;

/**
 * Item interface for carrying Mixture fluids.
 *
 * <p>1.21 migration notes:</p>
*/
public interface IMixtureStorageItem {

    /** Max mB this stack can hold. Implementations override to return per-item capacity (e.g., TEST_TUBE = 25 mB).*/
    int getCapacity(ItemStack stack);

    /** Display name when empty — subclasses provide the base item name.*/
    Component getNameRegardlessOfFluid(ItemStack stack);

    // ---- fill/empty interactions ----

    /**
     * Try filling the item from an external fluid handler. Determines the actual transfer
     * amount by simulating both ends first, then executes both ends with exactly that
     * amount — preventing source-side over-drain when the destination's free capacity is
     * smaller than {@code maxTransfer}.
     *
     * <p>The previous two-pass {@code simulate/execute} loop drained {@code maxTransfer}
     * verbatim on the execute pass; if the destination only had room for less than
     * {@code maxTransfer}, the destination's {@code fill} returned the smaller amount and
     * the difference was lost (the {@code drained} FluidStack went out of scope).
     * Reported as the symmetric pour-out bug ("flask 500mB into 200mB-free container =
     * flask empties, 300mB destroyed").</p>
     */
    default InteractionResult tryFill(ItemStack stack, IFluidHandlerItem itemTank, @Nullable IFluidHandler otherTank, int maxTransfer) {
        if (otherTank == null) return InteractionResult.PASS;
        // Phase 1: simulate to find the largest transfer amount both ends accept.
        FluidStack simulatedDrain = otherTank.drain(maxTransfer, FluidAction.SIMULATE);
        if (simulatedDrain.isEmpty()) return InteractionResult.FAIL;
        int simulatedFill = itemTank.fill(simulatedDrain, FluidAction.SIMULATE);
        if (simulatedFill == 0) return InteractionResult.FAIL;
        int actualTransfer = Math.min(simulatedDrain.getAmount(), simulatedFill);
        if (actualTransfer <= 0) return InteractionResult.FAIL;
        // Phase 2: execute with that exact amount on both ends.
        FluidStack actualDrain = otherTank.drain(actualTransfer, FluidAction.EXECUTE);
        if (actualDrain.isEmpty()) return InteractionResult.FAIL;
        itemTank.fill(actualDrain, FluidAction.EXECUTE);
        return InteractionResult.SUCCESS;
    }

    /** Try filling completely from other tank.*/
    default InteractionResult tryFill(ItemStack stack, IFluidHandlerItem itemTank, @Nullable IFluidHandler otherTank) {
        int space = getCapacity(stack) - (itemTank.drain(getCapacity(stack), FluidAction.SIMULATE).getAmount());
        return tryFill(stack, itemTank, otherTank, Math.max(space, 1));
    }

    /**
     * Try emptying the item into an external fluid handler. Symmetric counterpart of
     * {@link #tryFill} — see that method's javadoc for the over-drain failure mode and
     * the two-phase fix.
     *
     * <p>The {@code infiniteFluid} flag (creative-mode flask) means "don't actually
     * remove fluid from the source", but the executed fill is still the genuine
     * destination side; the simulate-then-execute split still applies.</p>
     */
    default InteractionResult tryEmpty(ItemStack stack, IFluidHandlerItem itemTank, @Nullable IFluidHandler otherTank, boolean infiniteFluid, int maxTransfer) {
        if (otherTank == null) return InteractionResult.PASS;
        FluidStack simulatedDrain = itemTank.drain(maxTransfer, FluidAction.SIMULATE);
        if (simulatedDrain.isEmpty()) return InteractionResult.FAIL;
        int simulatedFill = otherTank.fill(simulatedDrain, FluidAction.SIMULATE);
        if (simulatedFill == 0) return InteractionResult.FAIL;
        int actualTransfer = Math.min(simulatedDrain.getAmount(), simulatedFill);
        if (actualTransfer <= 0) return InteractionResult.FAIL;
        // Drain only what the destination actually accepted. Skip drain in creative
        // (infiniteFluid) so the flask doesn't deplete, but still perform the fill.
        FluidStack toFill;
        if (infiniteFluid) {
            toFill = simulatedDrain.copyWithAmount(actualTransfer);
        } else {
            toFill = itemTank.drain(actualTransfer, FluidAction.EXECUTE);
            if (toFill.isEmpty()) return InteractionResult.FAIL;
        }
        otherTank.fill(toFill, FluidAction.EXECUTE);
        return InteractionResult.SUCCESS;
    }

    /** Try emptying completely into other tank.*/
    default InteractionResult tryEmpty(ItemStack stack, IFluidHandlerItem itemTank, @Nullable IFluidHandler otherTank, boolean infiniteFluid) {
        int held = itemTank.drain(getCapacity(stack), FluidAction.SIMULATE).getAmount();
        return tryEmpty(stack, itemTank, otherTank, infiniteFluid, Math.max(held, 1));
    }

    /** Lookup the target tank at a block position. Returns null if no cap found.*/
    @Nullable
    default IFluidHandler getTank(Level level, BlockPos pos, BlockState state, @Nullable Direction face,
                                  Player player, InteractionHand hand, ItemStack stack, boolean filling) {
        if (state.getBlock() instanceof ISpecialMixtureContainerBlock specialBlock) {
            return specialBlock.getTankForMixtureStorageItems(this, level, pos, state, face, player, hand, stack, filling);
        }
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
    }

    /** Typical right-click behaviour: empty the item into the clicked block.*/
    static InteractionResult defaultUseOn(IMixtureStorageItem item, UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        IFluidHandlerItem itemTank = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (itemTank == null) return InteractionResult.PASS;
        IFluidHandler otherTank = item.getTank(level, pos, state, context.getClickedFace(),
            context.getPlayer(), context.getHand(), stack, true);
        InteractionResult result = item.tryEmpty(stack, itemTank, otherTank, context.getPlayer().isCreative());
        item.afterEmpty(level, pos, state, context.getClickedFace(), context.getPlayer(), context.getHand(), stack, result);
        return result;
    }

    /** Typical left-click behaviour: fill the item from the clicked block.*/
    static InteractionResult defaultAttack(IMixtureStorageItem item, Level level, BlockPos pos, BlockState state,
                                           Direction face, Player player, InteractionHand hand, ItemStack stack) {
        IFluidHandlerItem itemTank = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (itemTank == null) return InteractionResult.PASS;
        IFluidHandler otherTank = item.getTank(level, pos, state, face, player, hand, stack, false);
        InteractionResult result = item.tryFill(stack, itemTank, otherTank);
        item.afterFill(level, pos, state, face, player, hand, stack, result);
        return result;
    }

    default void afterEmpty(Level level, BlockPos pos, BlockState state, @Nullable Direction face, Player player, InteractionHand hand, ItemStack stack, InteractionResult result) {
        if (result == InteractionResult.SUCCESS) level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS);
    }

    default void afterFill(Level level, BlockPos pos, BlockState state, @Nullable Direction face, Player player, InteractionHand hand, ItemStack stack, InteractionResult result) {
        if (result == InteractionResult.SUCCESS) level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS);
    }

    static boolean isHolding(Player player, InteractionHand hand) {
        return player.getItemInHand(hand).getItem() instanceof IMixtureStorageItem;
    }

    // ---- content helpers ----

    default boolean isEmpty(ItemStack stack) {
        return getContents(stack).map(FluidStack::isEmpty).orElse(true);
    }

    default int getColor(ItemStack stack) {
        return getContents(stack).map(MixtureFluid::getTintColor).orElse(0xFFFFFFFF);
    }

    default Optional<FluidStack> getContents(ItemStack stack) {
        IFluidHandlerItem cap = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (cap == null) return Optional.empty();
        return Optional.of(cap.drain(getCapacity(stack), FluidAction.SIMULATE));
    }

    default void setContents(ItemStack stack, FluidStack fluidStack) {
        IFluidHandlerItem cap = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (cap != null) cap.fill(fluidStack, FluidAction.EXECUTE);
    }

    default Component getNameWithFluid(ItemStack stack) {
        FluidStack contents = getContents(stack).orElse(FluidStack.EMPTY);
        if (contents.isEmpty()) return Component.translatable(stack.getDescriptionId());
        return Component.translatable(stack.getDescriptionId() + ".filled", contents.getHoverName());
    }

    // Two formatters — concentrationDf (3 digits) drives the M / mM / μM threshold
    // and per-species concentration line; temperatureDf (1 digit) keeps the kelvin
    // header readable. Sharing one formatter forced a choice between "0.3 M" (too
    // coarse for chemistry) and "308.700 K" (visual noise on the temperature line).
    DecimalFormat concentrationDf = _initConcentrationDf();
    DecimalFormat temperatureDf = _initTemperatureDf();

    private static DecimalFormat _initConcentrationDf() {
        DecimalFormat f = new DecimalFormat();
        f.setMinimumFractionDigits(3);
        f.setMaximumFractionDigits(3);
        return f;
    }

    private static DecimalFormat _initTemperatureDf() {
        DecimalFormat f = new DecimalFormat();
        f.setMinimumFractionDigits(1);
        f.setMaximumFractionDigits(1);
        return f;
    }

    /** Add mixture contents description to the item tooltip.*/
    default void addContentsDescription(ItemStack stack, List<Component> tooltip) {
        getContents(stack).ifPresent(fluidStack -> {
            if (fluidStack.isEmpty()) return;

            float temperature = 289f;
            tooltip.add(Component.literal(""));

            CompoundTag mixtureTag = fluidStack.get(DestroyDataComponents.MIXTURE);
            if (mixtureTag != null && !mixtureTag.isEmpty()) {
                ReadOnlyMixture mixture = ReadOnlyMixture.readNBT(ClientMixture::new, mixtureTag);
                boolean iupac = DestroyAllConfigs.CLIENT.chemistry.iupacNames.get();
                temperature = mixture.getTemperature();
                tooltip.addAll(mixture.getContentsTooltip(iupac, false, false, fluidStack.getAmount(), concentrationDf)
                    .stream().map(Component::copy).toList());
            }

            // (tied to DestroyLang port which defers Phase-5/Client). Use plain kelvin display.
            tooltip.add(2, Component.literal(" " + fluidStack.getAmount()).withStyle(ChatFormatting.GRAY)
                .append(CreateLang.translateDirect("generic.unit.millibuckets"))
                .append(" " + temperatureDf.format(temperature) + "K"));
        });
    }
}
