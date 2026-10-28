package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftRating;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.BoatFitting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The craft a paired descent flies for a committed hull: a nimble boat herself, else an Aeroshuttle keeping a cradle's lift. */
class ShuttleArrivalPlanFrameTest {

    @Test
    void aStandInForACradledBoatInheritsItsLiftRating() {
        FittedBoat cradled = new FittedBoat(ShuttleType.BUFFALO, BoatFitting.STANDARD_PLATING,
                BoatFitting.STANDARD_DRIVE, BoatFitting.LIFT_CRADLE, "buffalo-1");
        List<ShuttleAssignment> source = List.of(
                new ShuttleAssignment(cradled, 1, 6),
                new ShuttleAssignment(FittedBoat.standard(ShuttleType.BUFFALO), 1, 6));

        Airframe standIn = ShuttleArrivalPlan.frameFor(source, 0, 2, 0);

        assertEquals(LiftRating.LIGHT, standIn.liftRating());
        assertSame(ShuttleType.AEROSHUTTLE, ShuttleType.fromAirframe(standIn));
        assertEquals(LiftRating.NONE, ShuttleArrivalPlan.frameFor(source, 0, 2, 1).liftRating());
        assertSame(ShuttleType.AEROSHUTTLE, ShuttleArrivalPlan.frameFor(source, 0, 2, 5), "past the source");
    }

    @Test
    void aHermesMakesTheDescentHerselfAndRepeatsAsAnAnonymousCopy() {
        FittedBoat first = new FittedBoat(ShuttleType.HERMES, BoatFitting.ARMOURED_PLATING,
                BoatFitting.UPRATED_DRIVE, "hermes-1");
        FittedBoat second = FittedBoat.standard(ShuttleType.HERMES);
        List<ShuttleAssignment> source = List.of(
                new ShuttleAssignment(first, 1, 6), new ShuttleAssignment(second, 1, 6));

        assertSame(first, ShuttleArrivalPlan.frameFor(source, 0, 2, 0));
        assertSame(second, ShuttleArrivalPlan.frameFor(source, 0, 2, 1));

        FittedBoat repeat = (FittedBoat) ShuttleArrivalPlan.frameFor(source, 0, 2, 2);
        assertEquals(ShuttleType.HERMES, repeat.pattern());
        assertEquals(BoatFitting.ARMOURED_PLATING, repeat.plating());
        assertEquals(BoatFitting.UPRATED_DRIVE, repeat.drive());
        assertNull(repeat.boatId());
    }

    @Test
    void aHeavierHullIsNeverRepeatedAndFliesAsAnAeroshuttle() {
        List<ShuttleAssignment> source = List.of(
                new ShuttleAssignment(FittedBoat.standard(ShuttleType.BUFFALO), 1, 6));

        assertSame(ShuttleType.AEROSHUTTLE, ShuttleArrivalPlan.frameFor(source, 0, 1, 0));
        assertSame(ShuttleType.AEROSHUTTLE, ShuttleArrivalPlan.frameFor(source, 0, 1, 1));
    }
}
