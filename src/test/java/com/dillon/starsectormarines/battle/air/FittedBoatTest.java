package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.BoatGun;
import com.dillon.starsectormarines.battle.turret.ShieldTint;
import com.dillon.starsectormarines.marine.BoatFittingSlot;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * Two of the company's boats at the same fit are still two boats, and only
     * one of them burned. Without the id in the identity a shoot-down would
     * name whichever of them the deck happened to look at first.
     */
    @Test
    void aBoatIsIdentifiedByWhichBoatItIsAndNotOnlyByHowItIsFitted() {
        FittedBoat first = new FittedBoat(ShuttleType.AEROSHUTTLE,
                BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE, "boat_01");
        FittedBoat second = new FittedBoat(ShuttleType.AEROSHUTTLE,
                BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE, "boat_02");
        FittedBoat sameBoat = new FittedBoat(ShuttleType.AEROSHUTTLE,
                BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE, "boat_01");

        assertEquals("boat_01", first.boatId());
        assertEquals(first, sameBoat);
        assertEquals(first.hashCode(), sameBoat.hashCode());
        assertNotEquals(first, second);
        assertNotEquals(first, FittedBoat.standard(ShuttleType.AEROSHUTTLE),
                "an employer's craft at the same fit is nobody's boat");
    }

    /** Nothing owns a pattern as it leaves the yard, so it names no boat. */
    @Test
    void aStandardFitBelongsToNoBoat() {
        assertNull(FittedBoat.standard(ShuttleType.AEROSHUTTLE).boatId());
        assertNull(new FittedBoat(ShuttleType.HERMES,
                BoatFitting.ARMOURED_PLATING, BoatFitting.STANDARD_DRIVE).boatId());
    }

    /** A fitting names its own slot, so a drive in the plating slot is a mistake. */
    @Test
    void aFittingCannotBeInstalledInTheOtherSlot() {
        assertThrows(IllegalArgumentException.class, () -> new FittedBoat(
                ShuttleType.AEROSHUTTLE,
                BoatFitting.TUNED_DRIVE, BoatFitting.STANDARD_DRIVE));
    }

    @Test
    void theCabinSetsTheSeatsAndTheDriveQuickensTheRamp() {
        FittedBoat refitted = new FittedBoat(ShuttleType.HERMES, Map.of(
                BoatFittingSlot.CABIN, BoatFitting.TWO_TEAM_CABIN,
                BoatFittingSlot.DRIVE, BoatFitting.UPRATED_DRIVE), null, null);

        assertEquals(ShuttleType.HERMES.capacity, FittedBoat.standard(ShuttleType.HERMES).seats());
        assertEquals(8, refitted.seats());
        assertEquals(ShuttleType.HERMES.deboardInterval * 0.6f, refitted.deboardInterval(), EPSILON);
    }

    @Test
    void weightAboardSlowsHerAndABetterDriveWinsItBack() {
        FittedBoat standard = FittedBoat.standard(ShuttleType.HERMES);
        FittedBoat uprated = new FittedBoat(ShuttleType.HERMES,
                Map.of(BoatFittingSlot.DRIVE, BoatFitting.UPRATED_DRIVE), null, null);

        assertSame(standard, standard.carrying(null, 0), "empty, she flies as built");
        float loaded = standard.carrying(LiftClass.HEAVY, 0).maxSpeed();
        assertEquals(standard.maxSpeed() * 0.7f, loaded, EPSILON);
        assertTrue(standard.carrying(LiftClass.LIGHT, 0).maxSpeed() > loaded, "a lighter machine drags less");
        assertTrue(standard.carrying(null, 8).maxSpeed() < standard.maxSpeed(), "a full cabin drags a little");
        assertTrue(uprated.carrying(LiftClass.HEAVY, 0).maxSpeed() >= standard.maxSpeed());
    }

    @Test
    void theLoadAboardIsWhatIsLeftOfTheSortie() {
        ShuttleMission mission = new ShuttleMission(0f, 0f, 0f, 0f, 0f, 0f, 0f, 1);
        assertEquals(1, mission.marinesAboard());
        assertNull(mission.machineAboard());

        mission.payload = MechSupportPayload.INSTANCE;
        mission.mechVariant = MechVariant.BULWARK;
        assertEquals(LiftClass.HEAVY, mission.machineAboard());
        assertEquals(0, mission.marinesAboard());

        mission.marinesRemaining = 0;
        assertNull(mission.machineAboard(), "dropped, she runs back light");
    }

    @Test
    void herShieldIsHerEmitterAndHerPoolIsHerPower() {
        FittedBoat standard = FittedBoat.standard(ShuttleType.HERMES);
        FittedBoat refitted = new FittedBoat(ShuttleType.HERMES, Map.of(
                BoatFittingSlot.EMITTER, BoatFitting.ASSAULT_EMITTER,
                BoatFittingSlot.POWER, BoatFitting.MILITARY_CAPACITORS), null, null);

        assertEquals(FittedBoat.BASE_SHIELD_ARC, standard.shieldSpec().arcDegrees(), EPSILON);
        assertEquals(240f, refitted.shieldSpec().arcDegrees(), EPSILON);
        assertEquals(0.6f, refitted.shieldSpec().fluxPerDamage(), EPSILON);
        assertEquals(FittedBoat.BASE_FLUX * 1.75f, refitted.shieldSpec().fluxCapacity(), EPSILON);
        assertEquals(FittedBoat.BASE_VENT * 1.5f, refitted.shieldSpec().dissipationPerSecond(), EPSILON);
        assertTrue(refitted.shieldSpec().upkeepPerSecond() < refitted.shieldSpec().dissipationPerSecond());
        assertEquals(ShieldTint.ORANGE, standard.shieldTint());
        assertEquals(ShieldTint.BLUE, refitted.shieldTint());
    }

    @Test
    void everyRoundButAMissilesChargesThePool() {
        assertTrue(FittedBoat.fluxPerRound(BoatGun.BURST_LASER.structureId())
                > FittedBoat.fluxPerRound(BoatGun.HEAVY_MG.structureId()));
        assertTrue(FittedBoat.fluxPerRound(BoatGun.HEAVY_MG.structureId()) > 0f);
        assertEquals(0f, FittedBoat.fluxPerRound(BoatGun.SRM_POD.structureId()));
    }
}
