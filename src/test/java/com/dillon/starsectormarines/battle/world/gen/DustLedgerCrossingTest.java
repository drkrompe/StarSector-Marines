package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Saltwell Cross keeps every mission site reachable from the apron and its three approaches open. */
class DustLedgerCrossingTest {

    @BeforeAll
    static void catalogs() throws Exception {
        DiskRegistries.install();
    }

    @Test
    void everySiteIsReachedFromTheApronAndEachApproachStaysOpen() {
        TargetProfile asharu = new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.ARID, SettlementLink.ROAD);
        DustLedgerCrossing.Layout layout = DustLedgerCrossing.generate(6150, asharu);
        NavigationGrid grid = layout.map().grid;
        boolean[] reached = DustLedgerCrossing.reach(grid, layout.companyStart(), null);
        List<DustLedgerCrossing.Cell> sites = new ArrayList<>(List.of(layout.ledgerTarget(), layout.brokerOffice(),
                layout.skimmerBoarding(), layout.gate(), layout.postern(), layout.southGate(), layout.wellHouse(),
                layout.crateDrop(), layout.couplingCrate()));
        sites.addAll(layout.salvageTeam());
        layout.posts().values().forEach(sites::addAll);
        sites.addAll(layout.spotterPosts());
        sites.addAll(layout.canalCrossings());
        sites.addAll(layout.ridgeOverwatch());
        for (DustLedgerCrossing.Cell site : sites) assertTrue(reached[grid.index(site.x(), site.y())], site.toString());
        for (DustLedgerCrossing.Approach approach : DustLedgerCrossing.Approach.values())
            assertTrue(DustLedgerCrossing.approachOpen(grid, layout.approaches().get(approach)), approach.name());
        // The ridge is high ground: its overwatch stands a tier above the compound it looks down on.
        DustLedgerCrossing.Cell rim = layout.ridgeOverwatch().get(0);
        assertEquals(1, grid.elevation().tier(rim.x(), rim.y()));
        // Sable's rocket truck has a lane: every stand of its is reached from its park by a light hull.
        VehicleClearance lane = VehicleClearance.erode(grid, VehicleClearance.radiusForWidth(VehicleType.SRM_TECHNICAL.visualWidthCells));
        DustLedgerCrossing.Cell park = layout.technicalStands().get(0);
        for (DustLedgerCrossing.Cell stand : layout.technicalStands().subList(1, layout.technicalStands().size()))
            assertTrue(GridPathfinder.findPathOnDemand(grid, park.x(), park.y(), stand.x(), stand.y(), index -> 1f,
                    lane::isPassableAt).length >= 2, "no lane to the truck's stand " + stand);
        // Buildings are held places only; the map brings no capture point of its own.
        for (TacticalNode node : layout.map().tacticalMap.all())
            assertEquals(TacticalNode.Kind.INNER_POSITION, node.kind);
    }

    @Test
    void anApproachIsClosedWhenItsOwnGroundIsCutEvenIfAnotherWayRoundExists() {
        NavigationGrid grid = new NavigationGrid(40, 30);
        for (int y = 0; y < 30; y++) for (int x = 0; x < 40; x++) grid.setWalkableFloor(x, y);
        List<DustLedgerCrossing.Cell> line = List.of(new DustLedgerCrossing.Cell(2, 5), new DustLedgerCrossing.Cell(37, 5));
        assertTrue(DustLedgerCrossing.approachOpen(grid, line));
        // A wall across the line's corridor, with a gap far to the north beyond it.
        for (int y = 0; y < 26; y++) grid.setWalkable(20, y, false);
        assertFalse(DustLedgerCrossing.approachOpen(grid, line));
        assertTrue(DustLedgerCrossing.reach(grid, line.get(0), null)[grid.index(37, 5)]);
    }
}
