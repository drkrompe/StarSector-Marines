package com.dillon.starsectormarines.battle.command.compound;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Slow-tick consumer that drives the compound capture state machine. Each
 * cadence period it samples per-compound zone occupancy via
 * {@link ZoneQueries#zoneClear} and writes the resulting state /
 * hold-timer / capture-progress back to {@link CompoundService}.
 *
 * <p>Cadence mirrors
 * {@link com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementService}
 * — 1 Hz. Capture is inherently slow (a few seconds of "hold the room");
 * the slower poll keeps the per-compound {@code zoneClear} scan off the
 * per-frame hot path. The system is stateless w.r.t. game state — the
 * accumulator field is pure tick-pacing plumbing, same shape as the
 * accumulator in {@link com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementService}.
 *
 * <p>The same state machine handles capture and recapture. A defender
 * re-entering a marine-held compound returns it to {@code CONTESTED}; the
 * existing defender reinforcement path can then drive it back to
 * {@code DEFENDER_HELD} without a state-machine rewrite.
 */
public final class CompoundCaptureSystem {

    /** Sim-seconds between capture-state evaluations. Same cadence shape as {@link com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementService#REINFORCEMENT_TICK_PERIOD} so the two layers reach the same compound state within at most a tick of each other. */
    public static final float CAPTURE_TICK_PERIOD = 1.0f;

    private float accumulator = 0f;

    /**
     * Advance the capture state machine. Accumulates {@code dt} and only
     * walks the record list when the cadence period elapses; per-cell
     * occupancy reads (via {@link ZoneQueries#zoneClear}) iterate the live
     * unit list once per compound per slow tick.
     */
    public void tick(float dt, BattleView sim, CompoundService service) {
        if (service == null || service.getRecords().isEmpty()) return;
        accumulator += dt;
        if (accumulator < CAPTURE_TICK_PERIOD) return;
        accumulator -= CAPTURE_TICK_PERIOD;

        ZoneGraph zones = sim.getZoneGraph();
        for (CompoundService.Record r : service.getRecords()) {
            int zoneId = captureZone(r, sim, zones);
            // The compound's footprint holds no zone at all — a degenerate
            // carve with no walkable interior. Skip this tick; the compound
            // state holds.
            if (zoneId < 0) continue;

            boolean defendersPresent = !ZoneQueries.zoneClear(zoneId, Faction.DEFENDER, sim);
            boolean marinesPresent   = !ZoneQueries.zoneClear(zoneId, Faction.MARINE, sim);

            switch (r.state) {
                case DEFENDER_HELD -> {
                    // First marine inside the zone flips to CONTESTED. Empty or
                    // defender-only zones stay DEFENDER_HELD with no progress.
                    if (marinesPresent) {
                        r.state = CompoundService.CompoundState.CONTESTED;
                        r.holdTimer = 0f;
                        r.captureProgress = 0f;
                    }
                }
                case CONTESTED -> {
                    if (marinesPresent && !defendersPresent) {
                        r.holdTimer += CAPTURE_TICK_PERIOD;
                        r.captureProgress = Math.min(1f,
                                r.holdTimer / CompoundService.MARINE_HOLD_TIME);
                        if (r.holdTimer >= CompoundService.MARINE_HOLD_TIME) {
                            r.state = CompoundService.CompoundState.MARINE_HELD;
                            r.holdTimer = 0f;
                            // Terminal-state progress is 0, not 1 — captureProgress
                            // models *in-flight* transition fill, not a "captured"
                            // marker. Renderer gates the capture arc on
                            // {@code captureProgress > 0}; leaving 1 here paints
                            // the arc forever inside the marine-blue ring.
                            r.captureProgress = 0f;
                        }
                    } else if (defendersPresent && !marinesPresent) {
                        // Symmetric recovery: defenders alone in a contested
                        // zone push it back to DEFENDER_HELD during capture or recapture.
                        r.holdTimer += CAPTURE_TICK_PERIOD;
                        r.captureProgress = Math.min(1f,
                                r.holdTimer / CompoundService.DEFENDER_HOLD_TIME);
                        if (r.holdTimer >= CompoundService.DEFENDER_HOLD_TIME) {
                            r.state = CompoundService.CompoundState.DEFENDER_HELD;
                            r.holdTimer = 0f;
                            r.captureProgress = 0f;
                        }
                    }
                    // Mixed (both sides) or empty (both sides briefly vacated):
                    // pause the timer without resetting. A brief firefight that
                    // clears either way resumes from where it paused — the
                    // capture-progress arc freezes mid-fill, which reads
                    // naturally.
                }
                case MARINE_HELD -> {
                    // Defender re-entry flips MARINE_HELD → CONTESTED and
                    // the CONTESTED branch above accumulates toward
                    // DEFENDER_HELD. This is the recapture path; no separate
                    // state-machine variant is needed for a garrison cycle.
                    if (defendersPresent) {
                        r.state = CompoundService.CompoundState.CONTESTED;
                        r.holdTimer = 0f;
                        r.captureProgress = 0f;
                    }
                }
            }
        }
    }

    /**
     * Zone the compound is captured in, resolving and caching the capture cell
     * on first use.
     *
     * <p>The node anchor is deliberately <em>not</em> assumed to be walkable:
     * {@link com.dillon.starsectormarines.battle.decision.TacticalNode} defines
     * the anchor as the place's stable identity, free to sit on a wall, a
     * turret mount, or a cell a furnishing pass later blocked. Reading a zone
     * straight off such an anchor yields {@code -1} every tick, which would
     * leave the compound permanently uncapturable and — on Conquest, where
     * every compound must flip — the mission unwinnable. So resolve outward
     * from the anchor to the nearest walkable cell inside the compound's own
     * building footprint and capture in that cell's room instead.
     *
     * <p>The resolved cell is cached because it stays valid: breaching a wall
     * only ever opens cells, so a cell that was walkable remains walkable, and
     * re-reading its zone each tick picks up any merge the breach caused.
     */
    private static int captureZone(CompoundService.Record r, BattleView sim,
                                   ZoneGraph zones) {
        if (r.captureCellX < 0) {
            int[] cell = resolveCaptureCell(r.node, sim.getGrid(), zones);
            if (cell == null) return -1;
            r.captureCellX = cell[0];
            r.captureCellY = cell[1];
        }
        return zones.zoneIdAt(r.captureCellX, r.captureCellY);
    }

    /**
     * Nearest cell to the node anchor that belongs to a zone, searched breadth
     * first and bounded to the node's own building bbox so a compound never
     * captures in a neighbour's room or out on the parade ground. Returns
     * {@code null} when the footprint holds no zoned cell at all.
     */
    private static int[] resolveCaptureCell(TacticalNode node, NavigationGrid grid,
                                            ZoneGraph zones) {
        int left = Math.max(0, node.left);
        int top = Math.max(0, node.top);
        int right = Math.min(grid.getWidth() - 1, node.right);
        int bottom = Math.min(grid.getHeight() - 1, node.bottom);
        if (left > right || top > bottom) return null;
        int width = right - left + 1;
        boolean[] visited = new boolean[width * (bottom - top + 1)];
        Deque<int[]> queue = new ArrayDeque<>();
        int startX = Math.min(right, Math.max(left, node.anchorX));
        int startY = Math.min(bottom, Math.max(top, node.anchorY));
        queue.add(new int[]{startX, startY});
        visited[(startY - top) * width + (startX - left)] = true;
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            if (zones.zoneIdAt(cell[0], cell[1]) >= 0) return cell;
            for (int[] step : NEIGHBOURS) {
                int nx = cell[0] + step[0];
                int ny = cell[1] + step[1];
                if (nx < left || nx > right || ny < top || ny > bottom) continue;
                int index = (ny - top) * width + (nx - left);
                if (visited[index]) continue;
                visited[index] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return null;
    }

    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
}
