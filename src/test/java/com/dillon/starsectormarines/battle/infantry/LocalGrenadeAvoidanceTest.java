package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tiny roster/proxy and pure geometry tests: no simulation loop, map generation, or navigation owner. */
class LocalGrenadeAvoidanceTest {
    @Test void sameInputLegacyControlSearchesTowardTheDistantGrenade() {
        try (Fixture f = new Fixture()) {
            f.grid.rejectComponents = false;
            int[] mission = {10, 10, 60, 10};
            f.path(mission);
            f.hazards.add(hazard(Faction.MARINE, 60.5f, 10.5f, 2f));
            assertTrue(FragGrenadeTactics.evadeKnownGrenade(f.unit, f.sim, false));
            assertEquals(16, f.profile.countOf(TickInnerProfile.Bucket.PATHFIND));
            assertEquals(1, f.paths);
            assertEquals(1, f.advances);
            assertNotSame(mission, f.roster.world().path(f.unit));

            f.path(mission);
            f.profile.reset();
            f.paths = f.advances = 0;
            f.grid.rejectComponents = true;
            assertFalse(f.evade());
            assertSame(mission, f.roster.world().path(f.unit));
            assertEquals(0, f.profile.countOf(TickInnerProfile.Bucket.PATHFIND));
            assertEquals(0, f.searches());
            assertEquals(0, f.paths);
            assertEquals(0, f.advances);
        }
    }

    @Test void futureRouteIntersectionBeyondFuseDoesNothing() {
        try (Fixture f = new Fixture()) {
            int[] mission = {10, 10, 60, 10};
            f.path(mission);
            f.hazards.add(hazard(Faction.MARINE, 60.5f, 10.5f, 2f));
            assertFalse(f.evade());
            assertSame(mission, f.roster.world().path(f.unit));
            assertEquals(0, f.paths);
            assertEquals(0, f.searches());
        }
    }

    @Test void nearPathThreatHoldsWithoutReplacingPathAndReleasesWithHeadingOrFuse() {
        try (Fixture f = new Fixture()) {
            int[] mission = {10, 10, 20, 10};
            f.path(mission);
            Projectile hazard = hazard(Faction.MARINE, 14.5f, 10.5f, 1f);
            f.hazards.add(hazard);
            assertTrue(f.evade());
            assertSame(mission, f.roster.world().path(f.unit));
            assertEquals(0, f.advances);
            assertEquals(0, f.paths);
            assertEquals(0, f.clears);
            assertEquals(0, f.searches());
            hazard.remainingTime = 0.1f;
            assertFalse(f.evade());
            hazard.remainingTime = 1f;
            f.path(new int[]{10, 10, 2, 10});
            assertFalse(f.evade());
        }
    }

    @Test void subTickFuseStillConsidersTheNextFullMovementStep() {
        try (Fixture f = new Fixture()) {
            f.path(new int[]{10, 10, 20, 10});
            Projectile hazard = hazard(Faction.MARINE, 12f, 10.5f, 1f);
            hazard.remainingTime = 0.001f;
            f.hazards.add(hazard);
            assertTrue(f.evade(), "one full movement tick would enter the safety radius");
            assertEquals(0, f.searches());
            assertEquals(0, f.advances);
        }
    }

    @Test void realDangerGetsOneLegalBoundedEscapeAndReusesIt() {
        try (Fixture f = new Fixture()) {
            f.hazards.add(hazard(Faction.MARINE, 10.5f, 10.5f, 2f));
            assertTrue(f.evade());
            int[] escape = f.roster.world().path(f.unit);
            assertLegal(f.grid, escape, false);
            float dx = Paths.destX(escape) - 10;
            float dy = Paths.destY(escape) - 10;
            assertTrue(dx * dx + dy * dy > 3.15f * 3.15f);
            assertEquals(1, f.searches());
            assertTrue(f.profile.countOf(TickInnerProfile.Bucket.GRENADE_ESCAPE_EXPANDED)
                    <= LocalGrenadeEscape.MAX_EXPANSIONS);
            assertTrue(f.evade());
            assertSame(escape, f.roster.world().path(f.unit));
            assertEquals(1, f.paths);
            assertEquals(1, f.searches());
            assertEquals(2, f.advances);
            assertEquals(1, f.profile.countOf(TickInnerProfile.Bucket.GRENADE_ESCAPE_REUSE));
        }
    }

    @Test void topologyHazardAndPathOwnershipChangesRetireTheWitness() {
        try (Fixture f = new Fixture()) {
            f.hazards.add(hazard(Faction.MARINE, 10.5f, 10.5f, 2f));
            f.evade();
            int[] original = f.roster.world().path(f.unit);
            f.grid.setWalkable(Paths.cellX(original, 1), Paths.cellY(original, 1), false);
            f.evade();
            assertEquals(2, f.searches());
            assertNotSame(original, f.roster.world().path(f.unit));
            assertLegal(f.grid, f.roster.world().path(f.unit), false);
            f.hazards.set(0, hazard(Faction.MARINE, 11.5f, 10.5f, 2f));
            f.evade();
            assertEquals(3, f.searches());
            int[] unrelated = {10, 10, 9, 10};
            f.path(unrelated);
            int clears = f.clears;
            f.evade();
            assertEquals(4, f.searches());
            assertEquals(clears, f.clears, "retiring an old witness never clears someone else's route");
            f.path(GridPathfinder.EMPTY_PATH);
            f.evade();
            assertEquals(5, f.searches());
        }
    }

    @Test void movingEscapeFinishesItsSafetyMarginBeforeHandingBack() {
        try (Fixture f = new Fixture()) {
            f.realMovement = true;
            Projectile hazard = hazard(Faction.MARINE, 10.5f, 10.5f, 2f);
            hazard.remainingTime = 10f;
            f.hazards.add(hazard);
            assertTrue(f.evade());
            int[] escape = f.roster.world().path(f.unit);
            int ticks = 0;
            while (f.distanceFrom(10.5f, 10.5f) <= 2.4f && ticks++ < 180) {
                assertTrue(f.evade());
            }
            double justOutside = f.distanceFrom(10.5f, 10.5f);
            assertTrue(justOutside > 2.4f && justOutside < 3.15f);
            assertTrue(f.evade(), "do not hand a barely-safe body straight back to mission travel");
            assertTrue(f.distanceFrom(10.5f, 10.5f) > justOutside);
            assertSame(escape, f.roster.world().path(f.unit));
            assertEquals(1, f.searches());
            while (f.roster.world().pathIdx(f.unit) < Paths.cellCount(escape) && ticks++ < 180) {
                assertTrue(f.evade());
            }
            assertTrue(ticks < 180, "the real mover must finish the retained route");
            assertFalse(f.evade());
            assertEquals(0, f.roster.world().path(f.unit).length);
            assertNull(f.roster.movement().grenadeEscape(f.unit));
            assertEquals(1, f.searches());
        }
    }

    @Test void expiryAndLostKnowledgeClearOnlyTheOwnedEscape() {
        try (Fixture f = new Fixture()) {
            Projectile hazard = hazard(Faction.MARINE, 10.5f, 10.5f, 2f);
            f.hazards.add(hazard);
            f.evade();
            hazard.remainingTime = 0f;
            assertFalse(f.evade());
            assertEquals(0, f.roster.world().path(f.unit).length);
            assertNull(f.roster.movement().grenadeEscape(f.unit));
            hazard.remainingTime = 1f;
            f.evade();
            int[] mission = {10, 10, 8, 10};
            f.path(mission);
            f.hazards.clear();
            int clears = f.clears;
            assertFalse(f.evade());
            assertSame(mission, f.roster.world().path(f.unit));
            assertEquals(clears, f.clears);
            f.hazards.add(hazard(Faction.DEFENDER, 10.5f, 10.5f, 2f));
            f.evade();
            assertNotNull(f.roster.movement().grenadeEscape(f.unit));
            f.grid.visible = false;
            assertFalse(f.evade());
            assertEquals(0, f.roster.world().path(f.unit).length);
        }
    }

    @Test void hiddenEnemyStillSuppliesNoWarningAndCurrentExposureOutranksPathThreat() {
        try (Fixture f = new Fixture()) {
            f.grid.visible = false;
            f.hazards.add(hazard(Faction.DEFENDER, 10.5f, 10.5f, 2f));
            assertFalse(f.evade());
            assertEquals(0, f.searches());
            f.hazards.clear();
            f.path(new int[]{10, 10, 20, 10});
            f.hazards.add(hazard(Faction.MARINE, 12.5f, 10.5f, 0.5f));
            f.hazards.add(hazard(Faction.MARINE, 10.5f, 13.5f, 4f));
            assertTrue(f.evade());
            assertEquals(1, f.searches(), "current exposure requires escape, not the nearer path-only hold");
        }
    }

    @Test void localSearchHonorsEdgesCornersModesAndHardWorkLimit() {
        TestGrid grid = new TestGrid(32, 32);
        grid.setSharedEdgePassable(10, 10, Direction.N, false);
        grid.setWalkable(11, 10, false);
        for (boolean cardinal : new boolean[]{false, true}) {
            LocalGrenadeEscape.Result result = LocalGrenadeEscape.find(grid, 10, 10,
                    10.5f, 10.5f, 3f, cardinal);
            assertLegal(grid, result.path(), cardinal);
            assertTrue(result.expanded() <= LocalGrenadeEscape.MAX_EXPANSIONS);
            assertFalse(Paths.cellX(result.path(), 1) == 11 && Paths.cellY(result.path(), 1) == 11,
                    "cannot cut the blocked northeast corner");
            LocalGrenadeEscape.Result capped = LocalGrenadeEscape.find(grid, 10, 10,
                    10.5f, 10.5f, 100f, cardinal);
            assertEquals(LocalGrenadeEscape.MAX_EXPANSIONS, capped.expanded());
            assertEquals(0, capped.path().length, "never publish a partial unsafe path");
            assertLegal(grid, LocalGrenadeEscape.find(grid, 10, 10,
                    10.5f, 10.5f, 2f, cardinal).path(), cardinal);
        }
    }

    @Test void duplicateVerticesCannotDefeatThePathWorkLimitAndSegmentsAreClipped() {
        int[] path = new int[(LocalGrenadeEscape.MAX_PATH_POINTS + 1) * 2];
        path[path.length - 2] = 20;
        assertFalse(LocalGrenadeEscape.pathThreatened(0.5f, 0.5f, path, 0,
                100f, 20.5f, 0.5f, 1f));
        assertFalse(LocalGrenadeEscape.pathThreatened(0.5f, 0.5f, new int[]{20, 0}, 0,
                2f, 10.5f, 0.5f, 1f));
        assertTrue(LocalGrenadeEscape.pathThreatened(0.5f, 0.5f, new int[]{20, 0}, 0,
                12f, 10.5f, 0.5f, 1f), "test the segment, not just its distant endpoint");
    }

    private static void assertLegal(NavigationGrid grid, int[] path, boolean cardinal) {
        assertTrue(path.length >= 4);
        for (int i = 1; i < Paths.cellCount(path); i++) {
            int x = Paths.cellX(path, i - 1);
            int y = Paths.cellY(path, i - 1);
            int nx = Paths.cellX(path, i);
            int ny = Paths.cellY(path, i);
            assertTrue(grid.canTraverseCellStep(x, y, nx, ny));
            if (cardinal) assertEquals(1, Math.abs(nx - x) + Math.abs(ny - y));
        }
    }

    private static Projectile hazard(Faction faction, float x, float y, float radius) {
        PendingDetonation payload = new PendingDetonation(0L, x, y, 1f, radius,
                32f, 2f, 0, faction, true);
        return new Projectile(10.5f, 10.5f, x, y, false, 1.8f, faction,
                true, 1f, payload, "weapon.frag-grenade");
    }

    private static final class TestGrid extends NavigationGrid {
        boolean visible = true;
        boolean rejectComponents = true;
        TestGrid(int width, int height) {
            super(width, height);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) setWalkableFloor(x, y);
        }
        @Override public boolean hasLineOfSight(int x0, int y0, int x1, int y1) { return visible; }
        @Override public boolean arePathConnected(int x0, int y0, int x1, int y1, boolean cardinal) {
            if (rejectComponents) throw new AssertionError("local escape must never build whole-map components");
            return super.arePathConnected(x0, y0, x1, y1, cardinal);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final TestGrid grid = new TestGrid(64, 24);
        final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(64, 24), null);
        final long unit;
        final List<Projectile> hazards = new ArrayList<>();
        final byte[] occupancy = new byte[64 * 24];
        final TickInnerProfile profile = new TickInnerProfile();
        final TickInnerProfile previous = TickInnerProfile.currentIfBound();
        final BattleControl sim;
        int paths;
        int clears;
        int advances;
        boolean realMovement;

        Fixture() {
            roster.setNavigationGrid(grid);
            unit = roster.spawn(new EntitySpec("observer", Faction.MARINE, UnitType.MARINE, 10, 10)
                    .moveSpeed(4f));
            sim = (BattleControl) Proxy.newProxyInstance(BattleControl.class.getClassLoader(),
                    new Class<?>[]{BattleControl.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getGrid" -> grid;
                        case "getOccupancyMap" -> occupancy;
                        case "world" -> roster.world();
                        case "identity" -> roster.identity();
                        case "movement" -> roster.movement();
                        case "vision" -> roster.vision();
                        case "squadOf" -> null;
                        case "resolveUnit" -> (long) args[0] == unit ? unit : 0L;
                        case "snapshotActiveProjectiles" -> hazards;
                        case "setPath" -> { paths++; path((int[]) args[1]); yield null; }
                        case "clearPath" -> { clears++; path(GridPathfinder.EMPTY_PATH); yield null; }
                        case "advanceMovement" -> {
                            advances++;
                            if (realMovement) roster.movement().advanceAlongPath(roster.world(), unit, 1f / 30f);
                            yield null;
                        }
                        default -> throw new AssertionError("Unexpected dependency: " + method.getName());
                    });
            TickInnerProfile.setCurrent(profile);
        }
        void path(int[] path) {
            roster.movement().setPathRef(unit, path);
            roster.movement().setPathIdx(unit, path.length > 2 ? 1 : 0);
        }
        boolean evade() { return FragGrenadeTactics.evadeKnownGrenade(unit, sim, true); }
        int searches() { return profile.countOf(TickInnerProfile.Bucket.GRENADE_ESCAPE_SEARCH); }
        double distanceFrom(float x, float y) {
            return Math.hypot(roster.world().x(unit) - x, roster.world().y(unit) - y);
        }
        @Override public void close() { TickInnerProfile.setCurrent(previous); }
    }
}
