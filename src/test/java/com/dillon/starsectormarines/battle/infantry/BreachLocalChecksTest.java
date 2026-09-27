package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tiny roster/grid inputs; no battle tick or generated world is needed. */
class BreachLocalChecksTest {
    @Test
    void memberTargetKeepsDenseOrderAfterUnrelatedReleaseSwapsTheTail() {
        Fixture f = new Fixture(12, 5);
        long unrelated = f.unit(Faction.CIVILIAN, UnitType.CIVILIAN, 0, 0);
        long first = f.member(1, 1);
        long targetA = f.unit(Faction.DEFENDER, UnitType.MARINE, 8, 1);
        long targetB = f.unit(Faction.DEFENDER, UnitType.MARINE, 9, 1);
        long last = f.member(2, 1);
        f.roster.combat().setTargetId(first, targetA);
        f.roster.combat().setTargetId(last, targetB);
        assertEquals(targetA, BreachToEngage.memberTarget(f.squad, f.view));
        f.roster.release(unrelated);
        assertEquals(last, f.roster.get(0));
        assertEquals(targetB, BreachToEngage.memberTarget(f.squad, f.view),
                "stable member order must not change the original dense-order winner");
        f.roster.release(targetB);
        assertEquals(targetA, BreachToEngage.memberTarget(f.squad, f.view),
                "released targets are still filtered by targetOf");
    }

    @Test
    void unlimitedLosIncludesFarEnemyAndDistantSquadMember() {
        Fixture f = new Fixture(100, 5);
        f.member(1, 1);
        long distant = f.member(85, 1);
        f.squad.centroidX = 1.5f;
        f.squad.centroidY = 1.5f;
        // Block sight from the near member without splitting the floor zone.
        f.grid.setWalkable(20, 1, false);
        f.zones.rebuild();
        f.unit(Faction.DEFENDER, UnitType.MARINE, 90, 1);
        int zone = f.zones.zoneIdAt(1, 1);
        assertTrue(BreachToEngage.anyLocalInZoneEnemyVisible(f.squad, zone, f.view));
        f.roster.release(distant);
        assertFalse(BreachToEngage.anyLocalInZoneEnemyVisible(f.squad, zone, f.view));
        f.grid.setWalkableFloor(20, 1);
        f.zones.rebuild();
        assertTrue(BreachToEngage.anyLocalInZoneEnemyVisible(f.squad, zone, f.view),
                "no vision-range cap may be introduced into this LOS gate");
    }

    @Test
    void exactZoneCombatantAndHostilityFiltersMatchTheOriginalGate() {
        Fixture f = new Fixture(14, 5);
        f.member(2, 2);
        f.unit(Faction.ALLY, UnitType.MARINE, 3, 2);
        f.unit(Faction.DEFENDER, UnitType.CIVILIAN, 4, 2);
        for (int y = 0; y < 5; y++) f.grid.setWalkable(7, y, false);
        f.grid.setWalkableFloor(7, 2);
        f.grid.setDoorway(7, 2, true);
        f.zones.rebuild();
        f.unit(Faction.DEFENDER, UnitType.MARINE, 10, 2);
        int zone = f.zones.zoneIdAt(2, 2);
        assertFalse(BreachToEngage.anyLocalInZoneEnemyVisible(f.squad, zone, f.view),
                "a visible enemy across a doorway is not an in-zone enemy");
        long inside = f.unit(Faction.DEFENDER, UnitType.MARINE, 5, 2);
        assertTrue(BreachToEngage.anyLocalInZoneEnemyVisible(f.squad, zone, f.view));
        f.roster.release(inside);
        assertFalse(BreachToEngage.anyLocalInZoneEnemyVisible(f.squad, zone, f.view));
    }

    @Test
    void eligibilityDoesNotSynthesizeAnUnusedPlan() {
        String old = System.getProperty("battle.goap.localBreachChecks");
        System.setProperty("battle.goap.localBreachChecks", "true");
        try {
            Fixture f = new Fixture(14, 5);
            long member = f.member(2, 2);
            for (int y = 0; y < 5; y++) f.grid.setWalkable(7, y, false);
            f.grid.setWalkableFloor(7, 2);
            f.grid.setDoorway(7, 2, true);
            f.zones.rebuild();
            long enemy = f.unit(Faction.DEFENDER, UnitType.MARINE, 10, 2);
            f.roster.combat().setTargetId(member, enemy);
            Goal.EvaluationContext context = new Goal.EvaluationContext(
                    WorldState.EMPTY, f.squad, f.view, null);
            Goal.Evaluation result = context.evaluate(BreachToEngage.INSTANCE);
            assertEquals(1f, result.relevance());
            assertNull(result.preparedPlan());
            assertEquals(0, f.coverReads, "eligibility must not score forward positions");
            var plan = BreachToEngage.INSTANCE.customPlan(result, f.squad, f.view);
            assertNotNull(plan);
            assertTrue(f.coverReads > 0, "only the selected goal should synthesize its plan");
            BreachAndAdvance action = (BreachAndAdvance) plan.steps().get(0).action;
            assertEquals(1, action.slotCount());
            assertTrue(action.forwardCellX(0) > 7);
        } finally {
            if (old == null) System.clearProperty("battle.goap.localBreachChecks");
            else System.setProperty("battle.goap.localBreachChecks", old);
        }
    }

    private static final class Fixture {
        final NavigationGrid grid;
        final ZoneGraph zones;
        final UnitSpatialIndex index;
        final UnitRosterService roster;
        final Squad squad;
        final BattleView view;
        int targetReads;
        int coverReads;

        Fixture(int width, int height) {
            grid = new NavigationGrid(width, height);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
            zones = new ZoneGraph(grid);
            zones.rebuild();
            index = new UnitSpatialIndex(width, height);
            roster = new UnitRosterService(index, null);
            int id = roster.mintSquad(Faction.MARINE, UnitType.MARINE);
            squad = roster.getSquadsMap().get(id);
            view = (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),
                    new Class<?>[]{BattleView.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getGrid" -> grid;
                        case "getZoneGraph" -> zones;
                        case "getUnitIndex" -> index;
                        case "world" -> roster.world();
                        case "squad" -> roster.squad();
                        case "squadMemberCount" -> roster.squadMemberCount((int) args[0]);
                        case "squadMemberAt" -> roster.squadMemberArray((int) args[0])[(int) args[1]];
                        case "liveUnitIndexOf" -> roster.indexOf((long) args[0]);
                        case "isRiding" -> roster.isRiding((long) args[0]);
                        case "resolveUnit" -> roster.isLive((long) args[0]) ? args[0] : 0L;
                        case "targetOf" -> {
                            targetReads++;
                            long target = roster.world().targetId((long) args[0]);
                            yield roster.isLive(target) ? target : 0L;
                        }
                        case "getDoodadCoverAt" -> { coverReads++; yield 0; }
                        default -> throw new AssertionError("Unexpected/global query: " + method.getName());
                    });
        }

        long member(int x, int y) {
            long id = roster.spawn(new EntitySpec("member", Faction.MARINE, UnitType.MARINE, x, y).squad(squad.id));
            if (squad.aliveMembers++ == 0) squad.leaderId = id;
            return id;
        }

        long unit(Faction faction, UnitType type, int x, int y) {
            return roster.spawn(new EntitySpec("other", faction, type, x, y));
        }
    }
}
