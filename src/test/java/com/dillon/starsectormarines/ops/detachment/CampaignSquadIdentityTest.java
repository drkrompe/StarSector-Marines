package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.squad.CampaignSquadTag;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSquad;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The manifest carries organizational identity across the campaign to battle seam. */
class CampaignSquadIdentityTest {

    @Test
    void fleetLiftCarriesWholeFireTeamsAndAeroshuttleCarriesHalfSquad() {
        for (ShuttleType type : ShuttleType.values()) {
            if (type != ShuttleType.AEROSHUTTLE) {
                assertEquals(0, type.capacity % Squad.FIRE_TEAM_SIZE,
                        type + " must not carry a partial fire team");
                assertEquals(type.teams * Squad.FIRE_TEAM_SIZE,
                        type.capacity, type.toString());
            }
            assertTrue(type.teams >= 1, type + " must lift at least one team");
        }
        assertEquals(MarineSquad.CAPACITY, ShuttleType.VALKYRIE.capacity,
                "the dedicated assault transport is the one that lands a squad intact");
        assertEquals(MarineSquad.CAPACITY / 2, ShuttleType.AEROSHUTTLE.capacity,
                "the Aeroshuttle lands one half-squad");
    }

    @Test
    void seatsAreSquadContiguousAndInRosterOrder() {
        MarineRoster roster = rosterOfSquads(3);
        List<MarineSquad> line = lineSquads(roster);
        // Selection order deliberately reversed: roster order is what must win.
        Set<String> selected = new LinkedHashSet<>();
        selected.add(line.get(2).id());
        selected.add(line.get(0).id());

        CampaignMarineDeployment frozen = CampaignMarineDeployment.freezeSelection(
                roster, selected, 2 * MarineSquad.CAPACITY);

        assertEquals(2 * MarineSquad.CAPACITY, frozen.size());
        for (int seat = 0; seat < MarineSquad.CAPACITY; seat++) {
            assertEquals(line.get(0).id(), frozen.seat(seat).campaignSquad.squadId,
                    "seat " + seat + " belongs to the first selected squad in roster order");
        }
        for (int seat = MarineSquad.CAPACITY; seat < 2 * MarineSquad.CAPACITY; seat++) {
            assertEquals(line.get(2).id(), frozen.seat(seat).campaignSquad.squadId,
                    "seat " + seat);
        }
    }

    @Test
    void freezingIsDeterministicAcrossRuns() {
        MarineRoster roster = rosterOfSquads(3);
        Set<String> selected = new LinkedHashSet<>();
        for (MarineSquad squad : lineSquads(roster)) selected.add(squad.id());

        List<String> first = soldierOrder(CampaignMarineDeployment.freezeSelection(
                roster, selected, 3 * MarineSquad.CAPACITY));
        List<String> second = soldierOrder(CampaignMarineDeployment.freezeSelection(
                roster, selected, 3 * MarineSquad.CAPACITY));

        assertEquals(first, second);
    }

    @Test
    void everySquadTagsExactlyOneLeaderAndCarriesItsFrozenName() {
        MarineRoster roster = rosterOfSquads(2);
        MarineSquad first = lineSquads(roster).get(0);
        Set<String> selected = Set.of(first.id());

        CampaignMarineDeployment frozen = CampaignMarineDeployment.freezeSelection(
                roster, selected, MarineSquad.CAPACITY);

        int leaders = 0;
        for (int seat = 0; seat < frozen.size(); seat++) {
            CampaignSquadTag tag = frozen.seat(seat).campaignSquad;
            assertEquals(first.name(), tag.label);
            if (tag.leader) leaders++;
        }
        assertEquals(1, leaders);

        MarineSoldier leader = roster.squadLeader(first);
        assertNotNull(leader);
        assertTrue(seatFor(frozen, leader.id()).campaignSquad.leader);
    }

    /**
     * The fire team a seat carries is the marine's billet back home, not the
     * seat's own position in the manifest. Those coincided until the NCO was
     * seated first in their squad's run — a leader billeted in the third fire
     * team now rides seat zero and must still land in the third fire team.
     */
    @Test
    void frozenSeatsCarryTheirRosterDerivedFireTeamIndex() {
        MarineRoster roster = rosterOfSquads(1);
        MarineSquad squad = lineSquads(roster).get(0);
        CampaignMarineDeployment frozen = CampaignMarineDeployment.freezeSelection(
                roster, Set.of(squad.id()), MarineSquad.CAPACITY);

        for (int seat = 0; seat < MarineSquad.CAPACITY; seat++) {
            MarineLoadout loadout = frozen.seat(seat);
            assertEquals(roster.teamIndexOf(squad, loadout.campaignSoldierId),
                    loadout.campaignSquad.fireTeamIndex, "seat " + seat);
        }
        // And still one whole squad: three manned teams of four, so the
        // reorder moved a marine rather than duplicating or dropping one.
        int[] perTeam = new int[MarineSquad.TEAMS_PER_SQUAD];
        for (int seat = 0; seat < MarineSquad.CAPACITY; seat++) {
            perTeam[frozen.seat(seat).campaignSquad.fireTeamIndex]++;
        }
        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            assertEquals(Squad.FIRE_TEAM_SIZE, perTeam[team], "fire team " + team);
        }
    }

    @Test
    void eachTagCarriesTheStrengthItsSquadIsAssemblingToward() {
        MarineRoster roster = rosterOfSquads(2);
        List<MarineSquad> line = lineSquads(roster);
        Set<String> selected = new LinkedHashSet<>();
        for (MarineSquad squad : line) selected.add(squad.id());

        // A manifest one team short of both squads: the second squad loads
        // only what fits, and must assemble toward that, not toward twelve.
        int seats = 2 * MarineSquad.CAPACITY - Squad.FIRE_TEAM_SIZE;
        CampaignMarineDeployment frozen = CampaignMarineDeployment.freezeSelection(
                roster, selected, seats);

        assertEquals(MarineSquad.CAPACITY, frozen.seat(0).campaignSquad.strength);
        assertEquals(MarineSquad.CAPACITY - Squad.FIRE_TEAM_SIZE,
                frozen.seat(seats - 1).campaignSquad.strength);
    }

    @Test
    void theLabelIsFrozenNotLive() {
        MarineRoster roster = rosterOfSquads(1);
        MarineSquad squad = lineSquads(roster).get(0);
        CampaignMarineDeployment frozen = CampaignMarineDeployment.freezeSelection(
                roster, Set.of(squad.id()), MarineSquad.CAPACITY);

        roster.renameSquad(squad.id(), "Renamed Mid-Battle");

        assertEquals("Squad 01", frozen.seat(0).campaignSquad.label);
    }

    @Test
    void generatedPersonnelCarryNoSquadIdentity() {
        // Militia, walk-in reinforcements and every other scenario-authored
        // spawn keep the per-shuttle minting. Only a seat frozen from a roster
        // carries a tag — which since `c12-the-debug-company.md` includes the
        // debug company, because it is a roster too.
        assertNull(MarineLoadout.COMBATANT.campaignSquad);
    }

    private static MarineLoadout seatFor(CampaignMarineDeployment frozen, String soldierId) {
        for (int seat = 0; seat < frozen.size(); seat++) {
            if (soldierId.equals(frozen.seat(seat).campaignSoldierId)) return frozen.seat(seat);
        }
        throw new AssertionError("no seat for " + soldierId);
    }

    private static List<String> soldierOrder(CampaignMarineDeployment frozen) {
        List<String> order = new ArrayList<>();
        for (int seat = 0; seat < frozen.size(); seat++) {
            order.add(frozen.seat(seat).campaignSoldierId);
        }
        return order;
    }

    private static List<MarineSquad> lineSquads(MarineRoster roster) {
        List<MarineSquad> line = new ArrayList<>();
        for (MarineSquad squad : roster.squads()) {
            if (!squad.reserve()) line.add(squad);
        }
        return line;
    }

    private static MarineRoster rosterOfSquads(int squads) {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(squads * MarineSquad.CAPACITY);
        assertEquals(squads, lineSquads(roster).size());
        return roster;
    }
}
