package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.command.ConquestLaneChain;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.world.model.FrontDepth;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-tick recompute driver for {@link RecaptureTargetService} — the
 * Services-own-state / Systems-process shape. On its slow-tick cadence it
 * recomputes the contested bands (the frontline) and the per-target open
 * state, writing the results back onto the Service.
 *
 * <p>A <b>System</b> (processor): it owns only the transient recompute
 * bookkeeping — the cadence {@link #accumulator}, the per-band debounce
 * {@link #disagreeStreak}, and the first-observation {@link #seeded} latch.
 * Named {@code *System}, not {@code *Service}, under the
 * Service(data-owner)/System(processor) convention — see
 * {@code ecs-nouns.md}.
 *
 * <ul>
 *   <li><b>Open targets.</b> Derived each tick from squad&rarr;node assignment,
 *       the same aggregation {@link GarrisonDepletedTrigger} uses.</li>
 *   <li><b>Contested bands.</b> Alive defender units are binned into front
 *       bands via {@link FrontDepth#bandAt}; presence is debounced over
 *       {@link #PRESENCE_DEBOUNCE_TICKS} ticks in <em>both</em> directions so a
 *       lone straggler dying/respawning doesn't make the front flicker.</li>
 * </ul>
 */
public final class RecaptureTargetSystem {

    private static final Logger LOG = Global.getLogger(RecaptureTargetSystem.class);

    /**
     * Consecutive slow-ticks a band's presence observation must disagree with
     * its current contested state before the state flips. At the reinforcement
     * cadence (~1s) this is ~3s of stable presence/absence — long enough to
     * ride out a single defender dying and the next arriving, short enough to
     * track a real advance. Tune in playtest.
     */
    public static final int PRESENCE_DEBOUNCE_TICKS = 3;

    /**
     * Recompute ticks (≈ seconds at the reinforcement cadence) a target may
     * sit {@code open && dispatched} before the dispatch flag is presumed
     * lost and cleared. The flag is set optimistically at post time, but the
     * delivery pipeline has consumed-without-spawn paths — a means whose
     * dispatch aborts after the request was consumed (convoy routing
     * failures), a request no means could fulfill, {@code SquadFallbackSystem}
     * re-assigning a mauled reinforcement's node — and without a timeout any
     * of those would strand the target open-but-suppressed forever. Must
     * comfortably exceed the slowest legitimate delivery (convoy: pending
     * delay + cross-map drive + advance on foot); a late expiry on a live
     * delivery merely double-books the node, which the arrival dedup absorbs.
     * Tune in playtest.
     */
    public static final int DISPATCH_TIMEOUT_TICKS = 90;

    private static final float TICK_PERIOD = ReinforcementService.REINFORCEMENT_TICK_PERIOD;

    private final RecaptureTargetService targets;
    private final FrontDepth frontDepth;

    private final int[] disagreeStreak;
    private boolean seeded = false;
    private float accumulator = 0f;

    /**
     * Per lane and rung, whether the marines held that place at the previous
     * recompute. The only way to know which place was lost <em>most
     * recently</em> is to have been watching, so the watching lives here — the
     * transient recompute bookkeeping this System owns — rather than on the
     * Service, which holds the answer.
     */
    private final boolean[][] linkWasHeld;

    public RecaptureTargetSystem(RecaptureTargetService targets, FrontDepth frontDepth) {
        this.targets = targets;
        this.frontDepth = frontDepth;
        this.disagreeStreak = new int[frontDepth.bands()];
        ConquestLaneChain chain = targets.laneChain();
        this.linkWasHeld = new boolean[chain.laneCount()][];
        for (int lane = 0; lane < chain.laneCount(); lane++) {
            linkWasHeld[lane] = new boolean[chain.links(lane).size()];
        }
    }

    /** Slow-tick: accumulate {@code dt}, then on cadence recompute contested bands and open targets. */
    public void tick(float dt, BattleView sim) {
        if (targets.allTargets().isEmpty()) return;
        accumulator += dt;
        if (accumulator < TICK_PERIOD) return;
        accumulator -= TICK_PERIOD;
        updateContested(sim);
        updateLaneChain(sim);
        updateOpenState(sim);
    }

    /**
     * Re-reads each lane's front and the place it most recently lost.
     *
     * <p>The front is ownership along the chain, which is a fact the compound
     * service already holds; what needs remembering is which place changed
     * hands last, because "retake the one just lost" is a claim about order and
     * a snapshot has none. A place is <em>lost</em> on the recompute at which
     * every compound on it first reads marine-held.
     *
     * <p>Nothing happens on a map with no lanes, which is every mission but
     * Conquest: the arrays are empty and every target keeps its band.
     */
    private void updateLaneChain(BattleView sim) {
        ConquestLaneChain chain = targets.laneChain();
        if (chain.laneCount() == 0) return;
        CompoundService compounds = sim.getCompoundService();
        if (compounds == null) return;
        Set<Integer> marineHeld = new HashSet<>();
        for (CompoundService.Record record : compounds.getRecords()) {
            if (record.state != CompoundService.CompoundState.MARINE_HELD) continue;
            marineHeld.add(compounds.captureZoneId(record, sim));
        }
        for (int lane = 0; lane < chain.laneCount(); lane++) {
            int lastLost = targets.laneLastLost(lane);
            for (ConquestLaneChain.Link link : chain.links(lane)) {
                if (!link.hasCompounds()) continue;
                boolean held = link.isHeld(marineHeld::contains);
                if (held && !linkWasHeld[lane][link.index()]) lastLost = link.index();
                linkWasHeld[lane][link.index()] = held;
            }
            targets.setLaneState(lane, chain.frontLink(lane, marineHeld::contains),
                    lastLost);
        }
    }

    private void updateContested(BattleView sim) {
        int[] present = new int[frontDepth.bands()];
        int totalDefenders = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            if (sim.identity().faction(u) != Faction.DEFENDER) continue;
            present[frontDepth.bandAt(sim.world().cellX(u), sim.world().cellY(u))]++;
            totalDefenders++;
        }
        for (int b = 0; b < present.length; b++) {
            boolean nowPresent = present[b] > 0;
            if (!seeded) {
                // First *real* observation seeds the stable state directly so
                // the front starts correct rather than debouncing up from "all
                // conceded" over the opening seconds.
                targets.setContested(b, nowPresent);
                disagreeStreak[b] = 0;
                continue;
            }
            boolean stable = targets.isContested(b);
            if (nowPresent == stable) {
                disagreeStreak[b] = 0;
            } else {
                int streak = disagreeStreak[b] + 1;
                if (streak >= PRESENCE_DEBOUNCE_TICKS) {
                    targets.setContested(b, nowPresent);
                    disagreeStreak[b] = 0;
                } else {
                    disagreeStreak[b] = streak;
                }
            }
        }
        // Defer locking the seed until defenders actually exist. A tick that
        // runs during sim-init before garrisons are placed would otherwise seed
        // every band "conceded" and force a full debounce ramp to recover;
        // until then each (all-conceded) tick is a harmless re-seed.
        if (totalDefenders > 0) seeded = true;
    }

    private void updateOpenState(BattleView sim) {
        // Open-detection rides on two invariants the dispatch layer must keep:
        // a wiped garrison squad keeps its assignedNode and stays in
        // getSquads() (squads are never GC'd), and a reinforcement squad is
        // given assignedNode == its target node at deboard. The second is what
        // lets a squad wiped *en route* re-open the target (alive drops to 0)
        // rather than leaving it open && dispatched forever — see
        // RecaptureTargetService#markDispatched.
        Map<TacticalNode, Integer> assignedAlive = new HashMap<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.DEFENDER) continue;
            TacticalNode node = squad.assignedNode;
            if (node == null) continue;
            assignedAlive.merge(node, squad.aliveMembers, Integer::sum);
        }
        for (RecaptureTarget t : targets.allTargets()) {
            int alive = assignedAlive.getOrDefault(t.node, 0);
            if (alive > 0) {
                // Held — original garrison or an arrived reinforcement. Clear
                // the dispatch flag so a future wipe re-opens the target.
                t.manned = true;
                t.open = false;
                t.dispatched = false;
                t.dispatchAgeTicks = 0;
            } else {
                t.open = true;
                if (t.dispatched && ++t.dispatchAgeTicks >= DISPATCH_TIMEOUT_TICKS) {
                    t.dispatched = false;
                    t.dispatchAgeTicks = 0;
                    LOG.debug("recapture: dispatch to " + t.node.kind
                            + " @(" + t.node.anchorX + "," + t.node.anchorY
                            + ") timed out with no arrival - re-opening");
                }
            }
        }
    }
}
