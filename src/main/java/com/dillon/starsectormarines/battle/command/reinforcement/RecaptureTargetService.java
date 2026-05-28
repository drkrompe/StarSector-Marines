package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.tactical.TacticalMap;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-tick system that owns the "where does the defender want to reinforce"
 * question for conquest progressive reinforcement
 * ({@code roadmap/conquest/stories/progressive-reinforcement.md}).
 *
 * <p>It holds the static set of {@link RecaptureTarget}s (every eligible
 * defender node, bucketed by biome slice at init — nodes don't move) and, on
 * its slow-tick cadence, maintains two pieces of derived state:
 *
 * <ul>
 *   <li><b>Open targets.</b> A node is <i>open</i> when zero alive defenders
 *       are assigned to it — the garrison (original or a prior reinforcement)
 *       has been wiped. Derived each tick from squad→node assignment, the same
 *       aggregation {@link GarrisonDepletedTrigger} uses.</li>
 *   <li><b>Contested slices (the frontline).</b> Alive defender units are
 *       binned into biome slices via {@link BiomeMap#biomeAt}. A slice is
 *       <i>contested</i> while it holds a defender presence; once the marines
 *       fully overrun it (no alive defenders) it is <i>conceded</i> and its
 *       targets drop out of eligibility. Presence is debounced over
 *       {@link #PRESENCE_DEBOUNCE_TICKS} ticks in <em>both</em> directions so
 *       a lone straggler dying/respawning doesn't make the front flicker.</li>
 * </ul>
 *
 * <p>A target is <b>eligible for dispatch</b> iff {@code open && !dispatched &&
 * contested(slice)}. The {@code dispatched} flag de-dups while a squad is en
 * route and clears itself once an alive defender is re-assigned to the node,
 * so a later wipe re-opens the target with no timer.
 *
 * <p>Naming: this is a stateful {@code *Service}-shaped component with its own
 * {@link #tick} accumulator (same shape as {@link ReinforcementService}); it
 * owns state rather than being a stateless consumer.
 */
public final class RecaptureTargetService {

    /**
     * Consecutive slow-ticks a slice's presence observation must disagree with
     * its current contested state before the state flips. At the reinforcement
     * cadence (~1s) this is ~3s of stable presence/absence — long enough to
     * ride out a single defender dying and the next arriving, short enough to
     * track a real advance. Tune in playtest.
     */
    public static final int PRESENCE_DEBOUNCE_TICKS = 3;

    private static final float TICK_PERIOD = ReinforcementService.REINFORCEMENT_TICK_PERIOD;

    private final BiomeMap biomeMap;
    private final List<RecaptureTarget> targets = new ArrayList<>();
    private final Map<BiomeKind, List<RecaptureTarget>> bySlice = new EnumMap<>(BiomeKind.class);

    private final EnumMap<BiomeKind, Boolean> contested = new EnumMap<>(BiomeKind.class);
    private final EnumMap<BiomeKind, Integer> disagreeStreak = new EnumMap<>(BiomeKind.class);

    private boolean seeded = false;
    private float accumulator = 0f;

    public RecaptureTargetService(TacticalMap tacticalMap, BiomeMap biomeMap) {
        this.biomeMap = biomeMap;
        for (TacticalNode node : tacticalMap.forFaction(Faction.DEFENDER)) {
            if (!isRecaptureEligible(node)) continue;
            BiomeKind slice = biomeMap.biomeAt(node.anchorX, node.anchorY);
            RecaptureTarget t = new RecaptureTarget(node, slice);
            targets.add(t);
            bySlice.computeIfAbsent(slice, k -> new ArrayList<>()).add(t);
        }
        for (BiomeKind b : BiomeKind.values()) {
            contested.put(b, Boolean.FALSE);
            disagreeStreak.put(b, 0);
        }
    }

    private static boolean isRecaptureEligible(TacticalNode node) {
        return node.defaultGuard == Faction.DEFENDER
                && node.garrisonSize > 0
                && node.kind != TacticalNode.Kind.AIRBASE;
    }

    /** Slow-tick: accumulate {@code dt}, then on cadence recompute contested slices and open targets. */
    public void tick(float dt, BattleSimulation sim) {
        if (targets.isEmpty()) return;
        accumulator += dt;
        if (accumulator < TICK_PERIOD) return;
        accumulator -= TICK_PERIOD;
        updateContested(sim);
        updateOpenState(sim);
    }

    private void updateContested(BattleSimulation sim) {
        EnumMap<BiomeKind, Integer> present = new EnumMap<>(BiomeKind.class);
        int totalDefenders = 0;
        for (Unit u : sim.getUnits()) {
            if (u.faction != Faction.DEFENDER || !u.isAlive()) continue;
            present.merge(biomeMap.biomeAt(u.getCellX(), u.getCellY()), 1, Integer::sum);
            totalDefenders++;
        }
        for (BiomeKind b : BiomeKind.values()) {
            boolean nowPresent = present.getOrDefault(b, 0) > 0;
            if (!seeded) {
                // First *real* observation seeds the stable state directly so
                // the front starts correct rather than debouncing up from "all
                // conceded" over the opening seconds.
                contested.put(b, nowPresent);
                disagreeStreak.put(b, 0);
                continue;
            }
            boolean stable = contested.get(b);
            if (nowPresent == stable) {
                disagreeStreak.put(b, 0);
            } else {
                int streak = disagreeStreak.get(b) + 1;
                if (streak >= PRESENCE_DEBOUNCE_TICKS) {
                    contested.put(b, nowPresent);
                    disagreeStreak.put(b, 0);
                } else {
                    disagreeStreak.put(b, streak);
                }
            }
        }
        // Defer locking the seed until defenders actually exist. A tick that
        // runs during sim-init before garrisons are placed would otherwise seed
        // every slice "conceded" and force a full debounce ramp to recover;
        // until then each (all-conceded) tick is a harmless re-seed.
        if (totalDefenders > 0) seeded = true;
    }

    private void updateOpenState(BattleSimulation sim) {
        // Open-detection rides on two invariants the dispatch layer must keep:
        // a wiped garrison squad keeps its assignedNode and stays in
        // getSquads() (squads are never GC'd), and a reinforcement squad is
        // given assignedNode == its target node at deboard. The second is what
        // lets a squad wiped *en route* re-open the target (alive drops to 0)
        // rather than leaving it open && dispatched forever — see markDispatched.
        Map<TacticalNode, Integer> assignedAlive = new HashMap<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.DEFENDER) continue;
            TacticalNode node = squad.assignedNode;
            if (node == null) continue;
            assignedAlive.merge(node, squad.aliveMembers, Integer::sum);
        }
        for (RecaptureTarget t : targets) {
            int alive = assignedAlive.getOrDefault(t.node, 0);
            if (alive > 0) {
                // Held — original garrison or an arrived reinforcement. Clear
                // the dispatch flag so a future wipe re-opens the target.
                t.open = false;
                t.dispatched = false;
            } else {
                t.open = true;
            }
        }
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
     * advance) the node re-opens via {@link #updateOpenState}. Skip the
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
}
