package petrolpark.mc.destroy.chemistry.legacy.index.genericreaction;

import petrolpark.mc.destroy.chemistry.legacy.LegacyAtom;
import petrolpark.mc.destroy.chemistry.legacy.LegacyElement;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMolecularStructure;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture;
import petrolpark.mc.destroy.chemistry.legacy.LegacyReaction;
import petrolpark.mc.destroy.chemistry.legacy.LegacyReaction.ReactionBuilder;
import petrolpark.mc.destroy.chemistry.legacy.genericreaction.GenericReactant;
import petrolpark.mc.destroy.chemistry.legacy.genericreaction.SingleGroupGenericReaction;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyGroupTypes;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.legacy.index.group.HalideGroup;

import net.minecraft.resources.ResourceLocation;

public abstract class HalideSubstitution extends SingleGroupGenericReaction<HalideGroup> {

    public HalideSubstitution(ResourceLocation id) {
        super(id, DestroyGroupTypes.HALIDE);
    };

    @Override
    public boolean isPossibleIn(ReadOnlyMixture mixture) {
        LegacySpecies nucleophile = getNucleophile();
        if (nucleophile != null) return mixture.getConcentrationOf(nucleophile) > 0f;
        return true;
    };

    @Override
    public LegacyReaction generateReaction(GenericReactant<HalideGroup> reactant) {
        LegacySpecies reactantMolecule = reactant.getMolecule();
        HalideGroup halideGroup = reactant.getGroup();
        LegacySpecies productMolecule = moleculeBuilder().structure(reactantMolecule.shallowCopyStructure()
            .moveTo(halideGroup.carbon)
            .addGroup(getSubstitutedGroup(), true)
            .remove(halideGroup.halogen)
        )
        .build();
        ReactionBuilder builder = reactionBuilder()
            .addReactant(reactantMolecule)
            .addProduct(productMolecule)
            .addProduct(getIon(halideGroup.halogen));
        LegacySpecies nucleophile = getNucleophile();
        if (nucleophile != null) builder.addReactant(nucleophile, 1, halideGroup.degree == 3 ? 0 : 1);
        transform(builder, halideGroup);
        return builder.build();
    };

    /**
 * The nucleophile substituting the carbon group.
 * @param builder
 * @return {@code null} to not add a species to the generated Reaction or Reaction requirements
*/
    public abstract LegacySpecies getNucleophile();

    public abstract LegacyMolecularStructure getSubstitutedGroup();

    /**
 * Add any other necessary products, reactants, catalysts and rate constants to the Reaction.
 * @param builder The builder with the Halide reactant, organic product and halide ion product already added
*/
    public void transform(ReactionBuilder builder, HalideGroup group) {};

    public LegacySpecies getIon(LegacyAtom atom) {
        // Phase 2b: LegacyElement was an enum (allowing `case CONSTANT:` shorthand) but is now
        // a class to support datapack-defined elements — switch needs the qualified == form.
        LegacyElement el = atom.getElement();
        if (el == LegacyElement.FLUORINE) return DestroyMolecules.FLUORIDE;
        if (el == LegacyElement.CHLORINE) return DestroyMolecules.CHLORIDE;
        if (el == LegacyElement.IODINE) return DestroyMolecules.IODIDE;
        throw new GenericReactionGenerationException(el.toString() + " is not a halogen.");
    };
    
};
