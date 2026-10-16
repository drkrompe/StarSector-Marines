package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.CompanyMechLedger;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A repair order is atomic: the whole bill and the clock, or nothing. */
class MechWorkshopRepairTest {

    @Test void aPaidRepairSpendsTheQuoteAndStartsTheClock() {
        MechBay bay = new MechBay();
        CampaignMech mech = damagedBulwark(bay);
        MechWorkshopTest.TestResources resources = MechWorkshopTest.TestResources.stocked(500);
        MechWorkshop workshop = new MechWorkshop(bay, resources);
        MechWorkshop.RepairQuote quote = workshop.repairQuote(mech.id());
        assertNotNull(quote);

        MechWorkshop.Result result = workshop.repair(mech.id(), 10f);

        assertEquals(MechWorkshop.Status.REPAIR_STARTED, result.status());
        assertSame(mech, result.mech());
        assertEquals(500 - quantity(quote.bill(), Commodities.METALS), resources.available(Commodities.METALS));
        assertNull(mech.damage());
        assertEquals(10f + quote.days(), mech.repairUntilDay(), 1e-4f);
        assertEquals(CampaignMech.Readiness.UNDER_REPAIR, mech.readiness(10f));
        assertEquals(CampaignMech.Readiness.READY, mech.readiness(10f + quote.days()));
    }

    @Test void aShortHoldRefusesAndChangesNothing() {
        MechBay bay = new MechBay();
        CampaignMech mech = damagedBulwark(bay);
        MechWorkshopTest.TestResources resources = MechWorkshopTest.TestResources.stocked(500);
        MechWorkshop workshop = new MechWorkshop(bay, resources);
        int rare = quantity(workshop.repairQuote(mech.id()).bill(), Commodities.RARE_METALS);
        resources.spend(new FabricationCost(List.of(
                new FabricationCost.Line(Commodities.RARE_METALS, 500 - rare + 1))));
        MechDamage before = mech.damage();

        MechWorkshop.Result result = workshop.repair(mech.id(), 10f);

        assertEquals(MechWorkshop.Status.INSUFFICIENT_MATERIALS, result.status());
        assertSame(before, mech.damage());
        assertEquals(0f, mech.repairUntilDay());
        assertEquals(500, resources.available(Commodities.SUPPLIES));
        assertEquals(rare - 1, resources.available(Commodities.RARE_METALS));
        assertEquals(CampaignMech.Readiness.DAMAGED, mech.readiness(10f));
    }

    @Test void aWholeMechHasNothingToRepair() {
        MechBay bay = new MechBay();
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);
        MechWorkshop workshop = new MechWorkshop(bay, MechWorkshopTest.TestResources.stocked(500));

        assertNull(workshop.repairQuote(mech.id()));
        assertEquals(MechWorkshop.Status.NOT_DAMAGED, workshop.repair(mech.id(), 0f).status());
        assertEquals(MechWorkshop.Status.INVALID, workshop.repair("missing", 0f).status());
    }

    private static CampaignMech damagedBulwark(MechBay bay) {
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.BULWARK);
        bay.settleBattleDamage(List.of(new CompanyMechLedger.Damage(mech.id(),
                MechDamageTest.ReportFixture.whole().destroyed(MechSection.LEGS, null)
                        .section(MechSection.TORSO, 0.6f, 0f).build(), false)));
        return mech;
    }

    private static int quantity(FabricationCost cost, String commodityId) {
        return cost.lines().stream().filter(line -> line.commodityId().equals(commodityId))
                .mapToInt(FabricationCost.Line::quantity).sum();
    }
}
