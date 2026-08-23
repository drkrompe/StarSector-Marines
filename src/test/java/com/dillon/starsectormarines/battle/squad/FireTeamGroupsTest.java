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
}
