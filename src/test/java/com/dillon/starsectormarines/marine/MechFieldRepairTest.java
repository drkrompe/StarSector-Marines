package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MechFieldRepairTest {
    @Test void repairsAreShorterButNeverBelowTheShortestThereIs() {
        var jigs = new MechFieldRepair(0.75f, 1f);
        assertEquals(3f, jigs.repairDays(4f), 1e-5f);
        assertEquals(MechRepairs.MINIMUM_DAYS, jigs.repairDays(MechRepairs.MINIMUM_DAYS));
        assertEquals(4f, MechFieldRepair.NEUTRAL.repairDays(4f));
    }

    @Test void theSparesRackTakesAShareOffTheBillRoundedUpButNeverToNothing() {
        var base = new FabricationCost(1001);
        assertEquals(751, new MechFieldRepair(1f, 0.75f).weaponBill(base).credits());
        assertEquals(1, new MechFieldRepair(1f, 0.01f).weaponBill(new FabricationCost(1)).credits());
        assertSame(base, MechFieldRepair.NEUTRAL.weaponBill(base));
    }
}
