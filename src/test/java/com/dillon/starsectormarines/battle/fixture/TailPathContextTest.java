package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.goap.action.ClearZone;
import com.dillon.starsectormarines.battle.infantry.SecureCompoundGoal;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;
import org.json.JSONException;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Small real grid/roster, no battle setup or ticking. */
class TailPathContextTest {
    @Test
    void capturesScopeAndPositionsWithoutConfusingThemWithQueryCoordinates() throws JSONException {
        Fixture f = new Fixture();
        TailPathContext context = TailPathContext.capture(List.of(f.search()), f.view).get(0);
        assertEquals("SecureCompound", context.goal());
        assertEquals("SECURE_COMPOUND", context.assignmentKind());
        assertEquals(18, context.actionZone().cells());
        assertEquals(new TailPathContext.Bounds(0, 0, 5, 2), context.actionZone().bounds());
        assertEquals(new TailPathContext.Bounds(4, 0, 5, 2), context.targetNode().footprint());
        assertEquals(1, context.member().cellX());
        assertEquals(5, context.target().cellX());
        assertEquals(f.target, context.currentTargetId());
        assertEquals("POST_TICK_NOT_QUERY_TIME", context.json().optString("observation"));
        assertEquals(40, context.observedTick());
    }

    @Test
    void frozenContextDoesNotFollowLaterPlanNodeOrTopologyChanges() throws JSONException {
        Fixture f = new Fixture();
        List<TailPathContext> contexts = TailPathContext.capture(List.of(f.search()), f.view);
        String captured = contexts.get(0).json().toString();
        f.squad.currentPlan = null;
        f.squad.assignedObjective = null;
        f.node.setCompoundBounds(0, 0, 100, 100);
        f.grid.setWalkable(0, 0, false);
        f.zones.rebuild();
        assertEquals(captured, contexts.get(0).json().toString());
        assertThrows(UnsupportedOperationException.class, () -> contexts.clear());
    }

    @Test
    void vanishedMemberAndSquadAreReportedAsMissingNotReadThroughStaleIdentity() {
        Fixture f = new Fixture();
        TickInnerProfile.PathSearch search = new TickInnerProfile.PathSearch(1L,
                0, 0, 3, 2, true, true, 4, 10, -99L, -99, "ClearZone", "");
        TailPathContext context = TailPathContext.capture(List.of(search), f.view).get(0);
        assertNull(context.member());
        assertNull(context.target());
        assertNull(context.actionZone());
        assertEquals("", context.goal());
        assertEquals(-99L, context.memberId());
    }

    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(6, 3);
        final ZoneGraph zones;
        final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(6, 3), null);
        final Squad squad = new Squad(7, Faction.MARINE);
        final TacticalNode node = new TacticalNode(TacticalNode.Kind.ARMORY,
                4, 1, 4, 0, 5, 2, Faction.DEFENDER, 80, 4);
        final long member, target;
        final BattleView view;

        Fixture() {
            for (int y = 0; y < 3; y++) for (int x = 0; x < 6; x++) grid.setWalkableFloor(x, y);
            zones = new ZoneGraph(grid);
            zones.rebuild();
            member = roster.spawn(new EntitySpec("member", Faction.MARINE, UnitType.MARINE, 1, 1));
            target = roster.spawn(new EntitySpec("target", Faction.DEFENDER, UnitType.MARINE, 5, 1));
            int zone = zones.zoneIdAt(1, 1);
            squad.assignedObjective = ObjectiveAssignment.secureCompound(7, zone, node);
            squad.currentGoal = SecureCompoundGoal.INSTANCE;
            squad.currentPlan = new SquadPlan(List.of(new SquadPlan.Step(new ClearZone(zone))));
            view = (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),
                    new Class<?>[]{BattleView.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getGrid" -> grid;
                        case "getZoneGraph" -> zones;
                        case "getSquad" -> (int) args[0] == 7 ? squad : null;
                        case "getSimTickIndex" -> 40;
                        case "world" -> roster.world();
                        case "targetOf" -> target;
                        case "liveUnitIndexOf" -> (long) args[0] == member ? 0 : (long) args[0] == target ? 1 : -1;
                        default -> throw new AssertionError("Unexpected query: " + method.getName());
                    });
        }

        TickInnerProfile.PathSearch search() {
            return new TickInnerProfile.PathSearch(1L, 0, 0, 3, 2,
                    true, true, 4, 10, member, 7, "ClearZone", "");
        }
    }
}
