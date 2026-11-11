package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The hull's deck split into the compartments a boarder opens one at a time ({@code fallen-hull-nouns.md}, The ground). */
class HullCompartmentsTest {

    @Test
    void aBuildingOnTheDeckSplitsByCompartmentAndTheFirstKeepsItsId() {
        // A 6x1 strip of deck: cells 0-2 the spine, 3-5 a hold; the flood fill found it as one building.
        CellTopology topology = new CellTopology(6, 1);
        int[] compartments = {0, 0, 0, 1, 1, 1};
        Building strip = new Building(7, BuildingKind.FORTIFIED, 0, 5, 0, 0, new int[]{0, 1, 2, 3, 4, 5},
                new int[6], 1f, 1f, 1f);
        Building town = new Building(9, BuildingKind.RESIDENTIAL, 0, 0, 0, 0, new int[]{0}, new int[]{0}, 1f, 1f, 1f);
        Buildings buildings = new Buildings();
        buildings.add(strip);
        Buildings split = CrashSiteFinishStage.splitCompartments(buildings, topology, compartments, 2);

        assertEquals(2, split.size());
        Building spine = split.get(7), hold = split.get(8);
        assertEquals(3, spine.cellCount());
        assertEquals(2, spine.maxX);
        assertEquals(3, hold.minX);
        assertEquals(8, topology.getBuildingId(4, 0), "the hold's cells carry its id");

        Buildings ashore = new Buildings();
        ashore.add(town);
        int[] none = new int[6];
        Arrays.fill(none, -1);
        assertSame(ashore, CrashSiteFinishStage.splitCompartments(ashore, topology, none, 2), "no deck, nothing split");
    }

    @Test
    void everyDeckCellIsInOneCompartmentAndBreachesAreOpenGround() {
        HullPlan plan = HullPlan.plan();
        for (int u = 0; u < HullPlan.LENGTH; u++) {
            for (int v = 0; v < HullPlan.BREADTH; v++) {
                HullPlan.Kind kind = plan.at(u, v);
                if (kind == HullPlan.Kind.FLOOR) assertEquals(true, plan.compartment(u, v) != null, u + "," + v);
                else assertEquals(null, plan.compartment(u, v), u + "," + v);
            }
        }
        for (HullPlan.Hall hall : plan.halls()) {
            assertEquals(hall.name(), plan.compartment(hall.floor().u0(), hall.floor().v0()));
            assertEquals(hall.name(), plan.compartment(hall.door().u0(), hall.door().v0()), "a dock is its hall's");
        }
        assertEquals("spine.stern", plan.compartment(0, 15), "the stern's door opens the stern's spine");
        assertEquals("spine.bow", plan.compartment(50, 15), "the spine is broken with the hull");
        assertEquals("bay", plan.compartment(35, 0), "the bay door opens the bay");
    }

    @Test
    void theShellIsTheSolidHullNearTheOutsideOrABreach() {
        HullPlan plan = HullPlan.plan();
        assertEquals(true, plan.shell(0, 0), "the stern's corner");
        assertEquals(false, plan.shell(17, 18), "the bulkhead between the spine and the first hold");
        // Beside a broken end, deep in the hull, the shell is torn open too.
        assertEquals(true, plan.shell(43, 20));
    }
}
