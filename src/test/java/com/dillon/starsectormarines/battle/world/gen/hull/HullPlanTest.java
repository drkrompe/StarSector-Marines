package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.world.gen.hull.HullPlan.Entrance;
import com.dillon.starsectormarines.battle.world.gen.hull.HullPlan.Hall;
import com.dillon.starsectormarines.battle.world.gen.hull.HullPlan.HallUse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The fallen hull's interior laws, proved on its plan before it meets a map ({@code fallen-hull-nouns.md}). */
class HullPlanTest {

    private final HullPlan plan = HullPlan.plan();

    @Test
    void everyHallTheForgeAndEveryWayInJoinOneDeck() {
        Entrance stern = plan.entrances().get(0);
        boolean[] reach = plan.reach(stern.door().u0(), stern.door().v0());
        assertTrue(reach[HullPlan.FORGE_U * HullPlan.BREADTH + HullPlan.FORGE_V], "the autoforge is reached");
        for (Hall hall : plan.halls()) {
            int u = (hall.floor().u0() + hall.floor().u1()) / 2, v = (hall.floor().v0() + hall.floor().v1()) / 2;
            assertTrue(reach[u * HullPlan.BREADTH + v], hall.name() + " is reached");
        }
        for (Entrance e : plan.entrances()) {
            assertTrue(reach[e.door().u0() * HullPlan.BREADTH + e.door().v0()], e.name() + " joins the deck");
            // A way in opens onto the outside: the step past its outer cell leaves the hull.
            int u = e.du() < 0 ? e.door().u0() - 1 : e.du() > 0 ? e.door().u1() + 1 : e.door().u0();
            int v = e.dv() < 0 ? e.door().v0() - 1 : e.dv() > 0 ? e.door().v1() + 1 : e.door().v0();
            assertEquals(HullPlan.Kind.OUTSIDE, plan.at(u, v), e.name() + " opens outside");
        }
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
