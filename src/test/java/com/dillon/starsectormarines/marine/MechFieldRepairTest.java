package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MechFieldRepairTest {
    @Test void repairsAreShorterButNeverBelowTheShortestThereIs() {
        var jigs = new MechFieldRepair(0.75f, 1f);
        assertEquals(3f, jigs.repairDays(4f), 1e-5f);
        assertEquals(MechRepairs.MINIMUM_DAYS, jigs.repairDays(MechRepairs.MINIMUM_DAYS));
        assertEquals(4f, MechFieldRepair.NEUTRAL.repairDays(4f));
    }

    @Test void theSparesRackTakesAShareOffEveryLineRoundedUp() {
        var base = new FabricationCost(List.of(new FabricationCost.Line(Commodities.METALS, 8),
                new FabricationCost.Line(Commodities.RARE_METALS, 3), new FabricationCost.Line(Commodities.SUPPLIES, 1)));
        var bill = new MechFieldRepair(1f, 0.75f).weaponBill(base);
        assertEquals(List.of(6, 3, 1), bill.lines().stream().map(FabricationCost.Line::quantity).toList());
        assertSame(base, MechFieldRepair.NEUTRAL.weaponBill(base));
    }
}
