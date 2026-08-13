package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptainDeploymentPolicyTest {

    @Test
    void defaultsToRosterOrderedHomeFormation() {
        MarineRoster roster = roster(18);
        MarineCaptain captain = captain(Rank.CORPORAL);
        roster.add(captain);
        MarineSquad first = roster.squads().get(0);
        MarineSquad second = roster.squads().get(1);
        assertTrue(roster.assignCaptainToSquad(captain.id(), first.id()));
        assertTrue(roster.assignCaptainToSquad(captain.id(), second.id()));

        assertEquals(List.of(first.id(), second.id()),
                CaptainDeploymentPolicy.defaultSquadIds(roster, captain));
    }

    @Test
    void borrowingStopsAtWholeFireteamRankCap() {
        MarineRoster roster = roster(18);
        MarineCaptain captain = captain(Rank.PRIVATE);
        roster.add(captain);
        MarineSquad first = roster.squads().get(0);
        MarineSquad second = roster.squads().get(1);
        Set<String> selected = new LinkedHashSet<>();
        selected.add(first.id());

        assertFalse(CaptainDeploymentPolicy.canAdd(
                roster, captain, selected, second.id()));
        assertTrue(CaptainDeploymentPolicy.isValidCommand(
                roster, captain, selected));

        selected.add(second.id());
        assertFalse(CaptainDeploymentPolicy.isValidCommand(
                roster, captain, selected));
    }

    @Test
    void unavailableCaptainCannotLeadOrBorrow() {
        MarineRoster roster = roster(6);
        MarineCaptain captain = captain(Rank.GENERAL);
        roster.add(captain);
        captain.setStatus(Status.INJURED);

        assertTrue(CaptainDeploymentPolicy.defaultSquadIds(
                roster, captain).isEmpty());
        assertFalse(CaptainDeploymentPolicy.canAdd(roster, captain,
                Set.of(), roster.squads().get(0).id()));
        assertFalse(CaptainDeploymentPolicy.isValidCommand(
                roster, captain, Set.of()));
    }

    private static MarineRoster roster(int personnel) {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(personnel);
        return roster;
    }

    private static MarineCaptain captain(Rank rank) {
        return new MarineCaptain("Commander", null, rank, 0f);
    }
}
