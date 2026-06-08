package petrolpark.mc.destroy.chemistry.legacy.reactionresult;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.world.level.Level;

import petrolpark.mc.destroy.DestroyAdvancementTrigger;
import petrolpark.mc.destroy.chemistry.legacy.LegacyReaction;
import petrolpark.mc.destroy.chemistry.legacy.ReactionResult;
import petrolpark.mc.destroy.core.chemistry.vat.VatControllerBlockEntity;
import petrolpark.mc.destroy.core.data.advancement.DestroyAdvancementBehaviour;

/**
 * A {@link ReactionResult} that awards a {@link DestroyAdvancementTrigger} when the reaction has
 * produced enough of its target molar amount. The award goes to the player who placed the
 * basin / vat (tracked via the BE's {@link DestroyAdvancementBehaviour}).
 */
public class DestroyAdvancementReactionResult extends ReactionResult {

    private final DestroyAdvancementTrigger.Stub advancement;

    public DestroyAdvancementReactionResult(float moles, LegacyReaction reaction,
                                             DestroyAdvancementTrigger.Stub advancement) {
        super(moles, reaction);
        this.advancement = advancement;
    }

    @Override
    public void onBasinReaction(Level level, BasinBlockEntity basin) {
        DestroyAdvancementBehaviour behaviour = basin.getBehaviour(DestroyAdvancementBehaviour.TYPE);
        if (behaviour != null) behaviour.awardDestroyAdvancement(advancement);
    }

    @Override
    public void onVatReaction(Level level, VatControllerBlockEntity vatController) {
        DestroyAdvancementBehaviour behaviour = vatController.getBehaviour(DestroyAdvancementBehaviour.TYPE);
        if (behaviour != null) behaviour.awardDestroyAdvancement(advancement);
    }
}
