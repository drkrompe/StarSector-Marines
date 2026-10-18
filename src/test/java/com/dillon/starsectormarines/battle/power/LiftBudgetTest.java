package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.BoatFitting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.dillon.starsectormarines.battle.air.LiftClass.HEAVY;
import static com.dillon.starsectormarines.battle.air.LiftClass.LIGHT;
import static com.dillon.starsectormarines.battle.air.LiftClass.MEDIUM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mission's lift: which carrier takes which machine, and why one is refused. */
class LiftBudgetTest {

    private static final FittedBoat LIGHT_CRADLE = new FittedBoat(ShuttleType.BUFFALO,
            BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE,
            BoatFitting.LIFT_CRADLE, "buffalo-1");

    @Test
    void aLightCradleRefusesAHeavyMachineForWantOfLift() {
        LiftBudget budget = LiftBudget.forMission(LIGHT_CRADLE, false, ShuttleType.VALKYRIE, 3);

        assertNull(budget.allocate(List.of(HEAVY), true));
        assertSame(LiftBudget.Refusal.NO_LIFT, budget.refusal(HEAVY, List.of(), true));
        assertNull(budget.refusal(LIGHT, List.of(), true));
    }

    @Test
    void spentLiftStaysSpentForTheMission() {
        LiftBudget budget = LiftBudget.forMission(LIGHT_CRADLE, false, ShuttleType.VALKYRIE, 3);
        budget.spend(budget.allocate(List.of(LIGHT, LIGHT), true));

        assertEquals(0, budget.remaining());
        assertSame(LiftBudget.Refusal.LIFT_SPENT, budget.refusal(LIGHT, List.of(), true));
    }

    @Test
    void aBoatInTheAirIsNotLiftForThisCall() {
        LiftBudget budget = LiftBudget.forMission(LIGHT_CRADLE, false, ShuttleType.VALKYRIE, 3);

        assertSame(LiftBudget.Refusal.CARRIER_AWAY, budget.refusal(LIGHT, List.of(), false));
    }

    @Test
    void heavyLiftIsKeptForTheHeavyMachine() {
        LiftBudget budget = LiftBudget.forMission(LIGHT_CRADLE, true, ShuttleType.VALKYRIE, 3);

        List<LiftBudget.Carrier> allocation = budget.allocate(List.of(LIGHT, HEAVY, MEDIUM), true);

        assertTrue(allocation.get(0).company(), "the Hound rides the cradle");
        assertSame(ShuttleType.VALKYRIE, allocation.get(1).frame());
        assertSame(ShuttleType.VALKYRIE, allocation.get(2).frame(),
                "a Light cradle cannot take a Medium machine");
    }

    @Test
    void aHeavyCradleLiftsOneMoreMachineThanALightOne() {
        FittedBoat heavyCradle = new FittedBoat(ShuttleType.BUFFALO, BoatFitting.STANDARD_PLATING,
                BoatFitting.STANDARD_DRIVE, BoatFitting.HEAVY_CRADLE, "buffalo-2");

        assertEquals(2, LiftBudget.forMission(LIGHT_CRADLE, false, ShuttleType.VALKYRIE, 4).remaining());
        assertEquals(3, LiftBudget.forMission(heavyCradle, false, ShuttleType.VALKYRIE, 4).remaining());
    }
}
