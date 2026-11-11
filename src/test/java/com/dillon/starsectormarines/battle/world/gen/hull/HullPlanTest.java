package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.world.gen.hull.HullPlan.Entrance;
import com.dillon.starsectormarines.battle.world.gen.hull.HullPlan.Hall;
import com.dillon.starsectormarines.battle.world.gen.hull.HullPlan.HallUse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The fallen hull's interior laws, proved on its plan before it meets a map ({@code fallen-hull-nouns.md}). */
class HullPlanTest {

    private final HullPlan plan = HullPlan.plan();

    @Test
    void eachSectionsHallsAndWaysInJoinItsOwnDeckAndNoOther() {
        for (HullPlan.Section section : HullPlan.SECTIONS) {
            Entrance first = null;
            for (Entrance e : plan.entrances()) if (section.contains(e.door().u0()) && first == null) first = e;
            boolean[] reach = plan.reach(first.door().u0(), first.door().v0());
            for (Hall hall : plan.halls()) {
                if (!section.contains(hall.floor().u0())) continue;
                int u = (hall.floor().u0() + hall.floor().u1()) / 2, v = (hall.floor().v0() + hall.floor().v1()) / 2;
                assertTrue(reach[u * HullPlan.BREADTH + v], hall.name() + " is reached in " + section.name());
            }
            for (Entrance e : plan.entrances()) {
                boolean mine = section.contains(e.door().u0());
                assertEquals(mine, reach[e.door().u0() * HullPlan.BREADTH + e.door().v0()],
                        e.name() + (mine ? " joins " : " is apart from ") + section.name());
                // A way in opens onto the outside: the step past its outer cell leaves its section.
                int u = e.du() < 0 ? e.door().u0() : e.du() > 0 ? e.door().u1() : e.door().u0();
                int v = e.dv() < 0 ? e.door().v0() : e.dv() > 0 ? e.door().v1() : e.door().v0();
                assertEquals(HullPlan.Kind.OUTSIDE, plan.near(u, v, e.du(), e.dv()), e.name() + " opens outside");
            }
            if (section.contains(HullPlan.FORGE_U)) {
                assertTrue(reach[HullPlan.FORGE_U * HullPlan.BREADTH + HullPlan.FORGE_V], "the autoforge is reached");
            }
        }
    }

    @Test
    void sectionsAreLaidApartAlongTheLineAndALaidCellMapsBack() {
        for (int u = 0; u < HullPlan.LENGTH; u++) {
            assertEquals(u, HullPlan.planU(HullPlan.laidU(HullPlan.section(u), u)));
        }
        HullPlan.Section mid = HullPlan.SECTIONS.get(1);
        // The ground between the stern and the midsection is no hull.
        assertEquals(-1, HullPlan.planU(HullPlan.laidU(HullPlan.section(mid.u0() - 1), mid.u0() - 1) + 1));
        assertEquals(HullPlan.LENGTH + 18 + 22, HullPlan.LAID_LENGTH);
        assertTrue(plan.sheared(mid.u0(), 0), "a broken end's corner is the cut face");
        assertEquals(false, plan.sheared(0, 0), "the stern is no broken end");
    }

    @Test
    void aSpreadLayingStringsTheSectionsApartAndNeverCloserThanTheyBroke() {
        HullPlan.Laying spread = HullPlan.Laying.between(List.of(115, 95));
        assertEquals(HullPlan.LENGTH + 115 + 95, spread.length());
        HullPlan.Section mid = HullPlan.SECTIONS.get(1), bow = HullPlan.SECTIONS.get(2);
        assertEquals(mid.u0() + 115, spread.laidU(mid, mid.u0()));
        assertEquals(bow.u0() + 115 + 95, spread.laidU(bow, bow.u0()));
        for (int u = 0; u < HullPlan.LENGTH; u++) assertEquals(u, spread.planU(spread.laidU(HullPlan.section(u), u)));
        assertEquals(-1, spread.planU(mid.u0() + 60), "the furrow between is no hull");
        assertThrows(IllegalArgumentException.class, () -> HullPlan.Laying.between(List.of(10, 95)));
        assertEquals(HullPlan.LAID_LENGTH, HullPlan.Laying.CLOSE.length());
    }

    @Test
    void waysAreSizedForTheDerelictLine() {
        // derelict-watch.md section 3: a hall frame walks three-cell joins and wider, never a corridor.
        assertTrue(HullPlan.SPINE.breadth() >= 4, "the spine is a freight way");
        for (Hall hall : plan.halls()) {
            boolean throughU = hall.door().u0() == hall.door().u1();
            int width = HullPlan.doorWidth(hall.door(), throughU);
            if (hall.use() == HallUse.CREW) assertEquals(2, width, "the crew deck's door is a service door");
            else assertTrue(width >= 4, hall.name() + "'s dock fits a hall frame");
        }
    }

    @Test
    void theForgeIsTheLargestHallAndHoldsTheAutoforge() {
        Hall forge = plan.hall("forge");
        assertTrue(forge.floor().contains(HullPlan.FORGE_U, HullPlan.FORGE_V));
        for (Hall hall : plan.halls()) {
            assertTrue(hall.floor().length() * hall.floor().breadth() <= forge.floor().length() * forge.floor().breadth());
        }
    }

    @Test
    void theBowTapersAndEverythingCarvedIsInsideTheOutline() {
        assertTrue(HullPlan.halfBreadth(HullPlan.LENGTH - 1) < HullPlan.halfBreadth(0));
        for (int u = 0; u < HullPlan.LENGTH; u++) {
            for (int v = 0; v < HullPlan.BREADTH; v++) {
                if (plan.open(u, v)) assertTrue(HullPlan.inOutline(u, v), "open deck at " + u + "," + v + " is in the hull");
            }
        }
    }
}
