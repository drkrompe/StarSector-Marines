package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.DustLedgerCrossing;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DustLedgerSitesTest {

    @Test
    void saltwellCrossYieldsSitesTheSequenceAccepts() {
        var layout = DustLedgerCrossing.generate(7, new TargetProfile(3, 5, 1, 0, "pirates", Set.of(),
                SurfacePalette.ARID, SettlementLink.ROAD));
        var sites = DustLedgerSites.from(layout); // the Sites constructor holds the ring's inside and outside rules
        assertEquals(layout.posts().get(DustLedgerCrossing.PostRole.CANTINA).size(), sites.drinkerCells().size());
        assertEquals(layout.salvageTeam().size(), sites.salvageTeamCells().size());
        assertTrue(sites.ledgerRoom().contains(layout.ledgerTarget().x(), layout.ledgerTarget().y()));
    }

    @Test
    void aBuildingAreaCoversItsWholeFootprint() {
        var building = new PointOfInterest(PointOfInterest.Kind.RESIDENTIAL, 2, 5, 4, 3, 3, 4, 3, 4);
        var area = DustLedgerSites.area(building);
        assertEquals(9, area.cells().size());
        assertTrue(area.contains(2, 3) && area.contains(4, 5));
        assertFalse(area.contains(5, 4));
    }
}
