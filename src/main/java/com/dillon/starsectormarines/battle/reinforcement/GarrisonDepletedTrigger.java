package com.dillon.starsectormarines.battle.reinforcement;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.compound.CompoundService;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * V1 reinforcement trigger: posts a {@link ReinforcementRequest.Reason#GARRISON_DEPLETED}
 * for any defender-side tactical-node compound (COMMAND_POST, BARRACKS, or
 * ARMORY) whose aggregated squad strength drops below
 * {@link #DEPLETION_THRESHOLD}. The compound is recorded as posted on first
 * fire — once answered, the same compound won't refire (v1 is one-shot per
 * compound, no recovery story yet).
 */
public final class GarrisonDepletedTrigger implements ReinforcementTrigger {

    /**
     * Compound strength ratio ({@code aliveMembers / originalSize}, summed
     * across all squads on the node) that triggers a request. 0.5 = "half
     * the garrison is down" — enough to read as a meaningful loss, not
     * jumpy enough to fire on the first casualty.
     */
    public static final float DEPLETION_THRESHOLD = 0.5f;

    private final Set<TacticalNode> posted = new HashSet<>();

    @Override
    public void check(BattleSimulation sim, Consumer<ReinforcementRequest> out) {
        CompoundService compounds = sim.getCompoundService();
        Map<TacticalNode, int[]> agg = new HashMap<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.DEFENDER) continue;
            TacticalNode node = squad.assignedNode;
            if (node == null) continue;
            if (!isCompound(node.kind)) continue;
            int[] sums = agg.computeIfAbsent(node, k -> new int[2]);
            sums[0] += squad.aliveMembers;
            sums[1] += squad.originalSize;
        }
        for (Map.Entry<TacticalNode, int[]> e : agg.entrySet()) {
            TacticalNode node = e.getKey();
            if (posted.contains(node)) continue;
            // Slice-3 gate: a marine-held compound has lost its supply line —
            // posting a reinforcement request for a captured structure is the
            // exact bug the compound-as-supply model exists to fix. The
            // canFulfill side will reject it too, but rejecting at the
            // trigger side keeps the dispatcher log clean.
            CompoundService.Record rec = (compounds != null) ? compounds.getRecord(node) : null;
            if (rec != null && rec.state == CompoundService.CompoundState.MARINE_HELD) {
                continue;
            }
            int alive = e.getValue()[0];
            int original = e.getValue()[1];
            if (original <= 0) continue;
            float ratio = alive / (float) original;
            if (ratio >= DEPLETION_THRESHOLD) continue;
            posted.add(node);
            out.accept(new ReinforcementRequest(
                    Faction.DEFENDER,
                    ReinforcementRequest.Reason.GARRISON_DEPLETED,
                    ReinforcementRequest.Strength.SMALL,
                    node.centerX(), node.centerY()));
        }
    }

    private static boolean isCompound(TacticalNode.Kind kind) {
        return kind == TacticalNode.Kind.COMMAND_POST
                || kind == TacticalNode.Kind.BARRACKS
                || kind == TacticalNode.Kind.ARMORY;
    }
}
