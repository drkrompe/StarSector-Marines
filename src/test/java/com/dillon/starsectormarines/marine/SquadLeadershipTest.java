package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rank follows the billet: one squad leader, one lance corporal per other manned team. */
class SquadLeadershipTest {

    @Test
    void aFullSquadHasOneLeaderAndOneLanceCorporalPerOtherTeam() {
        MarineRoster roster = rosterOfOneSquad();
        MarineSquad squad = roster.squads().get(0);

        MarineSoldier leader = roster.squadLeader(squad);
        assertNotNull(leader);
        assertEquals(EnlistedRank.CORPORAL, leader.enlistedRank());

        int lanceCorporals = 0;
        int marines = 0;
        for (MarineSoldier soldier : roster.squadMembers(squad)) {
            if (soldier.enlistedRank() == EnlistedRank.LANCE_CORPORAL) lanceCorporals++;
            if (soldier.enlistedRank() == EnlistedRank.MARINE) marines++;
        }
        assertEquals(MarineSquad.TEAMS_PER_SQUAD - 1, lanceCorporals);
        assertEquals(MarineSquad.CAPACITY - MarineSquad.TEAMS_PER_SQUAD, marines);
    }

    @Test
    void everyMannedTeamOtherThanTheLeadersHasItsOwnLeader() {
        MarineRoster roster = rosterOfOneSquad();
        MarineSquad squad = roster.squads().get(0);
        int leaderTeam = squad.teamIndexOf(roster.squadLeader(squad).id());

        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            if (team == leaderTeam) continue;
            List<String> members = squad.teamMembers(team);
            long leading = members.stream()
                    .map(roster::soldierById)
                    .filter(s -> s.enlistedRank() == EnlistedRank.LANCE_CORPORAL)
                    .count();
            assertEquals(1, leading, "fire team " + team + " has exactly one leader");
        }
    }

    @Test
    void losingTheLeaderPromotesTheSeniorFireTeamLeader() {
        MarineRoster roster = rosterOfOneSquad();
        MarineSquad squad = roster.squads().get(0);
        MarineSoldier leader = roster.squadLeader(squad);
        List<MarineSoldier> teamLeaders = withRank(roster, squad, EnlistedRank.LANCE_CORPORAL);
        MarineSoldier heir = teamLeaders.get(0);
        heir.addExperience(100);

        kill(roster, leader);

        assertSame(heir, roster.squadLeader(squad));
        assertEquals(EnlistedRank.CORPORAL, heir.enlistedRank());

        // Stable across further derivations: the same person leads next battle.
        roster.recoverWounded(1f);
        assertSame(heir, roster.squadLeader(squad));
    }

    @Test
    void rankOutranksExperienceWhenPromoting() {
        MarineRoster roster = rosterOfOneSquad();
        MarineSquad squad = roster.squads().get(0);
        MarineSoldier leader = roster.squadLeader(squad);
        MarineSoldier veteranRifleman = withRank(roster, squad, EnlistedRank.MARINE).get(0);
        veteranRifleman.addExperience(1000);

        kill(roster, leader);

        MarineSoldier successor = roster.squadLeader(squad);
        assertNotSame(veteranRifleman, successor,
                "a fire-team leader steps up before the most experienced rifleman does");
        assertEquals(EnlistedRank.CORPORAL, successor.enlistedRank());
    }

    private static List<MarineSoldier> withRank(MarineRoster roster, MarineSquad squad,
                                                EnlistedRank rank) {
        List<MarineSoldier> matching = new ArrayList<>();
        for (MarineSoldier soldier : roster.squadMembers(squad)) {
            if (soldier.enlistedRank() == rank) matching.add(soldier);
        }
        return matching;
    }

    private static void kill(MarineRoster roster, MarineSoldier soldier) {
        Map<String, MarineSoldierStatus> outcome = new HashMap<>();
        outcome.put(soldier.id(), MarineSoldierStatus.KIA);
        roster.applySoldierOutcome(outcome, 0, 0f, 1f);
    }

    @Test
    void aWoundedLeaderResumesTheBilletOnReturn() {
        MarineRoster roster = rosterOfOneSquad();
        MarineSquad squad = roster.squads().get(0);
        MarineSoldier leader = roster.squadLeader(squad);
        leader.addExperience(500);

        Map<String, MarineSoldierStatus> outcome = new HashMap<>();
        outcome.put(leader.id(), MarineSoldierStatus.WIA);
        roster.applySoldierOutcome(outcome, 0, 10f, 7f);

        MarineSoldier acting = roster.squadLeader(squad);
        assertNotEquals(leader.id(), acting.id());
        assertEquals(EnlistedRank.CORPORAL, leader.enlistedRank(),
                "the wounded keep their stripes");

        roster.recoverWounded(100f);

        assertSame(leader, roster.squadLeader(squad));
        // The stand-in gives the squad back but may still lead their own fire team.
        assertNotEquals(EnlistedRank.CORPORAL, acting.enlistedRank());
        assertNotEquals(EnlistedRank.SERGEANT, acting.enlistedRank());
    }

    @Test
    void aVeteranSquadLeaderWearsSergeantsStripes() {
        MarineRoster roster = rosterOfOneSquad();
        MarineSquad squad = roster.squads().get(0);
        roster.squadLeader(squad).addExperience(ExperienceTier.VETERAN.minimumXp);

        // Any membership change re-derives leadership.
        assertNotNull(roster.recruitToSquad(roster.reserveSquad().id()));

        assertEquals(EnlistedRank.SERGEANT, roster.squadLeader(squad).enlistedRank());
    }

    @Test
    void anEmptiedSquadAndTheReservePoolHaveNoLeader() {
        MarineRoster roster = rosterOfOneSquad();
        MarineSquad squad = roster.squads().get(0);
        Map<String, MarineSoldierStatus> outcome = new HashMap<>();
        for (MarineSoldier soldier : roster.squadMembers(squad)) {
            outcome.put(soldier.id(), MarineSoldierStatus.KIA);
        }
        roster.applySoldierOutcome(outcome, 0, 0f, 1f);

        assertNull(roster.squadLeader(squad));
        assertNull(roster.reserveSquad().leaderSoldierId());
    }

    @Test
    void anUndermannedSquadStillLeadsTheTeamsItHas() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.TEAM_SIZE + 1);
        MarineSquad squad = roster.squads().get(0);

        MarineSoldier leader = roster.squadLeader(squad);
        assertNotNull(leader);

        // Five green marines tie on rank and experience, so which one holds
        // the badge comes down to the id tiebreak — stable inside a roster,
        // arbitrary across them. Asserting a *specific* billet leads was a
        // one-in-five coin flip and is not what this test is about. What must
        // hold is the shape: the manned teams each have a leader and the
        // empty one has nobody.
        int leaderTeam = squad.teamIndexOf(leader.id());
        assertTrue(leaderTeam == 0 || leaderTeam == 1,
                "the leader comes from a manned team, not the empty third");

        // Count, do not index: which member of a team wins its lance-corporal
        // billet is the same id tiebreak, so "team 0 slot 0 is the NCO" is a
        // coin flip whenever that team has more than one marine in it.
        int otherTeam = leaderTeam == 0 ? 1 : 0;
        int lanceCorporals = 0;
        for (String memberId : squad.teamMembers(otherTeam)) {
            if (roster.soldierById(memberId).enlistedRank() == EnlistedRank.LANCE_CORPORAL) {
                lanceCorporals++;
            }
        }
        assertEquals(1, lanceCorporals,
                "the manned team the leader is not in gets exactly one team leader");
        assertTrue(squad.teamMembers(2).isEmpty());
    }

    private static MarineRoster rosterOfOneSquad() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        return roster;
    }
}
