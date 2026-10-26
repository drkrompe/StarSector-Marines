package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.TestHulls;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipsBoats;
import com.dillon.starsectormarines.marine.CompanyPreparation;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.SquadFoundingCost;
import com.dillon.starsectormarines.marine.SquadFoundingResources;
import com.dillon.starsectormarines.marine.TestPurse;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShipPreparationPreflightTest {
    @Test void changedCargoIsRecheckedBeforeDesignationOrTransferCharge() {
        MarineRoster roster = new MarineRoster();
        MarineCaptain sabine = new MarineCaptain("Sabine Hale", null, Rank.LIEUTENANT, 0);
        roster.add(sabine); roster.beginCompanyPreparation(sabine);
        var candidate = new ShipTransferViewModel.Candidate("new", "New Home", "Transport", 100, 200, TestHulls.transport());
        var means = CompanyMeans.of(12, 0, 100_000);
        var hull = TestHulls.transport();
        // The purse pays the boat and the squad's stores together; the hold supplies the marines.
        int price = CompanyPreparation.boatPrice(roster, ShipsBoats.carriedBy(hull)) + CompanyPreparation.squadPrice(roster);
        TestPurse purse = new TestPurse(price);
        int[] marines = {12};
        SquadFoundingResources stores = new SquadFoundingResources() {
            @Override public int available(String id) { return Commodities.MARINES.equals(id) ? marines[0] : 100; }
            @Override public long credits() { return purse.credits; }
            @Override public boolean atMarket() { return true; }
            @Override public String commodityName(String id) { return id; }
            @Override public String commodityIcon(String id) { return ""; }
            @Override public boolean spend(SquadFoundingCost cost) { fail("Preflight must not consume cargo"); return false; }
        };
        var model = new ShipTransferViewModel(new Reactor(), () -> List.of(candidate),
                () -> new CompanyShipDesignation.Home("old", null, false, 0),
                id -> roster.setCompanyShip(id, "New Home", 200), means);
        model.preparation(roster, purse, stores);
        assertFalse(model.preparationBlocked());
        marines[0] = 11;
        model.commit();
        assertNull(roster.companyShipId());
        assertEquals(100_000, means.credits());
        marines[0] = 12;
        purse.credits = price - 1;
        model.commit();
        assertNull(roster.companyShipId(), "A boat the credits cannot pay for blocks designation too");
        purse.credits = price;
        model.commit();
        assertEquals("new", roster.companyShipId());
        assertTrue(means.credits() < 100_000);
        assertEquals(12, marines[0]);
        assertEquals(0, purse.spends, "Designation checks the stores; the workshops spend them later");
    }
}
