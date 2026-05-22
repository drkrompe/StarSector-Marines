package com.dillon.starsectormarines.battle.compound;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.ai.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;

/**
 * Slow-tick consumer that drives the compound capture state machine. Each
 * cadence period it samples per-compound zone occupancy via
 * {@link ZoneQueries#zoneClear} and writes the resulting state /
 * hold-timer / capture-progress back to {@link CompoundService}.
 *
 * <p>Cadence mirrors
 * {@link com.dillon.starsectormarines.battle.reinforcement.ReinforcementService}
 * — 1 Hz. Capture is inherently slow (a few seconds of "hold the room");
 * the slower poll keeps the per-compound {@code zoneClear} scan off the
 * per-frame hot path. The system is stateless w.r.t. game state — the
 * accumulator field is pure tick-pacing plumbing, same shape as the
 * accumulator in {@link com.dillon.starsectormarines.battle.reinforcement.ReinforcementService}.
 *
 * <p>V1 ships forward transitions only — the MARINE_HELD &rarr; CONTESTED
 * branch is wired but dormant because no v1 production driver pushes
 * defenders into a marine-held zone. V2's {@code AutoGarrisonTrigger}
 * shuttle-drops defenders into recently-captured compounds and lights the
 * branch up without any state-machine changes here.
 */
public final class CompoundCaptureSystem {

    /** Sim-seconds between capture-state evaluations. Same cadence shape as {@link com.dillon.starsectormarines.battle.reinforcement.ReinforcementService#REINFORCEMENT_TICK_PERIOD} so the two layers reach the same compound state within at most a tick of each other. */
    public static final float CAPTURE_TICK_PERIOD = 1.0f;

    private float accumulator = 0f;

    /**
     * Advance the capture state machine. Accumulates {@code dt} and only
     * walks the record list when the cadence period elapses; per-cell
     * occupancy reads (via {@link ZoneQueries#zoneClear}) iterate the live
     * unit list once per compound per slow tick.
     */
    public void tick(float dt, BattleSimulation sim, CompoundService service) {
        if (service == null || service.getRecords().isEmpty()) return;
        accumulator += dt;
        if (accumulator < CAPTURE_TICK_PERIOD) return;
        accumulator -= CAPTURE_TICK_PERIOD;

        ZoneGraph zones = sim.getZoneGraph();
        for (CompoundService.Record r : service.getRecords()) {
            int zoneId = zones.zoneIdAt(r.node.anchorX, r.node.anchorY);
            // Anchor on a wall cell — rare (the BSP generator places compound
            // anchors at interior cells), but a wall collapse can shift
            // topology. Skip this tick; the compound state holds.
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
                        // zone push it back to DEFENDER_HELD. Fires in v1 as
                        // the natural "marines started the capture and got
                        // pushed off" path.
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
                    // V2 reverse path: defender re-entry flips MARINE_HELD →
                    // CONTESTED, and the CONTESTED branch above accumulates
                    // toward DEFENDER_HELD. Wired now so the AutoGarrisonTrigger
                    // lands as a v2 trigger registration and not a state-machine
                    // rewrite. In v1 no production path drops defenders into a
                    // marine-held zone, so this branch is dormant — MARINE_HELD
                    // is effectively absorbing.
                    if (defendersPresent) {
                        r.state = CompoundService.CompoundState.CONTESTED;
                        r.holdTimer = 0f;
                        r.captureProgress = 0f;
                    }
                }
            }
        }
    }
}
