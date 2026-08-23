package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The twelve-marine squad and the three four-marine fire teams inside it. */
class SquadFireTeamStructureTest {

    @Test
    void aSquadIsThreeFireTeamsOfFour() {
        assertEquals(4, MarineSquad.TEAM_SIZE);
        assertEquals(3, MarineSquad.TEAMS_PER_SQUAD);
        assertEquals(12, MarineSquad.CAPACITY);
    }

    @Test
    void billetsPartitionIntoTeamsInRosterOrder() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        MarineSquad squad = roster.squads().get(0);
        List<String> members = squad.memberIds();

        assertEquals(MarineSquad.CAPACITY, members.size());
        for (int i = 0; i < members.size(); i++) {
            assertEquals(i / MarineSquad.TEAM_SIZE, squad.teamIndexOf(members.get(i)));
        }
        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            assertEquals(MarineSquad.TEAM_SIZE, squad.teamMembers(team).size());
        }
    }

    @Test
    void teamsConsolidateRatherThanHollowOutWhenUndermanned() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.TEAM_SIZE + 1);
        MarineSquad squad = roster.squads().get(0);

        assertEquals(MarineSquad.TEAM_SIZE, squad.teamMembers(0).size());
        assertEquals(1, squad.teamMembers(1).size());
        assertTrue(squad.teamMembers(2).isEmpty());
    }

    @Test
    void nonMembersAndOutOfRangeTeamsReadEmpty() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(1);
        MarineSquad squad = roster.squads().get(0);

        assertEquals(-1, squad.teamIndexOf("not-a-member"));
        assertTrue(squad.teamMembers(-1).isEmpty());
        assertTrue(squad.teamMembers(MarineSquad.TEAMS_PER_SQUAD).isEmpty());
    }

    @Test
    void oneFullSquadIsTheStartingComplement() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);

        assertEquals(1, roster.squads().size());
        assertEquals(MarineSquad.CAPACITY, roster.manningCount(roster.squads().get(0)));
        assertEquals(0, roster.vacancies(roster.squads().get(0)));
    }
}
