package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechPatternCreditsTest {

    @Test
    void everyBuildablePatternIsCreditedAndAgreesWithWhoSellsIt() {
        for (MechFabricationCatalog.Recipe recipe : MechFabricationCatalog.chassis()) {
            assertNotNull(MechPatternCredits.of(MechPattern.of(recipe.variant())), recipe.id());
        }
        for (MechFabricationCatalog.Recipe recipe : MechFabricationCatalog.vehicles()) {
            VehicleType type = recipe.vehicle();
            MechPatternCredits.Credit credit = MechPatternCredits.of(MechPattern.of(type));
            assertNotNull(credit, recipe.id());
            if (type.isPickup()) {
                assertTrue(MechPatternPicker.FIELD_RIG_FACTIONS.contains(credit.factionId()), recipe.id());
            }
        }
        for (String faction : MechPatternPicker.issuingFactions()) {
            for (MechPattern pattern : MechPatternPicker.factionIssue(faction)) {
                if (pattern.kind() != MechPattern.Kind.VEHICLE) continue;
                assertEquals(faction, MechPatternCredits.of(pattern).factionId(), pattern.id());
            }
        }
    }
}
