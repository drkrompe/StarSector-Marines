package com.dillon.starsectormarines.battle.command.compound;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.FactionUnitRoster;
import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spawns a marine-faction garrison shuttle when a compound transitions to
 * {@link CompoundService.CompoundState#MARINE_HELD}. The garrison troops
 * hold the captured compound while the assault force pushes on; existing
 * defender reinforcement naturally counter-attacks, creating the tug-of-war
 * dynamic.
 *
 * <p>Garrison drops are free (no {@code BattleResources} ticket cost) — the
 * strategic cost is the troops themselves: they're tied down holding, not
 * advancing. The shuttle delivers infantry-tier marines (not elite) so the
 * garrison is a defensive holding force, not a second assault team.
 *
 * <p>Per-compound dispatch re-arms when the compound returns to
 * {@link CompoundService.CompoundState#DEFENDER_HELD}, allowing the cycle
 * to repeat if marines recapture. Same 1 Hz slow-tick cadence as
 * {@link CompoundCaptureSystem} so both systems see consistent state.
 */
public final class CompoundGarrisonSystem {

    private static final Logger LOG = Global.getLogger(CompoundGarrisonSystem.class);

    private static final float TICK_PERIOD = CompoundCaptureSystem.CAPTURE_TICK_PERIOD;
    private static final int LZ_SCAN_RADIUS = 8;
    private static final float OFFMAP_PAD = 8f;
    private static final ShuttleType SHUTTLE_TYPE = ShuttleType.AEROSHUTTLE;

    private enum GarrisonState { NONE, DISPATCHED, RE_ARMED }

    private final TraversalAxis axis;
    private final Map<TacticalNode, GarrisonState> dispatch = new IdentityHashMap<>();
    private float accumulator = 0f;

    public CompoundGarrisonSystem(TraversalAxis axis) {
        this.axis = axis;
    }

    public void tick(float dt, BattleControl sim, CompoundService service) {
        if (service == null || service.getRecords().isEmpty()) return;
        accumulator += dt;
        if (accumulator < TICK_PERIOD) return;
        accumulator -= TICK_PERIOD;

        for (CompoundService.Record r : service.getRecords()) {
            GarrisonState gs = dispatch.getOrDefault(r.node, GarrisonState.NONE);

            if (r.state == CompoundService.CompoundState.MARINE_HELD
                    && (gs == GarrisonState.NONE || gs == GarrisonState.RE_ARMED)) {
                if (spawnGarrisonShuttle(sim, r.node)) {
                    dispatch.put(r.node, GarrisonState.DISPATCHED);
                }
            } else if (r.state == CompoundService.CompoundState.DEFENDER_HELD
                    && gs == GarrisonState.DISPATCHED) {
                dispatch.put(r.node, GarrisonState.RE_ARMED);
            }
        }
    }

    private boolean spawnGarrisonShuttle(BattleControl sim, TacticalNode node) {
        NavigationGrid grid = sim.getGrid();
        int[] lz = findCompoundLz(grid, node);
        if (lz == null) {
            LOG.warn("CompoundGarrisonSystem: no LZ found for compound "
                    + node.kind + " at (" + node.anchorX + "," + node.anchorY + ")");
            return false;
        }

        float lzX = lz[0] + 0.5f;
        float lzY = lz[1] + 0.5f;
        float[] entry = entryForMarine(lzX, lzY, grid.getWidth(), grid.getHeight());

        Shuttle shuttle = new Shuttle(
                SHUTTLE_TYPE, Faction.MARINE,
                lzX, lzY,
                entry[0], entry[1],
                entry[2], entry[3],
                0f);
        shuttle.mission.totalCycles = 1;
        shuttle.mission.deboardUnitType = FactionUnitRoster.forFaction(Faction.MARINE).infantry();
        // The deboarded squad is born holding this compound (HOLD_NODE → the
        // GarrisonCompound behavior), so it garrisons without the commander
        // pinning whichever assault squad captured the place.
        shuttle.mission.garrisonNode = node;
        sim.addShuttle(shuttle);
        LOG.info("CompoundGarrisonSystem: garrison shuttle dispatched to compound "
                + node.kind + " lz=(" + lz[0] + "," + lz[1] + ")");
        return true;
    }

    /**
     * Progressive LZ search:
     * <ol>
     *   <li>Parade ground — BFS from the building bbox exterior rim for a
     *       3x3 clear patch. The 1-cell yard between the building shell and
     *       the compound perimeter wall is ideal open ground.</li>
     *   <li>Gate cells — scan the bbox perimeter for doorway-flagged cells
     *       (compound gates punched by {@code MilitaryBaseFiller}), BFS from
     *       those for a 3x3 patch.</li>
     *   <li>Fallback — simple BFS from the anchor for any single walkable
     *       cell. Tight spaces are better than no drop.</li>
     * </ol>
     */
    static int[] findCompoundLz(NavigationGrid grid, TacticalNode node) {
        // 1. Parade-ground scan: seed BFS from walkable cells just outside
        //    the building bbox (the 1-cell rim MilitaryBaseFiller paints as
        //    STONE parade ground).
        List<int[]> perimeterSeeds = collectBboxExterior(grid, node);
        int[] lz = bfsFor3x3(grid, perimeterSeeds, LZ_SCAN_RADIUS);
        if (lz != null) return lz;

        // 2. Gate fallback: doorway cells near the compound bbox.
        List<int[]> gateSeeds = collectGateCells(grid, node);
        lz = bfsFor3x3(grid, gateSeeds, LZ_SCAN_RADIUS);
        if (lz != null) return lz;

        // 3. Last resort: any single walkable cell near the anchor.
        return bfsForWalkable(grid, node.anchorX, node.anchorY, LZ_SCAN_RADIUS);
    }

    private static List<int[]> collectBboxExterior(NavigationGrid grid, TacticalNode node) {
        List<int[]> seeds = new ArrayList<>();
        int expand = 1;
        int l = node.left - expand, t = node.top - expand;
        int r = node.right + expand, b = node.bottom + expand;
        for (int x = l; x <= r; x++) {
            addIfWalkable(grid, seeds, x, t);
            addIfWalkable(grid, seeds, x, b);
        }
        for (int y = t + 1; y < b; y++) {
            addIfWalkable(grid, seeds, l, y);
            addIfWalkable(grid, seeds, r, y);
        }
        return seeds;
    }

    private static List<int[]> collectGateCells(NavigationGrid grid, TacticalNode node) {
        List<int[]> seeds = new ArrayList<>();
        int scanMargin = 2;
        int l = node.left - scanMargin, t = node.top - scanMargin;
        int r = node.right + scanMargin, b = node.bottom + scanMargin;
        for (int y = t; y <= b; y++) {
            for (int x = l; x <= r; x++) {
                if (!grid.inBounds(x, y)) continue;
                if (grid.isDoorway(x, y) && grid.isWalkable(x, y)) {
                    seeds.add(new int[]{x, y});
                }
            }
        }
        return seeds;
    }

    private static void addIfWalkable(NavigationGrid grid, List<int[]> out, int x, int y) {
        if (grid.inBounds(x, y) && grid.isWalkable(x, y)) {
            out.add(new int[]{x, y});
        }
    }

    private static boolean is3x3Clear(NavigationGrid grid, int cx, int cy) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = cx + dx, ny = cy + dy;
                if (!grid.inBounds(nx, ny) || !grid.isWalkable(nx, ny)) return false;
            }
        }
        return true;
    }

    private static int[] bfsFor3x3(NavigationGrid grid, List<int[]> seeds, int maxRadius) {
        if (seeds.isEmpty()) return null;
        Set<Long> seen = new HashSet<>();
        Deque<int[]> q = new ArrayDeque<>();
        for (int[] s : seeds) {
            long k = ((long) s[0] << 32) | (s[1] & 0xFFFFFFFFL);
            if (seen.add(k)) q.add(new int[]{s[0], s[1], 0});
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[2] > maxRadius) continue;
            if (grid.isWalkable(p[0], p[1]) && is3x3Clear(grid, p[0], p[1])) {
                return new int[]{p[0], p[1]};
            }
            for (int[] d : dirs) {
                int nx = p[0] + d[0], ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                long k = ((long) nx << 32) | (ny & 0xFFFFFFFFL);
                if (!seen.add(k)) continue;
                q.add(new int[]{nx, ny, p[2] + 1});
            }
        }
        return null;
    }

    private static int[] bfsForWalkable(NavigationGrid grid, int startX, int startY, int maxRadius) {
        if (grid.inBounds(startX, startY) && grid.isWalkable(startX, startY)) {
            return new int[]{startX, startY};
        }
        Set<Long> seen = new HashSet<>();
        Deque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{startX, startY, 0});
        seen.add(((long) startX << 32) | (startY & 0xFFFFFFFFL));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[2] > maxRadius) continue;
            if (grid.inBounds(p[0], p[1]) && grid.isWalkable(p[0], p[1])) {
                return new int[]{p[0], p[1]};
            }
            for (int[] d : dirs) {
                int nx = p[0] + d[0], ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                long k = ((long) nx << 32) | (ny & 0xFFFFFFFFL);
                if (!seen.add(k)) continue;
                q.add(new int[]{nx, ny, p[2] + 1});
            }
        }
        return null;
    }

    private float[] entryForMarine(float lzX, float lzY, int gridW, int gridH) {
        if (axis == TraversalAxis.SOUTH_TO_NORTH) {
            return new float[]{lzX, -OFFMAP_PAD, lzX, -OFFMAP_PAD - 4f};
        }
        if (axis == TraversalAxis.WEST_TO_EAST) {
            return new float[]{-OFFMAP_PAD, lzY, -OFFMAP_PAD - 4f, lzY};
        }
        return new float[]{lzX, -OFFMAP_PAD, lzX, -OFFMAP_PAD - 4f};
    }
}
