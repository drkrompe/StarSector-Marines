package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.mech.MechVariant;

import java.util.List;

/**
 * Coarse battle-start force score used to prevent a single high-impact unit
 * from overwhelming an otherwise small encounter.
 *
 * <p>This first slice scores the forces that roster setup can see reliably:
 * attacking infantry seats (player and employer/allied waves), defender
 * militia/regulars, and defender mech chassis. Mission tier still authors the
 * infantry count; the score only decides how much of the candidate mech roster
 * the combined attack can support. Fighter wings, command powers, equipment,
 * and terrain are future score inputs rather than hidden adjustments here.
 */
public final class BattleForceScore {

    static final float MILITIA = 1f;
    static final float REGULAR_INFANTRY = 1.5f;

    /** Attackers should retain a nominal advantage before terrain/objectives. */
    private static final float DEFENDER_BUDGET_FRACTION = 0.75f;

    private BattleForceScore() {}

    /** Scores every infantry seat across every attacker shuttle cycle. */
    public static float attackers(List<ShuttleAssignment> assignments) {
        if (assignments == null) return 0f;
        int seats = 0;
        for (ShuttleAssignment assignment : assignments) {
            if (assignment == null) continue;
            seats += assignment.type.capacity * Math.max(0, assignment.cycles);
        }
        return seats * REGULAR_INFANTRY;
    }

    static float defenderBudget(float attackerScore) {
        return Math.max(0f, attackerScore) * DEFENDER_BUDGET_FRACTION;
    }

    static float defenders(int militiaCount, int eliteCount,
                           List<MechVariant> mechVariants) {
        float score = militiaCount * MILITIA + eliteCount * REGULAR_INFANTRY;
        if (mechVariants == null) return score;
        for (MechVariant variant : mechVariants) score += mech(variant);
        return score;
    }

    /** Provisional chassis values; tune against telemetry rather than HP alone. */
    static float mech(MechVariant variant) {
        if (variant == null) return 0f;
        return switch (variant) {
            case BULWARK -> 24f;
            case HOUND, SIROCCO -> 18f;
        };
    }
}
