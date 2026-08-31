package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.TaxiingAircraftIsOneBodyTest;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Can this be shot" only ever had a relational answer.
 *
 * <p>Asked absolutely it had to be answered for the most limited shooter on the
 * map, so the one case that differed — a defence post reaching something in the
 * air — lived somewhere else entirely, as a hardcoded filter inside the anti-air
 * drain. These are the three answers that filter and the absolute predicate gave
 * between them, now given by one relation. They are deliberately the same three:
 * this changed the structure and not the balance.
 */
public class EngagementRelationTest {

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(64, 64), null);
    }

    private static long marine(UnitRosterService roster) {
        return roster.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, 5, 5));
    }

    private static long mech(UnitRosterService roster) {
        return roster.spawn(new EntitySpec("mech", Faction.MARINE, UnitType.HEAVY_MECH, 6, 5));
    }

    private static long defencePost(UnitRosterService roster) {
        return roster.spawn(new EntitySpec("post", Faction.MARINE, UnitType.TURRET, 7, 5));
    }

    @Test
    public void everybodyCanEngageACraftOnItsWheels() {
        UnitRosterService roster = roster();
        EngagementService engagement = new EngagementService(roster);
        long craft = TaxiingAircraftIsOneBodyTest.taxiingAircraft(
                roster, Faction.DEFENDER, 20.5f, 20.5f);

        assertTrue(engagement.canEngage(marine(roster), craft));
        assertTrue(engagement.canEngage(mech(roster), craft));
        assertTrue(engagement.canEngage(defencePost(roster), craft));
    }

    @Test
    public void onlySomethingThatReachesUpCanEngageACraftInTheAir() {
        UnitRosterService roster = roster();
        EngagementService engagement = new EngagementService(roster);
        long craft = TaxiingAircraftIsOneBodyTest.taxiingAircraft(
                roster, Faction.DEFENDER, 20.5f, 20.5f);
        roster.world().mission(craft).state = ShuttleState.ATTACK_RUN;

        assertFalse(engagement.canEngage(marine(roster), craft),
                "a rifle section cannot engage something overhead");
        assertFalse(engagement.canEngage(mech(roster), craft),
                "and neither can a mech");
        assertTrue(engagement.canEngage(defencePost(roster), craft),
                "a defence post is the one thing that reaches up, and it is the "
                        + "shooter's capability that says so");
    }

    @Test
    public void aMechAndAMarineGiveTheSameAnswerForTheSameCandidate() {
        UnitRosterService roster = roster();
        EngagementService engagement = new EngagementService(roster);
        long craft = TaxiingAircraftIsOneBodyTest.taxiingAircraft(
                roster, Faction.DEFENDER, 20.5f, 20.5f);
        long marine = marine(roster);
        long mech = mech(roster);

        assertEquals(engagement.canEngage(marine, craft),
                engagement.canEngage(mech, craft),
                "the same question about the same body, asked by two shooters "
                        + "whose reach is identical");

        roster.world().mission(craft).state = ShuttleState.DEPARTING;
        assertEquals(engagement.canEngage(marine, craft),
                engagement.canEngage(mech, craft),
                "and still the same once it is out of both their reach");
    }

    @Test
    public void aHeldTargetStopsBeingEngageableTheTickItLifts() {
        UnitRosterService roster = roster();
        EngagementService engagement = new EngagementService(roster);
        long craft = TaxiingAircraftIsOneBodyTest.taxiingAircraft(
                roster, Faction.DEFENDER, 20.5f, 20.5f);
        long marine = marine(roster);
        assertTrue(engagement.canEngage(marine, craft));

        roster.world().mission(craft).state = ShuttleState.TAKEOFF_ROLL;
        assertTrue(engagement.canEngage(marine, craft),
                "a takeoff roll is still on the wheels — the lock survives it");

        roster.world().mission(craft).state = ShuttleState.INCOMING;
        assertFalse(engagement.canEngage(marine, craft),
                "the moment it is airborne the lock is gone, rather than lasting "
                        + "until range or line of sight happens to break it");
    }

    @Test
    public void aCraftWithItsRampOpenIsNotEngageableByAnybody() {
        UnitRosterService roster = roster();
        EngagementService engagement = new EngagementService(roster);
        long craft = TaxiingAircraftIsOneBodyTest.taxiingAircraft(
                roster, Faction.DEFENDER, 20.5f, 20.5f);
        roster.world().mission(craft).state = ShuttleState.LOADING;

        assertFalse(engagement.canEngage(marine(roster), craft));
        assertFalse(engagement.canEngage(defencePost(roster), craft),
                "presence is the candidate's own business, and a loading craft "
                        + "owes passengers a disposition nothing gives them");
    }
}
