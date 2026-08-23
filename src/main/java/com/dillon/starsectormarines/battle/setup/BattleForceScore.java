package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.turret.TurretKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Coarse battle-start force score used to prevent a single high-impact unit
 * from overwhelming an otherwise small encounter.
 *
 * <p>This first slice scores the forces that roster setup can see reliably:
 * attacking infantry seats (player and employer/allied waves), defender
 * militia/regulars, defender mech chassis, and static turret emplacements.
 * Mission tier still authors the infantry count and the map still authors the
 * defense-post candidates; the score only decides how much heavy support the
 * combined attack can support. Fighter wings, command powers, equipment, and
 * terrain are future score inputs rather than hidden adjustments here.
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

    static float defenders(DefenderRoster roster) {
        return defenders(roster.militiaCount, roster.eliteCount,
                roster.mechVariants);
    }

    /** Provisional chassis values; tune against telemetry rather than HP alone. */
    static float mech(MechVariant variant) {
        if (variant == null) return 0f;
        return switch (variant) {
            case BULWARK -> 24f;
            case HOUND, SIROCCO -> 18f;
        };
    }

    /** Provisional emplacement values; indirect fire and AoE carry premiums. */
    static float turret(TurretKind kind) {
        if (kind == null) return 0f;
        return switch (kind) {
            case VULCAN -> 8f;
            case ARBALEST, HEAVY_MG -> 10f;
            case DUAL_FLAK, GRENADE_LAUNCHER -> 12f;
            case HEAVY_MORTAR -> 14f;
            case HEPHAESTUS -> 16f;
            case LOCUST -> 18f;
        };
    }

    /**
     * Retains one stable prefix of the map-authored turret candidates beneath
     * the same attacker-derived cap already consumed by infantry and mechs.
     * Mechs keep priority because the roster is resolved first; turrets spend
     * only the remaining score. Fortification geometry is left on the map.
     *
     * <p>Posts with every turret removed are omitted so Conquest guard squads
     * are not linked to an emplacement that can never die. Drone hubs are not
     * turrets and survive unchanged.
     */
    static List<DefensePost> affordableDefensePosts(
            List<DefensePost> candidates, DefenderRoster roster,
            float attackerScore) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        if (!Float.isFinite(attackerScore)) return List.copyOf(candidates);

        int candidateTurrets = turretCount(candidates);
        int retainedTurrets = candidateTurrets;
        float supportScore = turretScore(candidates, retainedTurrets);
        float budget = defenderBudget(attackerScore);
        float rosterScore = defenders(roster);
        while (retainedTurrets > 0 && rosterScore + supportScore > budget) {
            retainedTurrets--;
            supportScore = turretScore(candidates, retainedTurrets);
        }
        if (retainedTurrets == candidateTurrets) return List.copyOf(candidates);

        List<DefensePost> selected = new ArrayList<>();
        int remaining = retainedTurrets;
        for (DefensePost post : candidates) {
            if (post.tier == DefensePostKind.DRONE_HUB) {
                selected.add(post);
                continue;
            }
            int take = Math.min(remaining, post.turrets.size());
            if (take <= 0) continue;
            if (take == post.turrets.size()) {
                selected.add(post);
            } else {
                selected.add(new DefensePost(post.tier, post.anchorX, post.anchorY,
                        List.copyOf(post.turrets.subList(0, take))));
            }
            remaining -= take;
        }
        return List.copyOf(selected);
    }

    private static int turretCount(List<DefensePost> posts) {
        int count = 0;
        for (DefensePost post : posts) count += post.turrets.size();
        return count;
    }

    private static float turretScore(List<DefensePost> posts, int retained) {
        float score = 0f;
        int remaining = retained;
        for (DefensePost post : posts) {
            for (DefensePost.TurretSpec spec : post.turrets) {
                if (remaining-- <= 0) return score;
                score += turret(spec.kind);
            }
        }
        return score;
    }
}
