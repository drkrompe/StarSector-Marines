package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.command.ConquestLaneChain;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.FrontDepth;

import java.util.ArrayList;
import java.util.Arrays;
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

    /**
     * The lanes as chains, for the half of the map that stands on one.
     *
     * <p>{@link ConquestLaneChain#NONE} for every mission but Conquest and for
     * a Conquest whose map laid no lanes, in which case every target answers
     * with its band exactly as it always has.
     */
    private final ConquestLaneChain chain;
    /** Per lane: the place the marines are coming for next. */
    private final int[] laneFront;
    /** Per lane: the place they most recently took, which is what to retake. */
    private final int[] laneLastLost;

    public RecaptureTargetService(TacticalMap tacticalMap, FrontDepth frontDepth) {
        this(tacticalMap, frontDepth, ConquestLaneChain.NONE);
    }

    /**
     * The chain-aware form: a defender position standing on a lane is bucketed
     * by the place it belongs to rather than by the ring it happens to fall in.
     *
     * <p>A front band is a ring around the objective and says nothing about
     * which lane is being fought over; a lane's chain says exactly that, and a
     * defender wanting to hold the next place and retake the last one lost is
     * asking a question about places. Positions on no lane keep the band
     * reading, which is what a settlement's guard post has and should have.
     */
    public RecaptureTargetService(TacticalMap tacticalMap, FrontDepth frontDepth,
                                  ConquestLaneChain chain) {
        int bands = frontDepth.bands();
        this.contested = new boolean[bands];
        this.chain = chain == null ? ConquestLaneChain.NONE : chain;
        this.laneFront = new int[this.chain.laneCount()];
        this.laneLastLost = new int[this.chain.laneCount()];
        Arrays.fill(this.laneLastLost, -1);
        for (int b = 0; b < bands; b++) {
            byBand.add(new ArrayList<>());
        }
        for (TacticalNode node : tacticalMap.forFaction(Faction.DEFENDER)) {
            if (!isRecaptureEligible(node)) continue;
            int band = frontDepth.bandAt(node.anchorX, node.anchorY);
            RecaptureTarget t = new RecaptureTarget(node, band);
            int[] place = this.chain.placeAt(node.anchorX, node.anchorY);
            if (place != null) {
                t.lane = place[0];
                t.link = place[1];
            }
            targets.add(t);
            byBand.get(band).add(t);
        }
    }

    /** The lanes this service buckets its targets by; never null. */
    public ConquestLaneChain laneChain() {
        return chain;
    }

    /**
     * Whether this position is somewhere the defender still wants people.
     *
     * <p>On a lane the answer is the chain's: the place the marines are coming
     * for next is worth holding, and the place they have just taken is worth
     * retaking. Everything further back on the lane is behind the fighting and
     * everything further forward is not being attacked yet, and reinforcing
     * either is a squad spent where nothing is happening.
     *
     * <p>Off a lane it is the band's, unchanged.
     */
    public boolean isContested(RecaptureTarget target) {
        if (target.lane < 0 || target.lane >= laneFront.length) {
            return isContested(target.band);
        }
        return target.link == laneFront[target.lane]
                || target.link == laneLastLost[target.lane];
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
            if (t.manned && t.open && !t.dispatched && isContested(t)) out.add(t);
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

    /**
     * Set one lane's chain state. Called by the recompute in
     * {@link RecaptureTargetSystem}, which reads compound ownership.
     *
     * @param front    the first place on the lane the marines do not hold
     * @param lastLost the place most recently taken from the defender, or
     *                 {@code -1} while the lane has lost nothing
     */
    void setLaneState(int lane, int front, int lastLost) {
        if (lane < 0 || lane >= laneFront.length) return;
        laneFront[lane] = front;
        laneLastLost[lane] = lastLost;
    }

    /** The first place on {@code lane} the marines do not hold. */
    public int laneFront(int lane) {
        return lane >= 0 && lane < laneFront.length ? laneFront[lane] : -1;
    }

    /** The place most recently taken from the defender on {@code lane}. */
    public int laneLastLost(int lane) {
        return lane >= 0 && lane < laneLastLost.length ? laneLastLost[lane] : -1;
    }
}
