package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.RouteCostField;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The swarm's step cost ({@link SwarmStepCost}): a stopped hull's cells are dear, folded over the fire's. */
class SwarmStepCostTest {

    private static final int W = 4, H = 3;

    @Test
    void aStoppedHullsCellsCostTheMostAndTheRestNothing() {
        SwarmStepCost cost = new SwarmStepCost(W, H);
        assertNull(cost.publish(null), "no hull and no fire cost nothing");
        cost.standingHull(1, 2);
        RouteCostField field = cost.publish(null);
        assertEquals(SwarmStepCost.STANDING_HULL, field.costAt(2 * W + 1));
        assertEquals(1f, field.costAt(0));
    }

    @Test
    void theCostingIsKeptWhileTheHullsStandAndRebuiltWhenTheyMove() {
        SwarmStepCost cost = new SwarmStepCost(W, H);
        cost.standingHull(1, 1);
        RouteCostField first = cost.publish(null);
        cost.standingHull(1, 1);
        assertSame(first, cost.publish(null), "the same cells keep the same revision, so no field rebuilds");
        cost.standingHull(2, 1);
        assertNotSame(first, cost.publish(null), "a hull that moved is a new costing");
        assertNull(cost.publish(null), "a hull that drove off costs nothing again");
    }

    @Test
    void aHullInAFireCostsBoth() {
        float[] burning = new float[W * H];
        Arrays.fill(burning, 1f);
        burning[5] = 3f;
        RouteCostField fire = new RouteCostField(burning, RouteCostField.nextRevision());
        SwarmStepCost cost = new SwarmStepCost(W, H);
        cost.standingHull(1, 1);
        RouteCostField both = cost.publish(fire);
        assertEquals(3f * SwarmStepCost.STANDING_HULL, both.costAt(5));
        assertEquals(1f, both.costAt(0));
        cost.standingHull(1, 1);
        assertSame(both, cost.publish(fire), "unchanged fire and hulls keep the costing");
    }
}
