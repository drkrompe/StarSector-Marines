package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnterZoneEchelonTest {

    @Test
    void quietOpenAdvanceReleasesFireTeamsBySpatialProgress() {
        BattleSimulation sim = openArena(40, 20);
        Fixture fixture = fixture(sim, 5, 8, 34, 9);

        fixture.action.execute(fixture.members.get(0), fixture.squad, sim);
        fixture.action.execute(fixture.members.get(4), fixture.squad, sim);
        fixture.action.execute(fixture.members.get(8), fixture.squad, sim);

        assertFalse(Paths.isEmpty(sim.world().path(fixture.members.get(0))));
        assertTrue(Paths.isEmpty(sim.world().path(fixture.members.get(4))),
                "second team waits for a two-cell lead");
        assertTrue(Paths.isEmpty(sim.world().path(fixture.members.get(8))),
                "third team waits on the second team, not the squad clock");

        moveTeamToX(sim, fixture.members, 0, 8);
        fixture.action.execute(fixture.members.get(4), fixture.squad, sim);
        fixture.action.execute(fixture.members.get(8), fixture.squad, sim);
        assertFalse(Paths.isEmpty(sim.world().path(fixture.members.get(4))));
        assertTrue(Paths.isEmpty(sim.world().path(fixture.members.get(8))));

        moveTeamToX(sim, fixture.members, 1, 8);
        fixture.action.execute(fixture.members.get(8), fixture.squad, sim);
        assertFalse(Paths.isEmpty(sim.world().path(fixture.members.get(8))));
    }

    @Test
    void constrainedPassageReleasesAllTeamsToNavigation() {
        int width = 30;
        int height = 7;
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int x = 1; x < width - 1; x++) grid.setWalkableFloor(x, 3);
        BattleSimulation sim = new BattleSimulation(grid,
                new CellTopology(width, height));
        Fixture fixture = fixture(sim, 3, 3, 26, 3);

        fixture.action.execute(fixture.members.get(0), fixture.squad, sim);
        fixture.action.execute(fixture.members.get(4), fixture.squad, sim);
        fixture.action.execute(fixture.members.get(8), fixture.squad, sim);

        assertFalse(Paths.isEmpty(sim.world().path(fixture.members.get(0))));
        assertFalse(Paths.isEmpty(sim.world().path(fixture.members.get(4))));
        assertFalse(Paths.isEmpty(sim.world().path(fixture.members.get(8))),
                "echelon authority must yield inside a one-cell passage");
    }

    private static Fixture fixture(BattleSimulation sim, int startX, int startY,
                                   int destX, int destY) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            long member = sim.spawn(new EntitySpec("m" + i, Faction.MARINE,
                    UnitType.MARINE, startX, startY).squad(squadId)
                    .fireTeam(i / Squad.FIRE_TEAM_SIZE));
            members.add(member);
        }
        squad.leaderId = members.get(0);
        squad.aliveMembers = members.size();
        squad.originalSize = members.size();
        squad.centroidX = startX + 0.5f;
        squad.centroidY = startY + 0.5f;
        EnterZone action = new EnterZone(-99, destX, destY);
        SquadPlan.Step step = new SquadPlan.Step(action);
        step.assignments.putAll(action.assignRoles(squad, sim, members));
        squad.currentPlan = new SquadPlan(List.of(step));
        return new Fixture(squad, action, members);
    }

    private static void moveTeamToX(BattleSimulation sim, List<Long> members,
                                    int team, int x) {
        int first = team * Squad.FIRE_TEAM_SIZE;
        for (int i = first; i < first + Squad.FIRE_TEAM_SIZE; i++) {
            sim.world().setCellPos(members.get(i), x, sim.world().cellY(members.get(i)));
        }
    }

    private static BattleSimulation openArena(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }

    private record Fixture(Squad squad, EnterZone action, List<Long> members) {}
}
