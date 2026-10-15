package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.world.gen.fortress.AssaultApproach.Variant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The assault data a stamp exports: its ways in, which nests cover each, and the breach. Pure geometry in the fort's
 * local frame, asked of the plan directly.
 */
class FortApproachesTest {

    private static AssaultApproach find(List<AssaultApproach> all, Variant variant) {
        return all.stream().filter(a -> a.variant() == variant).findFirst().orElse(null);
    }

    @Test
    void theBatteryFortExportsItsMainGateItsPosternAndABreach() {
        List<AssaultApproach> all = FortPlan.of(FortKind.BATTERY_FORT).approaches();
        assertEquals(3, all.size());
        assertEquals(Variant.MAIN_GATE, all.get(0).variant());
        assertEquals(0, all.get(0).gateIndex());
        assertEquals(Variant.POSTERN, all.get(1).variant());
        assertEquals(1, all.get(1).gateIndex());
        assertEquals(Variant.BREACH, all.get(2).variant());
        assertEquals(-1, all.get(2).gateIndex());
    }

    @Test
    void aGateApproachStartsAtTheGatesMouthAndEntersFourCellsThroughIt() {
        FortPlan plan = FortPlan.of(FortKind.BATTERY_FORT);
        AssaultApproach main = find(plan.approaches(), Variant.MAIN_GATE);
        FortPlan.Gate gate = plan.gates().get(0);
        assertEquals(gate.mouthX(), main.mouthX());
        assertEquals(gate.mouthY(), main.mouthY());
        assertEquals(main.mouthX() - main.outX() * FortApproaches.GATE_ENTRY_DEPTH, main.entryX());
        assertEquals(main.mouthY() - main.outY() * FortApproaches.GATE_ENTRY_DEPTH, main.entryY());
        assertEquals(main.mouthX(), main.lane().get(0)[0]);
        assertTrue(main.lane().size() > 10, "the lane runs out to the footprint's edge, across the belt");
    }

    @Test
    void theNestsThatCoverAGateAreTheOnesWithinReachOfItsMouth() {
        FortPlan plan = FortPlan.of(FortKind.BATTERY_FORT);
        AssaultApproach main = find(plan.approaches(), Variant.MAIN_GATE);
        // The main gate is on the west face: the two western bastion nests have it, the two eastern do not.
        assertTrue(main.covering().containsAll(List.of(0, 2)) && !main.covering().contains(1) && !main.covering().contains(3));
        assertTrue(main.covering().stream().anyMatch(i -> i >= BatteryFort.NESTS.size()), "the belt's guns cover it too");
        AssaultApproach postern = find(plan.approaches(), Variant.POSTERN);
        assertTrue(postern.covering().containsAll(List.of(0, 1)) && !postern.covering().contains(2), "the north postern has the two northern nests");
    }

    @Test
    void theBreachIsAWallSectionAwayFromEveryGateWithOpenGroundOutsideAndYardInside() {
        FortPlan plan = FortPlan.of(FortKind.BATTERY_FORT);
        AssaultApproach breach = find(plan.approaches(), Variant.BREACH);
        assertNotNull(breach);
        assertFalse(plan.isWorks(breach.mouthX(), breach.mouthY()), "outside the works");
        assertTrue(plan.isWall(breach.mouthX() - breach.outX(), breach.mouthY() - breach.outY()), "the wall is the cell behind the mouth");
        assertTrue(plan.isWorks(breach.entryX(), breach.entryY()), "the entry is inside the works");
        assertFalse(plan.isWall(breach.entryX(), breach.entryY()), "in the yard, not in the wall");
        for (FortPlan.Gate gate : plan.gates()) {
            assertTrue(Math.hypot(gate.mouthX() - breach.mouthX(), gate.mouthY() - breach.mouthY())
                    >= FortApproaches.BREACH_GATE_CLEARANCE, "a breach is not a gate with extra steps");
        }
    }

    @Test
    void theShieldFortExportsAnApproachForEachOfItsThreeGatesAndABreach() {
        FortPlan plan = FortPlan.of(FortKind.SHIELD_FORT);
        List<AssaultApproach> all = plan.approaches();
        assertEquals(plan.gates().size() + 1, all.size());
        assertEquals(Variant.MAIN_GATE, all.get(0).variant());
        for (int i = 1; i < plan.gates().size(); i++) assertEquals(Variant.POSTERN, all.get(i).variant());
        assertNotNull(find(all, Variant.BREACH));
        for (AssaultApproach a : all) {
            assertFalse(a.covering().isEmpty() && a.variant() != Variant.BREACH, "every gate of the round-tower fort is under a nest");
        }
    }

    @Test
    void theDerivationIsTheSameEveryTime() {
        FortPlan plan = FortPlan.of(FortKind.BATTERY_FORT);
        AssaultApproach a = find(plan.approaches(), Variant.BREACH);
        AssaultApproach b = find(plan.approaches(), Variant.BREACH);
        assertEquals(a.mouthX(), b.mouthX());
        assertEquals(a.mouthY(), b.mouthY());
        assertEquals(a.outX(), b.outX());
        assertEquals(a.outY(), b.outY());
    }
}
