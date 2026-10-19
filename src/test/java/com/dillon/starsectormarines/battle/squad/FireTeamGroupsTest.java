package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FireTeamGroupsTest {

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(64, 64), null);
    }

    @Test
    void generatedSquadClaimsStableFourMarineTeams() {
        UnitRosterService roster = roster();
        int squadId = roster.mintSquad(Faction.MARINE, UnitType.MARINE);
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            members.add(roster.spawn(new EntitySpec("m" + i, Faction.MARINE,
                    UnitType.MARINE, i, 0).squad(squadId)));
        }

        List<FireTeamGroups.Team> teams = FireTeamGroups.organize(members, roster.squad());
        assertEquals(List.of(4, 5), teams.stream().map(t -> t.members().size()).toList(),
                "the one-marine third team consolidates into a viable sibling");
        assertEquals(0, roster.squad().fireTeamIndex(members.get(0)));
        assertEquals(1, roster.squad().fireTeamIndex(members.get(4)));
        assertEquals(2, roster.squad().fireTeamIndex(members.get(8)));
    }

    @Test
    void singletonTeamConsolidatesIntoNearestViableSibling() {
        UnitRosterService roster = roster();
        List<Long> members = new ArrayList<>();
        int[] identities = {0, 0, 1, 2, 2};
        for (int i = 0; i < identities.length; i++) {
            members.add(roster.spawn(new EntitySpec("m" + i, Faction.MARINE,
                    UnitType.MARINE, i, 0).squad(7).fireTeam(identities[i])));
        }

        List<FireTeamGroups.Team> teams = FireTeamGroups.organize(members, roster.squad());
        assertEquals(2, teams.size());
        assertEquals(3, teams.get(0).members().size());
        assertEquals(2, teams.get(1).members().size());
        assertEquals(0, teams.get(0).index());
        assertEquals(2, teams.get(1).index());
    }

    private static List<Long> spawn(UnitRosterService roster, int... teamIdentities) {
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < teamIdentities.length; i++) {
            members.add(roster.spawn(new EntitySpec("m" + i, Faction.MARINE,
                    UnitType.MARINE, i, 0).squad(7).fireTeam(teamIdentities[i])));
        }
        return members;
    }

    @Test
    void everyTeamBelowStrengthFormsOneTeamUnderTheLowestIndex() {
        UnitRosterService roster = roster();
        List<Long> members = spawn(roster, 3, 1, 2);

        List<FireTeamGroups.Team> teams = FireTeamGroups.organize(members, roster.squad());

        assertEquals(1, teams.size());
        assertEquals(1, teams.get(0).index());
        assertEquals(members, teams.get(0).members(), "members come back in ascending id order");
    }

    @Test
    void equidistantOrphanJoinsTheEarlierTeamUnlessTheLaterIsSmaller() {
        UnitRosterService roster = roster();
        List<Long> even = spawn(roster, 0, 0, 2, 2, 1);
        List<FireTeamGroups.Team> evenTeams = FireTeamGroups.organize(even, roster.squad());
        assertEquals(List.of(3, 2), evenTeams.stream().map(t -> t.members().size()).toList());

        List<Long> lopsided = spawn(roster, 0, 0, 0, 2, 2, 1);
        List<FireTeamGroups.Team> lopsidedTeams = FireTeamGroups.organize(lopsided, roster.squad());
        assertEquals(List.of(3, 3), lopsidedTeams.stream().map(t -> t.members().size()).toList());
        assertEquals(lopsided.get(5), lopsidedTeams.get(1).members().get(2),
                "the orphan lands in the smaller, later team and sorts in by id");
    }

    @Test
    void primitiveFormMatchesTheListFormOnASlice() {
        UnitRosterService roster = roster();
        List<Long> members = spawn(roster, 0, 0, 1, 2, 2, 3);
        long[] ids = new long[members.size() + 2];
        for (int i = 0; i < members.size(); i++) ids[i + 1] = members.get(i);

        FireTeamGroups.Teams teams = new FireTeamGroups.Teams();
        FireTeamGroups.organizeInto(ids, 1, members.size(), roster.squad(), teams);

        List<FireTeamGroups.Team> expected = FireTeamGroups.organize(members, roster.squad());
        assertEquals(expected.size(), teams.count());
        for (int t = 0; t < teams.count(); t++) {
            assertEquals(expected.get(t).index(), teams.index(t));
            assertEquals(expected.get(t).members().size(), teams.length(t));
            for (int k = 0; k < teams.length(t); k++) {
                assertEquals(expected.get(t).members().get(k), teams.member(t, k));
            }
        }
    }
}
