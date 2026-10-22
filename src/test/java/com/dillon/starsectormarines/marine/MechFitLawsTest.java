package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechFrame;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fitting laws of {@code heavy-equipment-progression.md} as arithmetic over the engine budget
 * and the authored dolls: the stock fit and a full internal refit around the factory weapons fit
 * every chassis, the best of everything fits none, and no refit carries a chassis past the stock
 * frame of the class above it. The readings behind these are {@code MechFitSpaceEvidence}.
 */
class MechFitLawsTest {

    @Test
    void theStockFitAndAFullRefitAroundTheFactoryWeaponsFitEveryChassis() {
        for (MechVariant variant : MechVariant.values()) {
            assertFalse(MechFitSpace.stock(variant).overBudget(), variant + " stock");
            assertFalse(MechFitSpace.fullInternalRefit(variant).overBudget(),
                    variant + " full internal refit around its factory weapons");
        }
    }

    @Test
    void theBestOfEverythingNeverFitsOnOneFrame() {
        for (MechVariant variant : MechVariant.values()) {
            MechFrameBudget.Fit best = MechFitSpace.bestOfEverything(variant);
            assertTrue(best.overBudget(), variant + " carries the top of every ladder: "
                    + best.fittedMass() + " of " + best.output());
        }
    }

    @Test
    void aRefitStaysInsideItsWeightClass() {
        MechFrame refitHound = MechFitSpace.bestRefitAroundStockWeapons(MechVariant.HOUND).frame();
        MechFrame refitWarden = MechFitSpace.bestRefitAroundStockWeapons(MechVariant.WARDEN).frame();
        MechFrame stockBulwark = MechFrame.stock(MechVariant.BULWARK);
        assertTrue(refitHound.armorCapacity() < stockBulwark.armorCapacity());
        assertTrue(refitWarden.armorCapacity() < refitHound.armorCapacity(),
                "a refit Warden must not out-plate a refit Hound");
        for (MechVariant variant : MechVariant.values()) {
            MechFrame refit = MechFitSpace.bestRefitAroundStockWeapons(variant).frame();
            MechFrame preCut = MechFitSpace.preCut(variant);
            assertTrue(refit.armorCapacity() <= preCut.armorCapacity() + 1e-3f, variant + " armor past its pre-cut value");
            assertTrue(refit.moveSpeed() <= preCut.moveSpeed() + 1e-4f, variant + " speed past its pre-cut value");
        }
    }
}
