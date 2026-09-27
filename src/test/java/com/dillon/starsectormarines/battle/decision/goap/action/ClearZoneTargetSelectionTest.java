package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Direct selector tests on a small roster and grid, without a battle simulation. */
class ClearZoneTargetSelectionTest {
    @Test
    void nearerVisibleIncumbentPrunesEveryFartherRay() {
        Fixture f = new Fixture();
        long nearest = f.enemy(3, 2);
        for (int x = 4; x <= 20; x++) f.enemy(x, 2);

        assertEquals(nearest, f.pick(true));
        assertEquals(1, f.grid.rays);
        assertEquals(nearest, f.pick(false));
        assertEquals(18, f.grid.rays, "control really casts every candidate's ray");
    }

    @Test
    void nearerOccludedEnemyDoesNotHideAFartherVisibleOne() {
        Fixture f = new Fixture();
        f.enemy(3, 2);
        long visible = f.enemy(8, 2);
        f.grid.hidden.add(f.grid.index(3, 2));
        assertEquals(visible, f.pick(true));
        assertEquals(2, f.grid.rays);
        assertEquals(visible, f.pick(false));
    }

    @Test
    void noVisibleEnemyUsesSameNearestFallbackWithoutASecondRosterPass() {
        Fixture f = new Fixture();
        f.enemy(8, 2);
        long nearest = f.enemy(3, 2);
        f.grid.hidden.add(f.grid.index(8, 2));
        f.grid.hidden.add(f.grid.index(3, 2));
        assertEquals(nearest, f.pick(true));
        assertEquals(f.roster.liveCount(), f.visits);
        assertEquals(nearest, f.pick(false));
        assertEquals(2 * f.roster.liveCount(), f.visits);
    }

    @Test
    void visibleAndHiddenDistanceTiesKeepDenseRosterOrder() {
        Fixture f = new Fixture();
        long first = f.enemy(3, 2);
        f.enemy(2, 3);
        assertEquals(first, f.pick(true));
        assertEquals(first, f.pick(false));
        f.grid.hidden.add(f.grid.index(3, 2));
        f.grid.hidden.add(f.grid.index(2, 3));
        assertEquals(first, f.pick(true));
        assertEquals(first, f.pick(false));
    }

    @Test
    void nearerCandidateLaterInRosterStillWins() {
        Fixture f = new Fixture();
        f.enemy(10, 2);
        long nearer = f.enemy(5, 2);
        f.enemy(12, 2);
        assertEquals(nearer, f.pick(true));
        assertEquals(2, f.grid.rays);
        assertEquals(nearer, f.pick(false));
    }

    @Test
    void zoneHostilityAndCombatantFiltersRemainExact() {
        Fixture f = new Fixture();
        for (int y = 0; y < 6; y++) f.grid.setWalkable(12, y, false);
        f.grid.setWalkableFloor(12, 2);
        f.grid.setDoorway(12, 2, true);
        f.zones.rebuild();
        f.unit(Faction.ALLY, UnitType.MARINE, 3, 2);
        f.unit(Faction.DEFENDER, UnitType.CIVILIAN, 4, 2);
        f.enemy(14, 2);
        assertEquals(0L, f.pick(true));
        assertEquals(0, f.grid.rays);
        assertEquals(0L, f.pick(false));
        long inZone = f.enemy(10, 2);
        assertEquals(inZone, f.pick(true));
        assertEquals(inZone, f.pick(false));
    }

    @Test
    void noSightRadiusOrSpatialSnapshotCapDropsFarOrMovedEnemies() {
        Fixture f = new Fixture();
        long target = f.enemy(20, 2);
        f.roster.world().setPos(target, 40.5f, 2.5f);
        assertEquals(target, f.pick(true));
        assertEquals(target, f.pick(false));
    }

    private static final class CountingGrid extends NavigationGrid {
        final Set<Integer> hidden = new HashSet<>();
        int rays;

        CountingGrid() {
            super(50, 6);
            for (int y = 0; y < 6; y++) {
                for (int x = 0; x < 50; x++) setWalkableFloor(x, y);
            }
        }

        @Override public boolean hasLineOfSight(int x0, int y0, int x1, int y1) {
            rays++;
            return !hidden.contains(index(x1, y1));
        }
    }

    private static final class Fixture {
        final CountingGrid grid = new CountingGrid();
        final ZoneGraph zones = new ZoneGraph(grid);
        final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(50, 6), null);
        final long self;
        final BattleView view;
        int visits;

        Fixture() {
            zones.rebuild();
            self = unit(Faction.MARINE, UnitType.MARINE, 2, 2);
            view = (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),
                    new Class<?>[]{BattleView.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getGrid" -> grid;
                        case "getZoneGraph" -> zones;
                        case "world" -> roster.world();
                        case "identity" -> roster.identity();
                        case "liveUnitCount" -> roster.liveCount();
                        case "liveUnitAt" -> { visits++; yield roster.get((int) args[0]); }
                        default -> throw new AssertionError("Unexpected query: " + method.getName());
                    });
        }

        long enemy(int x, int y) { return unit(Faction.DEFENDER, UnitType.MARINE, x, y); }

        long unit(Faction faction, UnitType type, int x, int y) {
            return roster.spawn(new EntitySpec("unit", faction, type, x, y));
        }

        long pick(boolean pruned) {
            String property = "battle.targeting.pruneClearZoneSelection";
            String previous = System.getProperty(property);
            System.setProperty(property, Boolean.toString(pruned));
            try {
                ClearZone action = new ClearZone(zones.zoneIdAt(2, 2));
                grid.rays = 0;
                visits = 0;
                return action.pickZoneTarget(self, view);
            } finally {
                if (previous == null) System.clearProperty(property);
                else System.setProperty(property, previous);
            }
        }
    }
}
