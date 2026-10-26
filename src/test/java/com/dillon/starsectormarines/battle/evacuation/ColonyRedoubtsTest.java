package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a holdout may stand in relation to the breach's bores. */
class ColonyRedoubtsTest {

    private static ColonyEruptionPlan.Site site(int x, int y) {
        return new ColonyEruptionPlan.Site(1, x, y, ColonyEruptionPlan.Role.FLANK, 0, PointOfInterest.Kind.values()[0]);
    }

    @Test
    void aHoldoutStandsTheClearanceFromEveryBoreSite() {
        int r = ColonyRedoubts.BREACH_CLEARANCE;
        List<ColonyEruptionPlan.Site> sites = List.of(site(100, 100), site(300, 100));
        assertTrue(ColonyRedoubts.clearOfBores(100 + r, 100, sites), "at the clearance is clear");
        assertFalse(ColonyRedoubts.clearOfBores(100 + r - 1, 100, sites), "inside one site's ring is not");
        assertFalse(ColonyRedoubts.clearOfBores(300, 100 + r - 1, sites), "every site keeps its ring");
        assertTrue(ColonyRedoubts.clearOfBores(5, 5, List.of()), "no bores, no ring");
    }

    @Test
    void everyRedoubtButTheShelterIsAHoldoutAtItsMiddle() {
        List<int[]> centres = ColonyRedoubts.holdoutCentres(List.of(
                new CivilianRedoubt(1, 0, 0, 100, 200, 120, 180, 0, 8, -1, -1, 8, 0, -1, -1, -1),
                new CivilianRedoubt(2, 0, 0, 30, 140, 40, 130, 0, 3, -1, -1, 3, 0, -1, -1, -1)));
        assertEquals(1, centres.size(), "the shelter is not a holdout");
        assertArrayEquals(new int[]{35, 135}, centres.get(0));
    }
}
