package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RaidCommandTest {
    private static CommandTopology topology() {
        var grid = new NavigationGrid(12, 12);
        for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        var graph = new ZoneGraph(grid);
        var view = (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(), new Class<?>[]{BattleView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getGrid" -> grid;
                    case "getZoneGraph" -> graph;
                    default -> throw new AssertionError("Unexpected topology read: " + method.getName());
                });
        return CommandTopology.freeze(view);
    }

    /** A squad the player told to hold a spot: an unbounded lease. */
    private static CommandSquadState heldByPlayer() {
        var hold = ObjectiveAssignment.defendArea(1, 2, 2, 3);
        var directive = new CommandDirective(1, Faction.MARINE, "player", CommandAuthority.PLAYER_INTERVENTION,
                "fixture", hold, 0, -1, CommandDirective.Status.ACTIVE, "");
        return new CommandSquadState(1, Faction.MARINE, 4, 2.5f, 2.5f, 2, 2, 0, UnitRole.PATROL, false, null,
                hold, directive, 0, new int[0]);
    }

    private static RaidCommandFrame frame(boolean secured, CommandSquadState squad) {
        var facts = new RaidCommandFacts("records", "records", 5, 5, 0, 9, 9, secured ? 10f : 2f, 10f, false,
                secured, false, secured ? RaidObjective.Phase.EGRESS : RaidObjective.Phase.SERVICE);
        var common = new CommandFrame(75, Faction.MARINE, List.of(squad), null, topology(),
                new CommandAssignmentSnapshot(Map.of()));
        return RaidCommandFrame.of(common, facts);
    }

    @Test void theRaidWithdrawsASquadHeldByThePlayerOnceTheTargetIsSecured() {
        var command = new RaidCommand();
        assertTrue(command.plan(frame(false, heldByPlayer())).proposals().isEmpty(),
                "before the target is secured the player's order stands");
        var proposals = command.plan(frame(true, heldByPlayer())).proposals();
        assertEquals(1, proposals.size());
        assertEquals(CommandProposal.Action.ASSIGN, proposals.get(0).action());
        assertEquals(AssignmentKind.WITHDRAW, proposals.get(0).assignment().kind());
        assertEquals(9, proposals.get(0).assignment().targetCellX());
        assertEquals(9, proposals.get(0).assignment().targetCellY());
    }
}
