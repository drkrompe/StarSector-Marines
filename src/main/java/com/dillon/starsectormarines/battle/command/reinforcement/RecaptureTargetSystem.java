package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-tick recompute driver for {@link RecaptureTargetService} — the
 * Services-own-state / Systems-process shape. On its slow-tick cadence it
 * recomputes the contested slices (the frontline) and the per-target open
 * state, writing the results back onto the Service.
 *
 * <p>A <b>System</b> (processor): it owns only the transient recompute
 * bookkeeping — the cadence {@link #accumulator}, the per-slice debounce
 * {@link #disagreeStreak}, and the first-observation {@link #seeded} latch.
 * Named {@code *System}, not {@code *Service}, under the
 * Service(data-owner)/System(processor) convention — see
 * {@code roadmap/ecs-migration/stories/entity-field-migration.md}.
 *
 * <ul>
 *   <li><b>Open targets.</b> Derived each tick from squad&rarr;node assignment,
 *       the same aggregation {@link GarrisonDepletedTrigger} uses.</li>
 *   <li><b>Contested slices.</b> Alive defender units are binned into biome
 *       slices via {@link BiomeMap#biomeAt}; presence is debounced over
 *       {@link #PRESENCE_DEBOUNCE_TICKS} ticks in <em>both</em> directions so a
 *       lone straggler dying/respawning doesn't make the front flicker.</li>
 * </ul>
 */
public final class RecaptureTargetSystem {

    /**
     * Consecutive slow-ticks a slice's presence observation must disagree with
     * its current contested state before the state flips. At the reinforcement
     * cadence (~1s) this is ~3s of stable presence/absence — long enough to
     * ride out a single defender dying and the next arriving, short enough to
     * track a real advance. Tune in playtest.
     */
    public static final int PRESENCE_DEBOUNCE_TICKS = 3;

    private static final float TICK_PERIOD = ReinforcementService.REINFORCEMENT_TICK_PERIOD;

    private final RecaptureTargetService targets;
    private final BiomeMap biomeMap;

    private final EnumMap<BiomeKind, Integer> disagreeStreak = new EnumMap<>(BiomeKind.class);
    private boolean seeded = false;
    private float accumulator = 0f;

    public RecaptureTargetSystem(RecaptureTargetService targets, BiomeMap biomeMap) {
        this.targets = targets;
        this.biomeMap = biomeMap;
        for (BiomeKind b : BiomeKind.values()) {
            disagreeStreak.put(b, 0);
        }
    }

    /** Slow-tick: accumulate {@code dt}, then on cadence recompute contested slices and open targets. */
    public void tick(float dt, BattleView sim) {
        if (targets.allTargets().isEmpty()) return;
        accumulator += dt;
        if (accumulator < TICK_PERIOD) return;
        accumulator -= TICK_PERIOD;
        updateContested(sim);
        updateOpenState(sim);
    }

    private void updateContested(BattleView sim) {
        EnumMap<BiomeKind, Integer> present = new EnumMap<>(BiomeKind.class);
        int totalDefenders = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            Entity u = sim.liveUnitAt(i);
            if (u.faction != Faction.DEFENDER) continue;
            present.merge(biomeMap.biomeAt(sim.world().cellX(u.entityId), sim.world().cellY(u.entityId)), 1, Integer::sum);
            totalDefenders++;
        }
        for (BiomeKind b : BiomeKind.values()) {
            boolean nowPresent = present.getOrDefault(b, 0) > 0;
            if (!seeded) {
                // First *real* observation seeds the stable state directly so
                // the front starts correct rather than debouncing up from "all
                // conceded" over the opening seconds.
                targets.setContested(b, nowPresent);
                disagreeStreak.put(b, 0);
                continue;
            }
            boolean stable = targets.isContested(b);
            if (nowPresent == stable) {
                disagreeStreak.put(b, 0);
            } else {
                int streak = disagreeStreak.get(b) + 1;
                if (streak >= PRESENCE_DEBOUNCE_TICKS) {
                    targets.setContested(b, nowPresent);
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
                t.open = false;
                t.dispatched = false;
            } else {
                t.open = true;
            }
        }
    }
}
