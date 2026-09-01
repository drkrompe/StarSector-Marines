package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.FrontDepth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Data owner for the "where does the defender want to reinforce" question for
 * conquest progressive reinforcement
 * ({@code reinforcement-nouns.md}).
 *
 * <p>It holds the static set of {@link RecaptureTarget}s (every eligible
 * defender node, bucketed by {@link FrontDepth} band at init — nodes don't
 * move) plus the derived state the dispatch layer queries:
 *
 * <ul>
 *   <li><b>Open targets.</b> A node is <i>open</i> when zero alive defenders
 *       are assigned to it — the garrison (original or a prior reinforcement)
 *       has been wiped.</li>
 *   <li><b>Contested bands (the frontline).</b> A front band is
 *       <i>contested</i> while it holds a (debounced) defender presence; once
 *       the marines overrun it the band is <i>conceded</i> and its targets
 *       drop out of eligibility.</li>
 * </ul>
 *
 * <p>A target is <b>eligible for dispatch</b> iff {@code open && !dispatched &&
 * contested(band)}.
 *
 * <p>A <b>Service</b> (data owner): it holds the targets + the open/contested
 * results and exposes the read/mutate methods for them; the per-tick recompute
 * (the debounce machine + the squad-assignment aggregation) lives on
 * {@link RecaptureTargetSystem}. Named {@code *Service}, not {@code *System},
 * under the Service(data-owner)/System(processor) convention — see
 * {@code ecs-nouns.md}. The
 * recompute writes results back through the package-private
 * {@link #setContested} mutator (and the targets' own flags).
 */
public final class RecaptureTargetService {

    private final List<RecaptureTarget> targets = new ArrayList<>();
    private final List<List<RecaptureTarget>> byBand = new ArrayList<>();

    private final boolean[] contested;

    public RecaptureTargetService(TacticalMap tacticalMap, FrontDepth frontDepth) {
        int bands = frontDepth.bands();
        this.contested = new boolean[bands];
        for (int b = 0; b < bands; b++) {
            byBand.add(new ArrayList<>());
        }
        for (TacticalNode node : tacticalMap.forFaction(Faction.DEFENDER)) {
            if (!isRecaptureEligible(node)) continue;
            int band = frontDepth.bandAt(node.anchorX, node.anchorY);
            RecaptureTarget t = new RecaptureTarget(node, band);
            targets.add(t);
            byBand.get(band).add(t);
        }
    }

    private static boolean isRecaptureEligible(TacticalNode node) {
        return node.defaultGuard == Faction.DEFENDER
                && node.garrisonSize > 0
                && node.kind != TacticalNode.Kind.AIRBASE;
    }

    /** Whether {@code band} currently holds a (debounced) defender presence. */
    public boolean isContested(int band) {
        return band >= 0 && band < contested.length && contested[band];
    }

    /**
     * Open, undispatched, once-manned targets sitting in contested bands —
     * the dispatch-eligible set the front-line trigger round-robins over.
     * Conceded bands (marines overran them), already-dispatched targets, and
     * never-manned nodes (positions {@code BattleSetup} never actually
     * garrisoned — nothing was "lost" there) are filtered out.
     */
    public List<RecaptureTarget> eligibleTargets() {
        List<RecaptureTarget> out = new ArrayList<>();
        for (RecaptureTarget t : targets) {
            if (t.manned && t.open && !t.dispatched && isContested(t.band)) out.add(t);
        }
        return out;
    }

    /** Static bucket of every target whose anchor falls in {@code band}, regardless of state. */
    public List<RecaptureTarget> targetsInSlice(int band) {
        if (band < 0 || band >= byBand.size()) return List.of();
        return Collections.unmodifiableList(byBand.get(band));
    }

    /**
     * Mark a target as having a reinforcement en route, suppressing re-dispatch
     * until it arrives or the wave is wiped.
     *
     * <p>Contract for the dispatch layer (slices 3-4): reserve while a request
     * is pending, release immediately if every means rejects, and give a
     * committed spawned squad
     * {@code assignedNode == target.node} at deboard — <em>not</em> only after
     * it physically reaches the node. The flag self-clears the moment an alive
     * squad is assigned to the node; if that squad is then wiped (even mid-
     * advance) the node re-opens via {@link RecaptureTargetSystem}. Skip the
     * at-deboard assignment and a squad wiped before arrival leaves the target
     * {@code open && dispatched} forever — silently un-reinforced.
     *
     * <p>Later delivery-pipeline losses the assignment contract can't see
     * ({@code SquadFallbackSystem} re-assigning a mauled squad's node away
     * from the target, or an in-flight actor never arriving) are healed by the
     * {@link RecaptureTargetSystem#DISPATCH_TIMEOUT_TICKS} safety net rather
     * than tracked individually.
     */
    public void markDispatched(RecaptureTarget target) {
        target.dispatched = true;
        target.dispatchAgeTicks = 0;
        target.dispatchReservationGeneration++;
    }

    /** Reserve one target and return its idempotent terminal-rejection release. */
    ReinforcementDispatchReservation reserveDispatch(RecaptureTarget target) {
        markDispatched(target);
        long generation = target.dispatchReservationGeneration;
        return () -> {
            if (target.dispatchReservationGeneration != generation) return;
            target.dispatched = false;
            target.dispatchAgeTicks = 0;
        };
    }

    /** All recapture targets, regardless of state. */
    public List<RecaptureTarget> allTargets() {
        return Collections.unmodifiableList(targets);
    }

    // ---- System-facing mutators (driven by RecaptureTargetSystem) ----

    /** Set a band's contested result. Called by the recompute in {@link RecaptureTargetSystem}. */
    void setContested(int band, boolean value) {
        if (band < 0 || band >= contested.length) return;
        contested[band] = value;
    }
}
