package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.TestHulls;
import com.dillon.starsectormarines.marine.FabricationCost;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShipPreparationPreflightTest {
    @Test void changedCargoIsRecheckedBeforeDesignationOrTransferCharge() {
        MarineRoster roster = new MarineRoster();
        MarineCaptain mira = new MarineCaptain("Mira Hale", null, Rank.LIEUTENANT, 0);
        roster.add(mira); roster.beginCompanyPreparation(mira);
        var candidate = new ShipTransferViewModel.Candidate("new", "New Home", "Transport", 100, 200, TestHulls.transport());
        var means = CompanyMeans.of(12, 0, 100_000);
        int[] supplies = {76};
        FabricationResources resources = new FabricationResources() {
            @Override public int available(String id) { return Commodities.SUPPLIES.equals(id) ? supplies[0] : 100; }
            @Override public String commodityName(String id) { return id; }
            @Override public String commodityIcon(String id) { return ""; }
            @Override public boolean spend(FabricationCost cost) { fail("Preflight must not consume cargo"); return false; }
        };
        var model = new ShipTransferViewModel(new Reactor(), () -> List.of(candidate),
                () -> new CompanyShipDesignation.Home("old", null, false, 0),
                id -> roster.setCompanyShip(id, "New Home", 200), means);
        model.preparation(roster, resources);
        assertFalse(model.preparationBlocked());
        assertTrue(model.preparationBill().contains("76 / 76 / 0"));
        supplies[0] = 75;
        model.commit();
        assertNull(roster.companyShipId());
        assertEquals(100_000, means.credits());
        assertTrue(model.preparationBill().contains("76 / 75 / 1"));
        supplies[0] = 76;
        model.commit();
        assertEquals("new", roster.companyShipId());
        assertTrue(means.credits() < 100_000);
        assertEquals(76, supplies[0]);
    }
}
