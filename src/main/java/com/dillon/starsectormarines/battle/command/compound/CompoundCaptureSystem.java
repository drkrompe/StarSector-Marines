package com.dillon.starsectormarines.battle.command.compound;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Slow-tick consumer that drives the compound capture state machine. Each
 * cadence period it samples per-compound occupancy via
 * {@link CompoundService#occupiedBy} and writes the resulting state /
 * hold-timer / capture-progress back to {@link CompoundService}.
 *
 * <p>Cadence mirrors
 * {@link com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementService}
 * — 1 Hz. Capture is inherently slow (a few seconds of "hold the room");
 * the slower poll keeps the per-compound occupancy scan off the
 * per-frame hot path. The system is stateless w.r.t. game state — the
 * accumulator field is pure tick-pacing plumbing, same shape as the
 * accumulator in {@link com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementService}.
 *
 * <p>Occupancy is scoped to the compound's own footprint as well as its
 * room — see {@link CompoundService#occupiedBy} for why an open compound
 * such as an airfield is otherwise permanently contested.
 *
 * <p>The same state machine handles capture and recapture. A defender
 * re-entering a marine-held compound returns it to {@code CONTESTED}; the
 * existing defender reinforcement path can then drive it back to
 * {@code DEFENDER_HELD} without a state-machine rewrite.
 */
public final class CompoundCaptureSystem {

    /** Sim-seconds between capture-state evaluations. Same cadence shape as {@link com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementService#REINFORCEMENT_TICK_PERIOD} so the two layers reach the same compound state within at most a tick of each other. */
    public static final float CAPTURE_TICK_PERIOD = 1.0f;

    private static final Logger LOG = Global.getLogger(CompoundCaptureSystem.class);

    private float accumulator = 0f;

    /** Compounds already reported as roomless, so the warning stays one per compound rather than one per second. */
    private final Set<TacticalNode> reportedRoomless = new HashSet<>();

    /**
     * Advance the capture state machine. Accumulates {@code dt} and only
     * walks the record list when the cadence period elapses; per-cell
     * occupancy reads (via {@link CompoundService#occupiedBy}) iterate the live
     * unit list once per compound per slow tick.
     */
    public void tick(float dt, BattleView sim, CompoundService service) {
        if (service == null || service.getRecords().isEmpty()) return;
        accumulator += dt;
        if (accumulator < CAPTURE_TICK_PERIOD) return;
        accumulator -= CAPTURE_TICK_PERIOD;

        for (CompoundService.Record r : service.getRecords()) {
            int zoneId = service.captureZoneId(r, sim);
            // The compound's footprint holds no zone at all — a degenerate
            // carve with no walkable interior. Skip this tick; the compound
            // state holds.
            if (zoneId < 0) {
                if (reportedRoomless.add(r.node)) {
                    LOG.warn("CompoundCaptureSystem: " + r.node.kind + " at "
                            + r.node.left + "," + r.node.top + ".."
                            + r.node.right + "," + r.node.bottom
                            + " encloses no zoned cell, so it can never be"
                            + " captured. Generated compound has no walkable interior.");
                }
                continue;
            }

            boolean defendersPresent = CompoundService.occupiedBy(
                    r, zoneId, Faction.DEFENDER, sim);
            // The attacking side, not the player's faction: an allied militia
            // in the zone contests and takes ground exactly as a marine does.
            // Who ends up *holding* it is still only MARINE or DEFENDER.
            boolean attackersPresent = CompoundService.occupiedBy(
                    r, zoneId, Faction.MARINE, sim);

            switch (r.state) {
                case DEFENDER_HELD -> {
                    // First marine inside the zone flips to CONTESTED. Empty or
                    // defender-only zones stay DEFENDER_HELD with no progress.
                    if (attackersPresent) {
                        r.state = CompoundService.CompoundState.CONTESTED;
                        r.holdTimer = 0f;
                        r.captureProgress = 0f;
                    }
                }
                case CONTESTED -> {
                    if (attackersPresent && !defendersPresent) {
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
                    } else if (defendersPresent && !attackersPresent) {
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

}
