package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An aircraft rolling across an apron is a body, and every liveness gate in the
 * battle has to say so.
 *
 * <p>Three sites did not. Each of them predated air and each spelled its
 * question out as "a live roster unit, or a targetable convoy vehicle" — a
 * sentence that is a list of the carriers that existed when it was written, and
 * that silently drops the one added afterwards. The consequences were a held
 * burst reference to an aircraft resolving to nothing, a squad forgetting a
 * taxiing craft on the tick it saw it, and a mech unable to shoot at something
 * the riflemen beside it were already shooting at.
 */
public class TaxiingAircraftIsOneBodyTest {

    static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(64, 64), null);
    }

    /**
     * An aircraft on its wheels: the trio a carried body holds, a kinematic
     * body, and a mission phase that says it is out in the open under its own
     * power. Deliberately minted here rather than through {@code AirSystem},
     * which needs a navigation service and a whole field to build one.
     */
    public static long taxiingAircraft(UnitRosterService roster, Faction faction,
                                float x, float y) {
        BattleComponents c = roster.components();
        long id = roster.allocateAir(new ComponentType[]{
                c.AIR_IDENTITY, c.KINEMATICS, c.SHUTTLE_MISSION,
                c.APPEARANCE, c.IDENTITY, c.HEALTH, c.ARMOR});
        AirBody body = new AirBody();
        body.teleport(x, y, 0f);
        ShuttleMission mission = new ShuttleMission(x, y, x, y, x, y, 0f, 0);
        mission.state = ShuttleState.TAXI_OUT;
        roster.world().setAirIdentity(id, ShuttleType.AEROSHUTTLE, faction);
        roster.world().setKinematics(id, body);
        roster.world().setMission(id, mission);
        roster.entityWorld().setObject(id, c.IDENTITY,
                BattleComponents.IDENTITY_TYPE, UnitType.BASED_AIRCRAFT);
        roster.entityWorld().setObject(id, c.IDENTITY,
                BattleComponents.IDENTITY_FACTION, faction);
        roster.entityWorld().setObject(id, c.IDENTITY,
                BattleComponents.IDENTITY_NAME, "taxiing-aircraft");
        roster.world().setMaxHp(id, 200f);
        roster.world().setHp(id, 200f);
        roster.entityWorld().setFloat(id, c.HEALTH,
                BattleComponents.HEALTH_DAMAGE_TAKEN_MULT, 1f);
        roster.world().setMaxArmor(id, 100f);
        roster.world().setArmor(id, 100f);
        roster.world().setArmorRating(id, 6f);
        roster.bodies().admit(id);
        return id;
    }

    @Test
    public void aHeldReferenceToATaxiingAircraftResolves() {
        UnitRosterService roster = roster();
        long craft = taxiingAircraft(roster, Faction.DEFENDER, 20.5f, 20.5f);

        assertTrue(roster.bodies().isTargetable(craft),
                "the gate behind resolveUnit and the squad belief predicate alike");
    }

    @Test
    public void aCraftThatLiftsStopsResolvingTheTickItDoes() {
        UnitRosterService roster = roster();
        long craft = taxiingAircraft(roster, Faction.DEFENDER, 20.5f, 20.5f);
        assertTrue(roster.bodies().isTargetable(craft));

        roster.world().mission(craft).state = ShuttleState.INCOMING;
        assertFalse(roster.bodies().isTargetable(craft),
                "a shooter holding a lock on a craft that lifts drops it, "
                        + "rather than spending rounds on something out of reach");
    }

    @Test
    public void theCraftIsInTheIndexOnTheOneAdmissionPath() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        long craft = taxiingAircraft(roster, Faction.DEFENDER, 20.5f, 20.5f);
        index.rebuild(roster);

        LongBucket out = new LongBucket();
        index.gather(20.5f, 20.5f, 4f, out);
        boolean found = false;
        for (int i = 0; i < out.size; i++) found |= out.ids[i] == craft;
        assertTrue(found, "the body snapshot and the index rebuild are one pass");
    }

    @Test
    public void aCraftsBodyCircleComesFromTheHullRatherThanOneAuthoredNumber() {
        UnitRosterService roster = roster();
        long craft = taxiingAircraft(roster, Faction.DEFENDER, 20.5f, 20.5f);

        assertEquals(roster.airTargets().targetRadius(craft),
                roster.radius(craft), 1e-4f,
                "the shared radius accessor asks the carrier");
        assertTrue(roster.radius(craft) > UnitType.BASED_AIRCRAFT.radius,
                "a transport is several cells of aircraft, not half of one — "
                        + "which is what a blast's catch radius against it was");
    }
}
