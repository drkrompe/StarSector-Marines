package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.world.gen.hull.HullSite.HaulRoad;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSite.LatticePoint;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Cell;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The claim lattice and its haul roads ({@code fallen-hull-nouns.md}, Claim lattice, Hauler). */
class ClaimLatticeTest {

    @Test
    void pointsStandOnARegularGridRoundTheMiddleInRingOrder() {
        List<LatticePoint> lattice = ClaimLattice.lay(100, 100, 50, 50, 20, (x, y) -> true);
        // Every grid point but the middle, 20 apart, inside the map's edge: 5 x 5 less one.
        assertEquals(24, lattice.size());
        for (LatticePoint p : lattice) {
            assertEquals(50 + p.col() * 20, p.cell().x());
            assertEquals(50 + p.row() * 20, p.cell().y());
            assertEquals(Math.max(Math.abs(p.col()), Math.abs(p.row())), p.ring());
        }
        for (int i = 1; i < lattice.size(); i++) assertTrue(lattice.get(i - 1).ring() <= lattice.get(i).ring());
    }

    @Test
    void aPointMovesToTheNearestStakeGroundOrIsAHole() {
        // A wall down column 70 to 72: the point at 70 steps aside to the nearest 5x5 of open ground.
        List<LatticePoint> moved = ClaimLattice.lay(100, 100, 50, 50, 20, (x, y) -> x < 70 || x > 72);
        LatticePoint east = point(moved, 1, 0);
        assertEquals(67, east.cell().x(), "two cells clear of the wall, toward it");
        assertEquals(50, east.cell().y());
        // Nothing within a quarter spacing: no point.
        List<LatticePoint> holed = ClaimLattice.lay(100, 100, 50, 50, 20, (x, y) -> Math.abs(x - 70) > 9);
        assertNull(point(holed, 1, 0));
    }

    @Test
    void haulRoadsGrowAsOneTreeFromTheHullSoNeighboursShareATrunk() {
        // A wall across row 30 with one gap at column 10: both points south of it go home through the gap.
        HaulRoads.Walk walk = (x, y) -> y != 30 || x == 10;
        List<LatticePoint> points = List.of(new LatticePoint(0, 0, 0, 1, new Cell(5, 50)),
                new LatticePoint(1, 0, 0, 1, new Cell(15, 50)),
                new LatticePoint(2, 0, 0, 1, new Cell(5, 29)));
        List<HaulRoad> roads = HaulRoads.lay(40, 60, walk, List.of(new Cell(10, 5)), points);
        assertEquals(3, roads.size());
        for (HaulRoad road : roads.subList(0, 2)) {
            assertEquals(points.get(road.point()).cell(), road.cells().get(0), "a road starts at its point");
            assertEquals(new Cell(10, 5), road.cells().get(road.cells().size() - 1), "and ends at the hull");
            assertTrue(road.cells().contains(new Cell(10, 30)), "through the gap");
        }
        // Shortest by cardinal steps.
        assertEquals(Math.abs(5 - 10) + Math.abs(29 - 5) + 1, roads.get(2).cells().size());
    }

    @Test
    void aPointWithNoWayHomeHasNoRoad() {
        HaulRoads.Walk walk = (x, y) -> y != 30;
        List<HaulRoad> roads = HaulRoads.lay(40, 60, walk, List.of(new Cell(10, 5)),
                List.of(new LatticePoint(0, 0, 0, 1, new Cell(5, 50))));
        assertTrue(roads.isEmpty());
    }

    private static LatticePoint point(List<LatticePoint> lattice, int col, int row) {
        for (LatticePoint p : lattice) if (p.col() == col && p.row() == row) return p;
        return null;
    }
}
