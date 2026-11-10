package com.dillon.starsectormarines.battle.derelict;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
