package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How much of an aircraft there is to hit is one fact, and an aircraft is two
 * representations of one thing.
 *
 * <p>It had two values for a while. Rolling, the craft is an air body and
 * {@code AirTargetService} sized it off the hull; parked, it is an ordinary
 * roster unit and the shared radius accessor fell through every special case to
 * the flat half-cell on {@code UnitType.BASED_AIRCRAFT}. The same Valkyrie was
 * four and a half cells of aircraft taxiing and half a cell on its hardstand,
 * and changed size the instant it launched or recovered — the drawn-scale pop
 * one seam over, in the hit geometry.
 *
 * <p>Both are pinned against {@link Airframe#targetRadiusCells}, not merely
 * against each other, so a call site that grew its own formula would have to
 * grow the same one twice to stay green.
 */
class AircraftHitRadiusIsOneNumberTest {

    private static final Airframe HULL = ShuttleType.AEROSHUTTLE;
    private static final float EPS = 1e-4f;

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(64, 64), null);
    }

    @Test
    void aParkedHullAndTheSameHullRollingAreTheSameSizeToShootAt() {
        UnitRosterService roster = roster();
        long parked = roster.spawn(
                BasedAircraft.create("parked", Faction.DEFENDER, HULL, 20, 20, HULL.maxHp()));
        long rolling = TaxiingAircraftIsOneBodyTest.taxiingAircraft(
                roster, Faction.DEFENDER, 30.5f, 30.5f);

        assertEquals(HULL.targetRadiusCells(), roster.radius(parked), EPS,
                "a berthed hull is sized by the airframe standing there");
        assertEquals(HULL.targetRadiusCells(), roster.radius(rolling), EPS,
                "and so is the same hull on its wheels");
        assertEquals(roster.radius(rolling), roster.radius(parked), EPS,
                "a launch or a recovery resizes the aircraft's hit circle");
    }

    @Test
    void aParkedHullIsNoLongerTheArchetypesHalfCell() {
        UnitRosterService roster = roster();
        long parked = roster.spawn(
                BasedAircraft.create("parked", Faction.DEFENDER, HULL, 20, 20, HULL.maxHp()));

        assertTrue(roster.radius(parked) > UnitType.BASED_AIRCRAFT.radius,
                "an aircraft on chocks is several cells of aircraft, which is what "
                        + "a blast's catch radius and a round's contact test have to see");
    }
}
