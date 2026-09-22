package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.decision.goap.action.AmbientAdvance;
import com.dillon.starsectormarines.battle.decision.goap.action.AttackMove;
import com.dillon.starsectormarines.battle.decision.goap.action.EnterZone;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.RouteCostField;
import com.dillon.starsectormarines.battle.nav.SquadRouteRequest;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadRoutePreparationSystemTest {
    private final NavigationGrid grid = new NavigationGrid(20, 10);
    private final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(20, 10), null);
    private final RouteCostField cost = new RouteCostField(new float[200], 17L);

    @Test
    void freezesStartsInMemberOrderAndRejectsAbsentOrRidingMembers() {
        long first = spawn(3, 1);
        long second = spawn(2, 1);
        long riding = spawn(1, 1);
        Squad later = squad(9, new SquadPlan.Step(new EnterZone(1, 10, 2)));
        later.currentPlan.currentStep().assignments.put("second", List.of(second, riding));
        later.currentPlan.currentStep().assignments.put("first", List.of(first, second, 999L));
        Squad earlier = squad(2, new SquadPlan.Step(new AmbientAdvance(12, 2)));
        earlier.currentPlan.currentStep().assignments.put("any", List.of(first));

        List<SquadRouteRequest> requests = SquadRoutePreparationSystem.collect(
                view(List.of(later, earlier), Set.of(riding)));
        assertEquals(List.of(2, 9), requests.stream().map(SquadRouteRequest::squadId).toList());
        SquadRouteRequest request = requests.get(1);
        assertArrayEquals(new int[]{grid.index(3, 1), grid.index(2, 1)}, request.startCells());
        assertSame(cost, request.cost());
        assertSame(later.currentPlan.currentStep(), request.routeToken());
        int[] changed = request.startCells();
        changed[0] = 0;
        assertEquals(grid.index(3, 1), request.startCells()[0]);
    }

    @Test
    void stepAdvanceAndStickyPlanEpochAreSeparateRouteIdentities() {
        long member = spawn(3, 1);
        SquadPlan.Step first = new SquadPlan.Step(new EnterZone(1, 10, 2));
        SquadPlan.Step second = new SquadPlan.Step(new AmbientAdvance(12, 2));
        first.assignments.put("any", List.of(member));
        second.assignments.put("any", List.of(member));
        Squad squad = squad(1, first, second);
        BattleView sim = view(List.of(squad), Set.of());
        SquadRouteRequest before = SquadRoutePreparationSystem.collect(sim).get(0);
        squad.routingEpoch++;
        SquadRouteRequest replan = SquadRoutePreparationSystem.collect(sim).get(0);
        assertSame(before.routeToken(), replan.routeToken());
        assertEquals(before.routingEpoch() + 1, replan.routingEpoch());
        squad.currentPlan.advance();
        SquadRouteRequest advance = SquadRoutePreparationSystem.collect(sim).get(0);
        assertNotSame(before.routeToken(), advance.routeToken());
        assertEquals(replan.routingEpoch(), advance.routingEpoch());
        assertEquals(12, advance.goalX());
    }

    @Test
    void attackMoveOnlyAdvertisesItsOrderedCellOutsideDynamicManeuver() {
        Squad squad = squad(1, new SquadPlan.Step(new AttackMove(10, 2)));
        squad.currentPlan.currentStep().assignments.put("any", List.of(spawn(3, 1)));
        assertEquals(10, SquadRoutePreparationSystem.collect(
                view(List.of(squad), Set.of())).get(0).goalX());
        squad.assaultPicture = new SquadAssaultPicture(1,
                SquadAssaultPicture.Role.MANEUVER, 77L, 2, 1f, 0f);
        assertTrue(SquadRoutePreparationSystem.collect(view(List.of(squad), Set.of())).isEmpty());
    }

    private long spawn(int x, int y) {
        return roster.spawn(new EntitySpec("member", Faction.MARINE, UnitType.MARINE_BLUE, x, y));
    }

    private static Squad squad(int id, SquadPlan.Step... steps) {
        Squad squad = new Squad(id, Faction.MARINE);
        squad.currentPlan = new SquadPlan(List.of(steps));
        return squad;
    }

    private BattleView view(List<Squad> squads, Set<Long> riding) {
        return (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),
                new Class<?>[]{BattleView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getGrid" -> grid;
                    case "getSquads" -> squads;
                    case "world" -> roster.world();
                    case "resolveUnit" -> roster.world().isAlive((long) args[0]) ? args[0] : 0L;
                    case "isRiding" -> riding.contains((long) args[0]);
                    case "getRouteCostField" -> cost;
                    default -> throw new AssertionError("Unexpected query: " + method.getName());
                });
    }
}
