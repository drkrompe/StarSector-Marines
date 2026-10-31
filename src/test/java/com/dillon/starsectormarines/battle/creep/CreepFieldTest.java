package com.dillon.starsectormarines.battle.creep;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreepFieldTest {

    private static final int W = 48, H = 24;
    private static final CreepGrowth FAST = new CreepGrowth(6f, 12f);

    @Test
    void spreadsAlongGroundAtItsRateAndStopsAtItsReach() {
        NavigationGrid grid = openGrid(W, H);
        CreepField field = new CreepField(grid, new CellTopology(W, H), 7L);
        field.putSource(1L, 10, 12, FAST);
        run(field, 0, 60);
        int early = covered(field);
        run(field, 60, 240);
        int late = covered(field);
        assertTrue(early > 0 && late > early, "creep grows outward over time: " + early + " -> " + late);
        assertTrue(field.isCreeped(10, 12), "the source cell is creeped");
        assertFalse(field.isCreeped(40, 12), "nothing past its reach");
        run(field, 240, 300);
        assertEquals(late, covered(field), "reach is capped, so a grown field stands still");
    }

    @Test
    void maturingGrowsTheWholeReachAtOnceAsTheClockWouldHave() {
        CreepField grown = new CreepField(openGrid(W, H), new CellTopology(W, H), 7L);
        grown.putSource(1L, 10, 12, FAST);
        run(grown, 0, 600);
        CreepField matured = new CreepField(openGrid(W, H), new CellTopology(W, H), 7L);
        matured.putSource(1L, 10, 12, FAST);
        matured.mature();
        assertEquals(covered(grown), covered(matured), "the same ground as a field left to grow out");
        assertEquals(CreepField.FULL, matured.coverageAt(14, 12), "and all of it full at once");
        assertFalse(matured.isCreeped(40, 12), "nothing past its reach");
    }

    @Test
    void creepSpeedsOnlyTheSwarmAndIsNoRefuge() {
        CreepField field = new CreepField(openGrid(W, H), new CellTopology(W, H), 7L);
        field.putSource(1L, 10, 12, FAST);
        run(field, 0, 60);
        assertEquals(CreepEffects.SWARM_SPEED_ON_CREEP, CreepEffects.groundSpeed(field, true, 10.5f, 12.5f));
        assertEquals(1f, CreepEffects.groundSpeed(field, false, 10.5f, 12.5f), "nobody else is slowed or sped");
        assertEquals(1f, CreepEffects.groundSpeed(field, true, 45.5f, 12.5f), "bare ground gives the swarm nothing");
        assertTrue(CreepEffects.refusedAsRefuge(field, 10, 12));
        assertFalse(CreepEffects.refusedAsRefuge(field, 45, 12));
        assertFalse(CreepEffects.refusedAsRefuge(null, 10, 12), "no field, no creep");
    }

    @Test
    void refusedGroundLosesItsCreepAndRegrowsOnceTheRefusalEnds() {
        CreepField field = new CreepField(openGrid(W, H), new CellTopology(W, H), 7L);
        field.putSource(1L, 10, 12, FAST);
        run(field, 0, 300);
        int burnt = 12 * W + 14;
        assertTrue(field.isCreeped(14, 12));
        boolean[] refused = {true};
        field.setRefusal(idx -> idx == burnt && refused[0]);
        field.scorch(burnt);
        run(field, 300, 400);
        assertEquals(0, field.coverageAt(burnt), "the scorched cell is bare");
        assertTrue(field.isCreeped(15, 12), "its neighbours are not");
        run(field, 400, 600);
        assertEquals(0, field.coverageAt(burnt), "and stays bare while refused");
        refused[0] = false;
        run(field, 600, 800);
        assertTrue(field.isCreeped(14, 12), "the source fills it again once the refusal ends");
    }

    @Test
    void heldGroundStartsClearAndIsRetakenSlowerThanAFreshSourceTakesIt() {
        CreepGrowth wide = new CreepGrowth(6f, 24f);
        IntPredicate held = idx -> idx % W >= 14;
        CreepField matured = new CreepField(openGrid(W, H), new CellTopology(W, H), 7L);
        matured.putSource(1L, 8, 12, wide);
        matured.mature(held);
        assertTrue(matured.isCreeped(12, 12), "the creep matures up to the held ground");
        assertEquals(0, creepedWhere(matured, held), "and grows none of it at setup");

        CreepField fresh = new CreepField(openGrid(W, H), new CellTopology(W, H), 7L);
        fresh.mature(held);
        fresh.putSource(1L, 8, 12, wide);
        run(matured, 1, 150);
        run(fresh, 1, 150);
        int retaken = creepedWhere(matured, held), taken = creepedWhere(fresh, held);
        assertTrue(retaken < taken, "a source matured round held ground retakes it slower than a later one takes it: "
                + retaken + " vs " + taken);

        CreepField unheld = new CreepField(openGrid(W, H), new CellTopology(W, H), 7L);
        unheld.putSource(1L, 8, 12, wide);
        unheld.mature();
        run(matured, 150, 4000);
        assertTrue(creepedWhere(matured, held) > 0, "held ground is retaken");
        assertEquals(creepedWhere(unheld, held), creepedWhere(matured, held), "as far as the source would have grown it");
        matured.removeSource(1L);
        run(matured, 4000, 9000);
        assertEquals(0, covered(matured), "and withers with its source");
    }

    @Test
    void sameSeedSameField() {
        CreepField a = new CreepField(openGrid(W, H), new CellTopology(W, H), 99L);
        CreepField b = new CreepField(openGrid(W, H), new CellTopology(W, H), 99L);
        for (CreepField f : new CreepField[]{a, b}) {
            f.putSource(1L, 8, 8, FAST);
            f.putSource(2L, 36, 16, FAST);
        }
        run(a, 0, 60);
        run(b, 0, 60);
        for (int i = 0; i < W * H; i++) {
            assertEquals(a.coverageAt(i), b.coverageAt(i));
            assertEquals(a.pulseAt(i), b.pulseAt(i));
        }
    }

    @Test
    void climbsTheWallItMeetsButDoesNotPassIt() {
        NavigationGrid grid = openGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            grid.setWalkable(20, y, false);
            topology.setWall(20, y, true);
        }
        CreepField field = new CreepField(grid, topology, 3L);
        field.putSource(1L, 16, 12, FAST);
        run(field, 0, 240);
        int wall = field.coverageAt(12 * W + 20), ground = field.coverageAt(12 * W + 19);
        assertTrue(wall > 0, "the wall beside the creep is climbed");
        assertTrue(wall < ground, "a wall fills slower than the ground, so its coverage is how long creep has held it");
        assertEquals(0, field.coverageAt(12 * W + 21), "the far side of an unbroken wall is never reached");
    }

    @Test
    void aFedSourceSpreadsFaster() {
        CreepField fed = new CreepField(openGrid(W, H), new CellTopology(W, H), 5L);
        CreepField starved = new CreepField(openGrid(W, H), new CellTopology(W, H), 5L);
        CreepGrowth slow = new CreepGrowth(1f, 40f);
        fed.putSource(1L, 24, 12, slow);
        starved.putSource(1L, 24, 12, slow);
        fed.feed(1L, 3f);
        run(fed, 0, 90);
        run(starved, 0, 90);
        float fedReach = fed.snapshot().sources().get(0).reachCells();
        float starvedReach = starved.snapshot().sources().get(0).reachCells();
        assertTrue(fedReach > starvedReach, fedReach + " vs " + starvedReach);
    }

    @Test
    void aRemovedSourceWithersFromTheEdgeBackToTheHole() {
        CreepField field = new CreepField(openGrid(W, H), new CellTopology(W, H), 11L);
        field.putSource(1L, 24, 12, FAST);
        run(field, 0, 200);
        int hole = 12 * W + 24;
        int edge = farthestCovered(field, 24, 12);
        field.removeSource(1L);
        int tick = 200;
        boolean edgeDiedFirst = false;
        while (tick < 2000 && field.coverageAt(hole) > 0) {
            field.tick(tick++);
            if (field.vitalityAt(edge) == 0 && field.vitalityAt(hole) == CreepField.FULL) edgeDiedFirst = true;
        }
        assertTrue(edgeDiedFirst, "the edge dies while the hole still lives");
        assertTrue(tick - 200 > 60, "dying back takes a visible interval, not one tick");
        run(field, tick, tick + 400);
        assertEquals(0, covered(field) + field.snapshot().dyingCells(), "everything it alone held is gone");
    }

    @Test
    void aLivingNeighbourKeepsSharedGroundAlive() {
        CreepField field = new CreepField(openGrid(W, H), new CellTopology(W, H), 13L);
        field.putSource(1L, 20, 12, FAST);
        field.putSource(2L, 24, 12, FAST);
        run(field, 0, 200);
        field.removeSource(1L);
        run(field, 200, 1200);
        assertTrue(field.isCreeped(22, 12), "ground the living source also reached stays creeped");
        assertTrue(field.isCreeped(20, 12), "including the dead hole, which the living one reaches");
    }

    @Test
    void changedTilesMoveTheirRevision() {
        CreepField field = new CreepField(openGrid(W, H), new CellTopology(W, H), 1L);
        assertEquals(0, field.revision());
        field.putSource(1L, 4, 4, FAST);
        field.tick(0);
        assertTrue(field.revision() > 0);
        assertTrue(field.tileRevision(0, 0) > 0, "the source's tile changed");
        assertEquals(0, field.tileRevision(1, 0), "a tile creep never reached did not");
    }

    /** A tree line is grown over to its far side; the same line of rock is only climbed, so nothing passes it. */
    @Test
    void growsOverDeclaredTreesButOnlyClimbsOtherSolids() {
        for (boolean trees : new boolean[]{true, false}) {
            NavigationGrid grid = openGrid(W, H);
            CellTopology topology = new CellTopology(W, H);
            for (int y = 0; y < H; y++) {
                grid.setWalkable(20, y, false);
                grid.setTag(20, y, NavigationGrid.CellTag.NATURAL_SOLID, true);
                topology.setGroundKind(20, y, CellTopology.GroundKind.GRASS);
            }
            CreepField field = new CreepField(grid, topology, 7L);
            if (trees) for (int y = 0; y < H; y++) field.growOver(y * W + 20);
            field.putSource(1L, 12, 12, new CreepGrowth(6f, 30f));
            run(field, 0, 900);
            assertTrue(field.coverageAt(20, 12) > 0, "the line itself is reached either way");
            assertEquals(trees, field.coverageAt(23, 12) > 0, trees ? "trees are grown over" : "rock is only climbed");
        }
    }

    private static void run(CreepField field, int from, int to) {
        for (int t = from; t < to; t++) field.tick(t);
    }

    private static int covered(CreepField field) { return field.snapshot().coveredCells(); }

    private static int creepedWhere(CreepField field, IntPredicate cells) {
        int n = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (cells.test(y * W + x) && field.isCreeped(x, y)) n++;
        return n;
    }

    private static int farthestCovered(CreepField field, int sx, int sy) {
        int best = -1, bestD = -1;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (!field.isCreeped(x, y)) continue;
                int d = Math.abs(x - sx) + Math.abs(y - sy);
                if (d > bestD) { bestD = d; best = y * W + x; }
            }
        }
        return best;
    }

    private static NavigationGrid openGrid(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        return grid;
    }
}
