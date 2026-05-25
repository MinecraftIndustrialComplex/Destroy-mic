package petrolpark.mc.destroy.chemistry.legacy.index.genericreaction;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.chemistry.legacy.LegacyAtom;
import petrolpark.mc.destroy.chemistry.legacy.LegacyElement;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMolecularStructure;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture;
import petrolpark.mc.destroy.chemistry.legacy.LegacyReaction;
import petrolpark.mc.destroy.chemistry.legacy.LegacyBond.BondType;
import petrolpark.mc.destroy.chemistry.legacy.genericreaction.DoubleGroupGenericReaction;
import petrolpark.mc.destroy.chemistry.legacy.genericreaction.GenericReactant;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyGroupTypes;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.legacy.index.group.HalideGroup;
import petrolpark.mc.destroy.chemistry.legacy.index.group.NonTertiaryAmineGroup;

public class HalideAmineSubstitution extends DoubleGroupGenericReaction<HalideGroup, NonTertiaryAmineGroup> {

    public HalideAmineSubstitution() {
        super(Destroy.asResource("halide_amine_substitution"), DestroyGroupTypes.HALIDE, DestroyGroupTypes.NON_TERTIARY_AMINE);
    };

    @Override
    public boolean isPossibleIn(ReadOnlyMixture mixture) {
        return true;
    };

    @Override
    public LegacyReaction generateReaction(GenericReactant<HalideGroup> firstReactant, GenericReactant<NonTertiaryAmineGroup> secondReactant) {
        LegacyMolecularStructure halideStructureCopy = firstReactant.getMolecule().shallowCopyStructure();
        HalideGroup halideGroup = firstReactant.getGroup();
        LegacyMolecularStructure amineStructureCopy = secondReactant.getMolecule().shallowCopyStructure();
        NonTertiaryAmineGroup amineGroup = secondReactant.getGroup();

        halideStructureCopy
            .moveTo(halideGroup.carbon)
            .remove(halideGroup.halogen);

        amineStructureCopy
            .moveTo(amineGroup.nitrogen)
            .remove(amineGroup.hydrogen);

        LegacySpecies substitutedAmine = moleculeBuilder().structure(LegacyMolecularStructure.joinFormulae(halideStructureCopy, amineStructureCopy, BondType.SINGLE)).build();

        return reactionBuilder()
            .addReactant(firstReactant.getMolecule())
            .addReactant(secondReactant.getMolecule(), 1, 2)
            .addProduct(substitutedAmine)
            .addProduct(getIon(halideGroup.halogen))
            .addProduct(DestroyMolecules.PROTON)
            // TODO kinetics
            .build();
    };

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
