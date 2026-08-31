package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The index is over <em>bodies</em>, not over the dense infantry roster. A
 * convoy chassis moves under its own kinematics and carries no {@code POSITION}
 * — it is still something a marine standing nearby can see, so a proximity
 * query has to find it. This is the contract every enemy scan inherits; before
 * it existed each scan carried its own convoy sweep and most of them forgot.
 */
public class UnitSpatialIndexBodyMembershipTest {

    private static long parkedVehicle(UnitRosterService roster, Faction faction,
                                      VehicleState state, float x, float y) {
        VehicleMission mission = new VehicleMission(
                new float[]{x, x}, new float[]{y, y},
                new float[]{x, x}, new float[]{y, y},
                0f, VehicleType.HEAVY_APC.capacity);
        mission.state = state;
        return roster.convoy().spawn(VehicleType.HEAVY_APC, faction, mission);
    }

    private static boolean contains(LongBucket out, long id) {
        for (int i = 0; i < out.size; i++) if (out.ids[i] == id) return true;
        return false;
    }

    @Test
    public void aDrivingChassisIsGatheredLikeAnyOtherBody() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        long marine = roster.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 10, 10));
        long apc = parkedVehicle(roster, Faction.DEFENDER, VehicleState.INCOMING, 14.5f, 10.5f);

        LongBucket out = new LongBucket();
        index.gather(10.5f, 10.5f, 8f, out);
        assertTrue(contains(out, marine), "the marine is in its own neighbourhood");
        assertTrue(contains(out, apc), "so is the APC driving past it");

        // And through the hostile-combatant projection the scans actually use,
        // which reads the faction and combatant flags the index stored.
        LongBucket hostiles = new LongBucket();
        index.gatherOtherFactionCombatants(10.5f, 10.5f, 8f, Faction.MARINE, hostiles);
        assertTrue(contains(hostiles, apc), "a hostile chassis is a hostile combatant");
    }

    @Test
    public void aRebuildKeepsTheChassisAndTracksItsBody() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        roster.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 10, 10));
        long apc = parkedVehicle(roster, Faction.DEFENDER, VehicleState.INCOMING, 14.5f, 10.5f);

        // Drive it out of the query and rebuild: the index reads the kinematic
        // body, so the snapshot follows the hull rather than its spawn cell.
        roster.convoy().body(apc).teleport(40.5f, 40.5f, 0f);
        index.rebuild(roster);

        LongBucket near = new LongBucket();
        index.gather(10.5f, 10.5f, 8f, near);
        assertFalse(contains(near, apc), "the chassis is no longer where it started");

        LongBucket far = new LongBucket();
        index.gather(40.5f, 40.5f, 4f, far);
        assertTrue(contains(far, apc), "it is where its body now is");
    }

    @Test
    public void offMapAndWreckedChassisAreNotBodiesToFind() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        long pending = parkedVehicle(roster, Faction.DEFENDER, VehicleState.PENDING, 14.5f, 10.5f);
        long wreck = parkedVehicle(roster, Faction.DEFENDER, VehicleState.WRECKED, 15.5f, 10.5f);
        index.rebuild(roster);

        LongBucket out = new LongBucket();
        index.gather(14.5f, 10.5f, 8f, out);
        assertFalse(contains(out, pending), "a chassis still off-map is not on the map");
        assertFalse(contains(out, wreck), "a wreck is scenery, not a body");
    }

    @Test
    public void theBodyRadiusBoundCoversTheLargestBodyHeld() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        long marine = roster.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 10, 10));
        assertEquals(roster.radius(marine), index.maxBodyRadiusForTest(), 1e-4f,
                "with only riflemen on the map that is as big as a body gets");

        long apc = parkedVehicle(roster, Faction.DEFENDER, VehicleState.INCOMING, 14.5f, 10.5f);
        assertEquals(roster.radius(apc), index.maxBodyRadiusForTest(), 1e-4f,
                "a chassis is much larger and the bound has to follow it");
    }

    @Test
    public void anOverlapQueryReachesAHullTheCentreQueryMisses() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        long apc = parkedVehicle(roster, Faction.DEFENDER, VehicleState.INCOMING, 20.5f, 20.5f);
        float hull = roster.radius(apc);

        // A blast whose edge stops half a hull short of the chassis CENTRE
        // still reaches the chassis, because the hull sticks out that far.
        float blast = hull * 0.5f;
        float distance = hull * 0.9f;

        LongBucket centres = new LongBucket();
        index.gather(20.5f + distance, 20.5f, blast, centres);
        assertFalse(contains(centres, apc), "its centre is outside the blast");

        LongBucket overlaps = new LongBucket();
        index.gatherOverlapping(20.5f + distance, 20.5f, blast, overlaps);
        assertTrue(contains(overlaps, apc), "its hull is not — that is the whole difference");
    }

    @Test
    public void threatRangeAnswersForEveryBodyKind() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        long marine = roster.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 10, 10));
        long apc = parkedVehicle(roster, Faction.DEFENDER, VehicleState.INCOMING, 14.5f, 10.5f);

        assertTrue(roster.threatRange(marine) > 0f, "a rifleman reaches as far as its weapon");
        // The APC is armed, so a cell it can reach is a cell to take cover from.
        assertTrue(roster.threatRange(apc) > 0f,
                "an armed chassis has reach even though it carries no COMBAT");
    }
}
