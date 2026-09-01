package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.marine.BoatFitting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** What a yard's work does to a boat, and what it deliberately leaves alone. */
class FittedBoatTest {

    private static final float EPSILON = 1e-4f;

    @Test
    void aBoatAtStandardFitIsExactlyItsPatternsNumbers() {
        FittedBoat boat = FittedBoat.standard(ShuttleType.AEROSHUTTLE);

        assertEquals(ShuttleType.AEROSHUTTLE.maxHp(), boat.maxHp(), EPSILON);
        assertEquals(ShuttleType.AEROSHUTTLE.maxSpeed(), boat.maxSpeed(), EPSILON);
        assertEquals(ShuttleType.AEROSHUTTLE.accel(), boat.accel(), EPSILON);
        assertEquals(ShuttleType.AEROSHUTTLE.brakingAccel(), boat.brakingAccel(), EPSILON);
    }

    @Test
    void platingScalesTheHullAndTheDriveScalesSpeedAndBothAccelerations() {
        FittedBoat boat = new FittedBoat(ShuttleType.AEROSHUTTLE,
                BoatFitting.REINFORCED_PLATING, BoatFitting.TUNED_DRIVE);

        assertEquals(ShuttleType.AEROSHUTTLE.maxHp() * 1.35f, boat.maxHp(), EPSILON);
        assertEquals(ShuttleType.AEROSHUTTLE.maxSpeed() * 1.20f, boat.maxSpeed(), EPSILON);
        assertEquals(ShuttleType.AEROSHUTTLE.accel() * 1.20f, boat.accel(), EPSILON);
        assertEquals(ShuttleType.AEROSHUTTLE.brakingAccel() * 1.20f,
                boat.brakingAccel(), EPSILON);
    }

    /**
     * A better boat is faster and tougher, not a different aircraft. Everything
     * that says what kind of thing it is belongs to the pattern.
     */
    @Test
    void everythingThatIsNotHullOrDriveIsTheUntouchedPattern() {
        ShuttleType pattern = ShuttleType.AEROSHUTTLE;
        FittedBoat boat = new FittedBoat(pattern,
                BoatFitting.ARMOURED_PLATING, BoatFitting.UPRATED_DRIVE);

        assertEquals(pattern.spritePath(), boat.spritePath());
        assertEquals(pattern.renderHullId(), boat.renderHullId());
        assertEquals(pattern.hardpoints(), boat.hardpoints());
        assertEquals(pattern.ordnance(), boat.ordnance());
        assertEquals(pattern.maxTurnRateDegPerSec(), boat.maxTurnRateDegPerSec(), EPSILON);
        assertEquals(pattern.lateralDriftDamping(), boat.lateralDriftDamping(), EPSILON);
        assertEquals(pattern.stationDamping(), boat.stationDamping(), EPSILON);
        assertEquals(pattern.targetRadiusCells(), boat.targetRadiusCells(), EPSILON);
        assertSame(boat, boat.flight(), "the fit is what the steering tick has to read");
    }

    /**
     * A manifest round-tripped through a fixture rebuilds its boats, so two
     * boats with the same pattern and the same fittings have to compare equal
     * or the replay is a different one.
     */
    @Test
    void twoBoatsWithTheSameFitCompareEqual() {
        FittedBoat one = new FittedBoat(ShuttleType.AEROSHUTTLE,
                BoatFitting.REINFORCED_PLATING, BoatFitting.STANDARD_DRIVE);
        FittedBoat same = new FittedBoat(ShuttleType.AEROSHUTTLE,
                BoatFitting.REINFORCED_PLATING, BoatFitting.STANDARD_DRIVE);
        FittedBoat otherFit = new FittedBoat(ShuttleType.AEROSHUTTLE,
                BoatFitting.ARMOURED_PLATING, BoatFitting.STANDARD_DRIVE);
        FittedBoat otherPattern = new FittedBoat(ShuttleType.HERMES,
                BoatFitting.REINFORCED_PLATING, BoatFitting.STANDARD_DRIVE);

        assertEquals(one, same);
        assertEquals(one.hashCode(), same.hashCode());
        assertNotEquals(one, otherFit);
        assertNotEquals(one, otherPattern);
    }

    /** A fitting names its own slot, so a drive in the plating slot is a mistake. */
    @Test
    void aFittingCannotBeInstalledInTheOtherSlot() {
        assertThrows(IllegalArgumentException.class, () -> new FittedBoat(
                ShuttleType.AEROSHUTTLE,
                BoatFitting.TUNED_DRIVE, BoatFitting.STANDARD_DRIVE));
    }
}
