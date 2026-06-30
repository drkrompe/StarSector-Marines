package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Data owner for the "where does the defender want to reinforce" question for
 * conquest progressive reinforcement
 * ({@code roadmap/conquest/stories/progressive-reinforcement.md}).
 *
 * <p>It holds the static set of {@link RecaptureTarget}s (every eligible
 * defender node, bucketed by biome slice at init — nodes don't move) plus the
 * derived state the dispatch layer queries:
 *
 * <ul>
 *   <li><b>Open targets.</b> A node is <i>open</i> when zero alive defenders
 *       are assigned to it — the garrison (original or a prior reinforcement)
 *       has been wiped.</li>
 *   <li><b>Contested slices (the frontline).</b> A biome slice is
 *       <i>contested</i> while it holds a (debounced) defender presence; once
 *       the marines overrun it the slice is <i>conceded</i> and its targets
 *       drop out of eligibility.</li>
 * </ul>
 *
 * <p>A target is <b>eligible for dispatch</b> iff {@code open && !dispatched &&
 * contested(slice)}.
 *
 * <p>A <b>Service</b> (data owner): it holds the targets + the open/contested
 * results and exposes the read/mutate methods for them; the per-tick recompute
 * (the debounce machine + the squad-assignment aggregation) lives on
 * {@link RecaptureTargetSystem}. Named {@code *Service}, not {@code *System},
 * under the Service(data-owner)/System(processor) convention — see
 * {@code roadmap/ecs-migration/stories/entity-field-migration.md}. The
 * recompute writes results back through the package-private
 * {@link #setContested} mutator (and the targets' own flags).
 */
public final class RecaptureTargetService {

    private final List<RecaptureTarget> targets = new ArrayList<>();
    private final Map<BiomeKind, List<RecaptureTarget>> bySlice = new EnumMap<>(BiomeKind.class);

    private final EnumMap<BiomeKind, Boolean> contested = new EnumMap<>(BiomeKind.class);

    public RecaptureTargetService(TacticalMap tacticalMap, BiomeMap biomeMap) {
        for (TacticalNode node : tacticalMap.forFaction(Faction.DEFENDER)) {
            if (!isRecaptureEligible(node)) continue;
            BiomeKind slice = biomeMap.biomeAt(node.anchorX, node.anchorY);
            RecaptureTarget t = new RecaptureTarget(node, slice);
            targets.add(t);
            bySlice.computeIfAbsent(slice, k -> new ArrayList<>()).add(t);
        }
        for (BiomeKind b : BiomeKind.values()) {
            contested.put(b, Boolean.FALSE);
        }
    }

    private static boolean isRecaptureEligible(TacticalNode node) {
        return node.defaultGuard == Faction.DEFENDER
                && node.garrisonSize > 0
                && node.kind != TacticalNode.Kind.AIRBASE;
    }

    /** Whether {@code slice} currently holds a (debounced) defender presence. */
    public boolean isContested(BiomeKind slice) {
        return contested.getOrDefault(slice, Boolean.FALSE);
    }

    /**
     * Open, undispatched targets sitting in contested slices — the
     * dispatch-eligible set the front-line trigger round-robins over. Conceded
     * slices (marines overran them) and already-dispatched targets are
     * filtered out.
     */
    public List<RecaptureTarget> eligibleTargets() {
        List<RecaptureTarget> out = new ArrayList<>();
        for (RecaptureTarget t : targets) {
            if (t.open && !t.dispatched && isContested(t.slice)) out.add(t);
        }
        return out;
    }

    /** Static bucket of every target whose anchor falls in {@code slice}, regardless of state. */
    public List<RecaptureTarget> targetsInSlice(BiomeKind slice) {
        return Collections.unmodifiableList(bySlice.getOrDefault(slice, List.of()));
    }

    /**
     * Mark a target as having a reinforcement en route, suppressing re-dispatch
     * until it arrives or the wave is wiped.
     *
     * <p>Contract for the dispatch layer (slices 3-4): call this only when a
     * means has actually dispatched, and give the spawned squad
     * {@code assignedNode == target.node} at deboard — <em>not</em> only after
     * it physically reaches the node. The flag self-clears the moment an alive
     * squad is assigned to the node; if that squad is then wiped (even mid-
     * advance) the node re-opens via {@link RecaptureTargetSystem}. Skip the
     * at-deboard assignment and a squad wiped before arrival leaves the target
     * {@code open && dispatched} forever — silently un-reinforced.
     */
    public void markDispatched(RecaptureTarget target) {
        target.dispatched = true;
    }

    /** All recapture targets, regardless of state. */
    public List<RecaptureTarget> allTargets() {
        return Collections.unmodifiableList(targets);
    }

    // ---- System-facing mutators (driven by RecaptureTargetSystem) ----

    /** Set a slice's contested result. Called by the recompute in {@link RecaptureTargetSystem}. */
    void setContested(BiomeKind slice, boolean value) {
        contested.put(slice, value);
    }
}
