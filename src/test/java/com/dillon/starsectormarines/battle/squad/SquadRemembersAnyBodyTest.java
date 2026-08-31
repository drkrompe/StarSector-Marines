package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.TaxiingAircraftIsOneBodyTest;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A squad's memory of a contact must not depend on what is carrying the body it
 * saw.
 *
 * <p>The expiry predicate used to be a hand-written list — a live roster unit,
 * or a targetable convoy vehicle — so a squad that watched an aircraft taxi
 * across its front recorded the contact and dropped it again on the very next
 * ageing pass. Nothing reported anything; the squad simply never knew.
 */
public class SquadRemembersAnyBodyTest {

    /**
     * The system is built with its collaborators null on purpose: the question
     * is about one predicate, which reads the roster and nothing else.
     */
    private static SquadAlertSystem alerts(UnitRosterService roster) {
        return new SquadAlertSystem(null, roster, null, null);
    }

    @Test
    public void aTaxiingAircraftStaysARememberedContact() {
        UnitRosterService roster =
                new UnitRosterService(new UnitSpatialIndex(64, 64), null);
        long craft = TaxiingAircraftIsOneBodyTest.taxiingAircraft(
                roster, Faction.DEFENDER, 20.5f, 20.5f);
        int squadId = roster.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = roster.getSquad(squadId);

        assertTrue(alerts(roster).identityResolves(craft),
                "a body is a body whatever is carrying it");

        SquadBeliefTestAccess.observeDirect(squad, craft, 20, 20, 1);
        assertNotNull(squad.believedContact(craft), "the squad saw it");

        SquadBeliefTestAccess.ageBeliefs(squad, 1f / 30f, 2,
                alerts(roster)::identityResolves);
        assertNotNull(squad.believedContact(craft),
                "and still knows about it a tick later");
    }

    @Test
    public void aCraftThatLiftsIsForgotten() {
        UnitRosterService roster =
                new UnitRosterService(new UnitSpatialIndex(64, 64), null);
        long craft = TaxiingAircraftIsOneBodyTest.taxiingAircraft(
                roster, Faction.DEFENDER, 20.5f, 20.5f);
        int squadId = roster.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = roster.getSquad(squadId);
        SquadBeliefTestAccess.observeDirect(squad, craft, 20, 20, 1);

        roster.world().mission(craft).state = ShuttleState.INCOMING;
        assertFalse(alerts(roster).identityResolves(craft));

        SquadBeliefTestAccess.ageBeliefs(squad, 1f / 30f, 2,
                alerts(roster)::identityResolves);
        assertNull(squad.believedContact(craft),
                "belief about something out of reach steers a squad at nothing");
    }
}
