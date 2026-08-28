package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link TacticalScoring}'s two pruned searches — the firing-position
 * picker and the target picker — against brute-force references that consider
 * every candidate in the order the searches originally used.
 *
 * <p>Both searches now skip candidates a score bound has already ruled out,
 * and both changed the order they visit candidates in to make that bound bite
 * early. Those are pruning moves, so the answer — including which of several
 * equal-scoring candidates wins the tie — must stay identical to the
 * exhaustive scan on every map. Randomized geometry is what makes that a claim
 * about the algorithms rather than about one hand-drawn arena.
 */
class TacticalSearchEquivalenceTest {

    private static final int WIDTH = 60;
    private static final int HEIGHT = 60;

    @Test
    void leashClampedSearchMatchesTheExhaustiveScan() {
        Random random = new Random(20260828L);
        int probes = 0;
        int foundPositions = 0;
        for (int map = 0; map < 12; map++) {
            BattleSimulation sim = randomArena(random);
            TacticalScoring scoring = sim.getTacticalScoring();
            for (int probe = 0; probe < 40; probe++) {
                long self = walkableUnit(sim, random, Faction.MARINE);
                long target = walkableUnit(sim, random, Faction.DEFENDER);
                int anchorX = sim.world().cellX(random.nextBoolean() ? self : target);
                int anchorY = sim.world().cellY(random.nextBoolean() ? self : target);
                float leash = 1f + random.nextInt(16) + random.nextFloat();

                int[] expected = referenceFiringPositionWithin(
                        sim, scoring, self, target, anchorX, anchorY, leash);
                int[] actual = scoring.findFiringPositionWithin(
                        self, target, anchorX, anchorY, leash);

                if (expected == null) {
                    assertNull(actual, "search found a cell the exhaustive scan rejected");
                } else {
                    assertArrayEquals(expected, actual,
                            "search and exhaustive scan disagree on the best firing cell");
                    foundPositions++;
                }
                probes++;
            }
        }
        assertTrue(foundPositions > probes / 2,
                "probes must mostly find a firing position, not agree on null; found "
                        + foundPositions + " of " + probes);
    }

    @Test
    void distanceOrderedTargetPickMatchesTheDenseOrderScan() {
        Random random = new Random(20260829L);
        int probes = 0;
        int found = 0;
        for (int map = 0; map < 12; map++) {
            BattleSimulation sim = randomArena(random);
            TacticalScoring scoring = sim.getTacticalScoring();
            for (int probe = 0; probe < 40; probe++) {
                long self = walkableUnit(sim, random, Faction.MARINE);
                float selfX = sim.world().x(self);
                float selfY = sim.world().y(self);
                boolean allowNoLos = random.nextBoolean();
                int squadId = Squad.NO_SQUAD;

                long expected = referenceBestTarget(sim, scoring, selfX, selfY,
                        Faction.MARINE, squadId, self, allowNoLos);
                long actual = scoring.findBestTarget(selfX, selfY, Faction.MARINE,
                        squadId, self, 0f, allowNoLos);

                assertEquals(expected, actual,
                        "distance-ordered pick and dense-order scan disagree on the target");
                if (expected != 0L) found++;
                probes++;
            }
        }
        assertTrue(found > probes / 2,
                "probes must mostly find a target, not agree on none; found "
                        + found + " of " + probes);
    }

    /**
     * The target picker exactly as it read before the distance ordering: one
     * dense-order pass, a line-of-sight raycast for every hostile combatant in
     * the roster, first candidate wins a tie.
     */
    private static long referenceBestTarget(
            BattleSimulation sim, TacticalScoring scoring, float selfX, float selfY,
            Faction selfFaction, int selfSquadId, long exclude, boolean allowNoLos) {
        NavigationGrid grid = sim.getGrid();
        long[] dense = sim.getRoster().denseArray();
        int liveCount = sim.getRoster().liveCount();
        int selfCellX = (int) Math.floor(selfX);
        int selfCellY = (int) Math.floor(selfY);

        long best = 0L;
        float bestScore = Float.MAX_VALUE;
        long bestAny = 0L;
        float bestAnyDist = Float.MAX_VALUE;
        for (int i = 0; i < liveCount; i++) {
            long other = dense[i];
            if (sim.getRoster().identity().faction(other) == selfFaction) continue;
            if (!sim.getRoster().identity().type(other).combatant) continue;

            int ox = sim.world().cellX(other);
            int oy = sim.world().cellY(other);
            float d = TacticalScoring.cellDistance(
                    selfX, selfY, sim.world().x(other), sim.world().y(other));
            if (d < bestAnyDist) {
                bestAnyDist = d;
                bestAny = other;
            }
            boolean visible = TacticalScoring.canSeePair(grid, selfCellX, selfCellY,
                    ox, oy, 0f, sim.vision().airLosRadius(other));
            if (!visible && !allowNoLos) continue;
            float score = scoring.scoreTargetCandidate(other, d, visible, selfFaction,
                    selfSquadId, exclude, selfCellX, selfCellY, ox, oy);
            if (score < bestScore) {
                bestScore = score;
                best = other;
            }
        }
        return best != 0L ? best : bestAny;
    }

    /**
     * The search exactly as it read before the leash clamp and score-bound
     * prune: every cell of the target's range box, filters in their original
     * order, first cell wins a tie.
     */
    private static int[] referenceFiringPositionWithin(
            BattleSimulation sim, TacticalScoring scoring, long self, long target,
            int anchorX, int anchorY, float maxDistFromAnchor) {
        NavigationGrid grid = sim.getGrid();
        int tx = sim.world().cellX(target);
        int ty = sim.world().cellY(target);
        int sx = sim.world().cellX(self);
        int sy = sim.world().cellY(self);
        float selfAir = sim.vision().airLosRadius(self);
        float targetAir = sim.vision().targetAirLosRadius(target);
        float effectiveRange = scoring.effectiveAttackRange(
                self, target, sim.world().attackRange(self));
        int range = Math.max(1, (int) Math.floor(effectiveRange));

        int[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (int dy = -range; dy <= range; dy++) {
            for (int dx = -range; dx <= range; dx++) {
                int cx = tx + dx;
                int cy = ty + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;

                float distFromTarget = (float) Math.sqrt(dx * dx + dy * dy);
                if (distFromTarget > effectiveRange) continue;
                if (distFromTarget < TacticalScoring.FIRING_MIN_DISTANCE) continue;
                if (!TacticalScoring.canShootPair(grid, cx + 0.5f, cy + 0.5f,
                        sim.world().x(target), sim.world().y(target),
                        selfAir, targetAir)) continue;
                if (TacticalScoring.cellDistance(anchorX, anchorY, cx, cy)
                        > maxDistFromAnchor) continue;

                int occupants = scoring.occupantsExcludingSelf(self, sx, sy, cx, cy);
                int alliesNear = scoring.alliesNearForSpread(self, cx, cy);
                int fdx = tx - cx;
                int fdy = ty - cy;
                float score = TacticalScoring.cellDistance(sx, sy, cx, cy)
                        + TacticalScoring.FIRING_OCCUPANCY_COST * occupants
                        + TacticalScoring.FIRING_AOE_SPREAD_COST * alliesNear
                        - TacticalScoring.FIRING_COVER_BONUS
                                * grid.getCoverAt(cx, cy, fdx, fdy)
                        - TacticalScoring.FIRING_DOODAD_COVER_BONUS
                                * sim.getDoodadCoverAt(cx, cy, fdx, fdy);
                if (score < bestScore) {
                    bestScore = score;
                    best = new int[]{cx, cy};
                }
            }
        }
        return best;
    }

    /**
     * Open floor with scattered wall blocks, per-facing wall cover, and cover
     * doodads — enough broken geometry that line of fire, cover and crowding
     * all vary across the scan box.
     */
    private static BattleSimulation randomArena(Random random) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        // Cells are wall until floored, so the blocks are stamped as holes in
        // the floor pass rather than punched back out afterwards.
        boolean[] wall = new boolean[WIDTH * HEIGHT];
        for (int block = 0; block < 40; block++) {
            int x = 1 + random.nextInt(WIDTH - 4);
            int y = 1 + random.nextInt(HEIGHT - 4);
            int w = 1 + random.nextInt(3);
            int h = 1 + random.nextInt(3);
            for (int by = y; by < y + h; by++) {
                for (int bx = x; bx < x + w; bx++) {
                    wall[by * WIDTH + bx] = true;
                }
            }
        }
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (!wall[y * WIDTH + x]) grid.setWalkableFloor(x, y);
            }
        }
        BattleSimulation sim = new BattleSimulation(grid, topology);
        for (int i = 0; i < 120; i++) {
            int x = random.nextInt(WIDTH);
            int y = random.nextInt(HEIGHT);
            if (!grid.isWalkable(x, y)) continue;
            for (int facing = 0; facing < NavigationGrid.FACING_COUNT; facing++) {
                if (random.nextBoolean()) continue;
                grid.setCoverAtFacing(x, y, facing,
                        1 + random.nextInt(NavigationGrid.MAX_COVER));
            }
        }
        for (int i = 0; i < 60; i++) {
            int x = random.nextInt(WIDTH);
            int y = random.nextInt(HEIGHT);
            if (!grid.isWalkable(x, y)) continue;
            sim.addDoodad(new Doodad(x, y, new TileManifest.TileFrame(8, 1), false,
                    1 + random.nextInt(NavigationGrid.MAX_COVER)));
        }
        // Crowd the map so the occupancy and AoE-spread terms actually fire.
        for (int i = 0; i < 50; i++) {
            walkableUnit(sim, random,
                    random.nextBoolean() ? Faction.MARINE : Faction.DEFENDER);
        }
        return sim;
    }

    private static long walkableUnit(BattleSimulation sim, Random random, Faction faction) {
        NavigationGrid grid = sim.getGrid();
        while (true) {
            int x = random.nextInt(WIDTH);
            int y = random.nextInt(HEIGHT);
            if (!grid.isWalkable(x, y)) continue;
            return sim.spawn(new EntitySpec(
                    "u" + sim.liveUnitCount(), faction, UnitType.MARINE, x, y));
        }
    }
}
