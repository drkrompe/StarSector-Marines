package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.BoatFitting;
import org.junit.jupiter.api.Test;

import static com.dillon.starsectormarines.battle.air.LiftClass.HEAVY;
import static com.dillon.starsectormarines.battle.air.LiftClass.LIGHT;
import static com.dillon.starsectormarines.battle.air.LiftClass.MEDIUM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mission's lift: which carrier's lift a machine spends, and why one is refused. */
class LiftBudgetTest {

    private static final FittedBoat LIGHT_CRADLE = new FittedBoat(ShuttleType.BUFFALO,
            BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE,
            BoatFitting.LIFT_CRADLE, "buffalo-1");

    @Test
    void aLightCradleRefusesAHeavyMachineForWantOfLift() {
        LiftBudget budget = LiftBudget.forMission(LIGHT_CRADLE, false, ShuttleType.VALKYRIE, 3);

        assertNull(budget.allocate(HEAVY));
        assertSame(LiftBudget.Refusal.NO_LIFT, budget.refusal(HEAVY));
        assertNull(budget.refusal(LIGHT));
    }

    @Test
    void spentLiftStaysSpentAndOnlyARefundGivesItBack() {
        LiftBudget budget = LiftBudget.forMission(LIGHT_CRADLE, false, ShuttleType.VALKYRIE, 4);
        LiftBudget.Carrier first = budget.allocate(LIGHT);
        budget.spend(first);
        for (int i = 1; i < 4; i++) budget.spend(budget.allocate(LIGHT));

        assertEquals(0, budget.remaining());
        assertSame(LiftBudget.Refusal.LIFT_SPENT, budget.refusal(LIGHT));

        budget.refund(first);
        assertEquals(1, budget.remaining());
        assertNull(budget.refusal(LIGHT));
    }

    @Test
    void heavyLiftIsKeptForTheHeavyMachine() {
        LiftBudget budget = LiftBudget.forMission(LIGHT_CRADLE, true, ShuttleType.VALKYRIE, 3);

        assertTrue(budget.allocate(LIGHT).company(), "the Hound spends the cradle's lift");
        assertSame(ShuttleType.VALKYRIE, budget.allocate(HEAVY).frame());
        assertSame(ShuttleType.VALKYRIE, budget.allocate(MEDIUM).frame(),
                "a Light cradle cannot take a Medium machine");
    }

    @Test
    void aCradledBoatLiftsTheWholeBayWhateverItsCradle() {
        FittedBoat heavyCradle = new FittedBoat(ShuttleType.BUFFALO, BoatFitting.STANDARD_PLATING,
                BoatFitting.STANDARD_DRIVE, BoatFitting.HEAVY_CRADLE, "buffalo-2");

        assertEquals(4, LiftBudget.forMission(LIGHT_CRADLE, false, ShuttleType.VALKYRIE, 4).remaining());
        assertEquals(4, LiftBudget.forMission(heavyCradle, false, ShuttleType.VALKYRIE, 4).remaining());
    }
}
