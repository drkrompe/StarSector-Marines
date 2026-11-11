package com.dillon.starsectormarines.battle.derelict;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimLatticeTest {

    /** A hull at x 0..4 and a row of points eastward, ten cells apart; links reach ten cells. */
    private static ClaimLattice row() {
        return new ClaimLattice(new FallenHullSite.Rect(0, 0, 4, 4), List.of(
                new ClaimLattice.Point(0, 14, 2), new ClaimLattice.Point(1, 24, 2),
                new ClaimLattice.Point(2, 34, 2), new ClaimLattice.Point(3, 12, 12)), 10);
    }

    @Test
    void theForgePlantsTheNearestOpenPointLinkedToTheHull() {
        ClaimLattice.Plan plan = row().nextPlantable(Set.of(0, 1, 2, 3), Set.of());
        assertEquals(0, plan.point().id());
        assertEquals(ClaimLattice.HULL, plan.parent());
        assertEquals(3, row().nextPlantable(Set.of(1, 2, 3), Set.of()).point().id(), "the next point the hull reaches");
    }

    @Test
    void aPointBeyondIsPlantableOnlyThroughAnEstablishedStake() {
        ClaimLattice lattice = row();
        assertNull(lattice.nextPlantable(Set.of(1, 2), Set.of()), "nothing links the far points to the hull");
        ClaimLattice.Plan plan = lattice.nextPlantable(Set.of(1, 2), Set.of(0));
        assertEquals(1, plan.point().id());
        assertEquals(0, plan.parent(), "its chain runs through the stake it was reached from");
        assertNull(lattice.nextPlantable(Set.of(2), Set.of(0)), "one point beyond an anchor, never two");
    }

    @Test
    void aPointLinkedToTheHullRunsItsChainStraightToIt() {
        assertEquals(ClaimLattice.HULL, row().nextPlantable(Set.of(3), Set.of(0)).parent());
    }

    @Test
    void aBrokenHullLinksAPointThroughTheNearestSectionNotTheGapsBox() {
        // Two sections, x 0..9 and 30..39, with a gap between; a point off the gap's side lies within the laid box's
        // reach but far from either section.
        List<FallenHullSite.Rect> sections = List.of(new FallenHullSite.Rect(0, 0, 9, 4),
                new FallenHullSite.Rect(30, 0, 39, 4));
        ClaimLattice lattice = new ClaimLattice(sections, List.of(new ClaimLattice.Point(0, 20, 14),
                new ClaimLattice.Point(1, 40, 2), new ClaimLattice.Point(2, 14, 6)), 8);
        assertFalse(lattice.linksToHull(lattice.point(0)), "off the gap, ten cells from either section");
        assertTrue(lattice.linksToHull(lattice.point(1)), "just off the far section's end");
        assertEquals(1, lattice.nextPlantable(Set.of(1, 2), Set.of()).point().id(),
                "the nearer the plates is planted first, wherever the laid box's middle lies");
    }
}
