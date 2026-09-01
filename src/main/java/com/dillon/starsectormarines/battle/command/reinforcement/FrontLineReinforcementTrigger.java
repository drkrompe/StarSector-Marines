package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.FrontDepth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Front-band round-robin reinforcement trigger — the front-line dispatch
 * design in {@code reinforcement-nouns.md}
 * (slice 3). Replaces {@link GarrisonDepletedTrigger} on maps that carry a
 * {@link RecaptureTargetService} (conquest only; see
 * {@code BattleSetup#installReinforcementLayer}).
 *
 * <p>Each poll:
 * <ol>
 *   <li>Reads {@link RecaptureTargetService#eligibleTargets()} — open,
 *       undispatched targets in bands the defender still contests.</li>
 *   <li>Groups them by front band and picks the shallowest one — band 0 sits
 *       at the objective, so the rear-to-front walk is simply
 *       {@code 0..bands-1} — that has at least one eligible target,
 *       reinforcing the defender's own rear before falling through toward the
 *       marine side.</li>
 *   <li>Round-robins across that band's eligible targets so repeated
 *       dispatches spread across positions instead of stacking on one.</li>
 * </ol>
 *
 * <p>Posts at most one request per {@link #check} call — the trigger runs on
 * {@link ReinforcementSystem}'s 1&nbsp;Hz cadence, so there's no need for an
 * internal accumulator. When {@link RecaptureTargetService#eligibleTargets()}
 * is empty (every lost position is already held or has a reinforcement en
 * route, or every band is conceded) the trigger posts nothing — overflow →
 * patrol is deferred per the design doc; the existing {@link WalkInMeans}
 * free-agent fallback covers ambient patrol in the meantime.
 */
public final class FrontLineReinforcementTrigger implements ReinforcementTrigger {

    /**
     * Cells the rally hint is shifted from the target's anchor, toward the
     * objective. The rally is only a search seed for the means +
     * {@link LandingZoneScorer} — not the literal deboard cell — so this just
     * needs to land solidly inside defender territory, not on a specific
     * viable cell.
     */
    private static final int RALLY_REAR_SHIFT = 8;

    private final RecaptureTargetService targets;
    private final FrontDepth frontDepth;

    /** Per-band round-robin cursor — the index of the next target to dispatch within that band's eligible list. */
    private final int[] rotation;

    public FrontLineReinforcementTrigger(RecaptureTargetService targets, FrontDepth frontDepth) {
        this.targets = targets;
        this.frontDepth = frontDepth;
        this.rotation = new int[frontDepth.bands()];
    }

    @Override
    public void check(BattleView sim, Consumer<ReinforcementRequest> out) {
        RecaptureTarget target = selectDispatchTarget();
        if (target == null) return;
        int[] rally = rallyRearShift(target.node.anchorX, target.node.anchorY, frontDepth);
        ReinforcementDispatchReservation reservation =
                targets.reserveDispatch(target);
        out.accept(new ReinforcementRequest(
                Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL,
                rally[0], rally[1],
                target.objectiveX(), target.objectiveY())
                .withDispatchReservation(reservation));
    }

    /**
     * Picks the next dispatch target: the shallowest band with at least one
     * eligible target — band 0, the objective's own, first — then the next
     * target in that band's round-robin rotation. Advances {@link #rotation}
     * as a side effect. Returns {@code null} when
     * {@link RecaptureTargetService#eligibleTargets()} is empty.
     *
     * <p>Package-private (not {@code check}'s {@link BattleView} dependency)
     * so tests can exercise the selection logic directly against a
     * synthetic {@link RecaptureTargetService}.
     */
    RecaptureTarget selectDispatchTarget() {
        List<RecaptureTarget> eligible = targets.eligibleTargets();
        if (eligible.isEmpty()) return null;

        List<List<RecaptureTarget>> byBand = new ArrayList<>();
        for (int band = 0; band < rotation.length; band++) {
            byBand.add(new ArrayList<>());
        }
        for (RecaptureTarget t : eligible) {
            byBand.get(t.band).add(t);
        }
        for (int band = 0; band < rotation.length; band++) {
            List<RecaptureTarget> inBand = byBand.get(band);
            if (inBand.isEmpty()) continue;
            int idx = rotation[band];
            RecaptureTarget picked = inBand.get(idx % inBand.size());
            rotation[band] = idx + 1;
            return picked;
        }
        return null;
    }

    /**
     * Shifts {@code (anchorX, anchorY)} {@link #RALLY_REAR_SHIFT} cells toward
     * the objective, clamped to the map by {@link FrontDepth#rearward}.
     *
     * <p>The defender's rear is wherever the thing being defended is, and that
     * is a fact every front carries; a traversal axis is a fact one recipe
     * carries. Package-private static so the shift is unit-testable without a
     * {@link BattleView}, and so {@link CounterattackSystem} can place its
     * bulge rallies by the same rule.
     */
    static int[] rallyRearShift(int anchorX, int anchorY, FrontDepth frontDepth) {
        return frontDepth.rearward(anchorX, anchorY, RALLY_REAR_SHIFT);
    }
}
