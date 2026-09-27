package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlankApproachRefusalTest {
    @Test
    void refusalCompletesBeforeEitherDispersedTeamCanQueryOrMove() {
        Squad squad = new Squad(1, Faction.MARINE);
        squad.leaderId = 1;
        squad.centroidX = 20.5f;
        squad.centroidY = 5.5f;
        FlankApproach action = new FlankApproach(2, 5, true);
        SquadPlan.Step step = new SquadPlan.Step(action);
        step.assignments.put("fix:0", List.of(1L));
        step.assignments.put("flank:1", List.of(2L));
        squad.currentPlan = new SquadPlan(List.of(step));
        BattleControl untouched = (BattleControl) Proxy.newProxyInstance(
                BattleControl.class.getClassLoader(), new Class<?>[]{BattleControl.class},
                (proxy, method, args) -> { throw new AssertionError("Refusal queried " + method.getName()); });

        assertEquals(ActionStatus.SUCCESS, action.execute(1L, squad, untouched));
        assertEquals(ActionStatus.SUCCESS, action.execute(2L, squad, untouched));
        assertTrue(action.highlightCells(squad, untouched).isEmpty());
    }

    @Test
    void ordinaryAcceptedConstructorStillAuthorsAPathAndAdvancesTheManeuveringMember() {
        NavigationGrid grid = new NavigationGrid(24, 8);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 24; x++) grid.setWalkableFloor(x, y);
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(24, 8), null);
        roster.movement().setNavigationGrid(grid);
        long member = roster.spawn(new EntitySpec("flanker", Faction.MARINE, UnitType.MARINE, 2, 5));
        Squad squad = new Squad(1, Faction.MARINE);
        FlankApproach action = new FlankApproach(20, 5);
        SquadPlan.Step step = new SquadPlan.Step(action);
        step.assignments.put("flank:0", List.of(member));
        squad.currentPlan = new SquadPlan(List.of(step));
        int[] advances = {0};
        BattleControl control = (BattleControl) Proxy.newProxyInstance(
                BattleControl.class.getClassLoader(), new Class<?>[]{BattleControl.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "world" -> roster.world();
                    case "movement" -> roster.movement();
                    case "getGrid" -> grid;
                    case "getOccupancyMap" -> null;
                    case "resolveUnit" -> args[0];
                    case "setPath" -> {
                        roster.movement().setPathRef((long) args[0], (int[]) args[1]);
                        roster.movement().setPathIdx((long) args[0], 0);
                        yield null;
                    }
                    case "advanceMovement" -> {
                        advances[0]++;
                        roster.movement().advanceAlongPath(roster.world(), (long) args[0], 1f / 30f);
                        yield null;
                    }
                    default -> throw new AssertionError("Unexpected query " + method.getName());
                });
        float before = roster.world().x(member);
        assertEquals(ActionStatus.RUNNING, action.execute(member, squad, control));
        assertFalse(Paths.isEmpty(roster.world().path(member)));
        assertEquals(20, Paths.destX(roster.world().path(member)));
        assertEquals(1, advances[0]);
        assertTrue(roster.world().x(member) > before);
    }
}
