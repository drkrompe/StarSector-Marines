package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
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
 * attacking infantry seats (player and employer/allied waves), fighter
 * sorties, defender militia/regulars, defender mech chassis, and static turret
 * emplacements. Mission tier still authors the infantry count and the map
 * still authors the defense-post candidates; the score only decides how much
 * heavy support the combined attack can support. Conquest deliberately passes
 * an unbounded attacker score so its authored set-piece support is retained.
 * Command powers, equipment, and terrain are future score inputs rather than
 * hidden adjustments here.
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

    /** Attacking infantry plus allied/employer fighter support. */
    public static float attackers(List<ShuttleAssignment> assignments,
                                  FlybyRoster fighterSupport) {
        return attackers(assignments) + fighterSupport(fighterSupport);
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

    /** Data-authored emplacement value used by force-budget accounting. */
    static float turret(TurretKind kind) {
        return kind != null ? kind.structure().forceScore : 0f;
    }

    /**
     * Fixed per-sortie values make ordinary strafing proportionally dangerous
     * to a small force and progressively less decisive as infantry scale rises.
     * Dagger missiles carry an AoE premium because one pass can damage a whole
     * clustered squad rather than attriting one target at a time.
     */
    static float fighter(FighterProfile profile) {
        if (profile == null) return 0f;
        return switch (profile) {
            case WASP -> 5f;
            case TALON -> 6f;
            case THUNDER -> 7f;
            case BROADSWORD -> 8f;
            case DAGGER -> 14f;
        };
    }

    static float fighterSupport(FlybyRoster roster) {
        if (roster == null || roster.isEmpty()) return 0f;
        float score = 0f;
        for (FighterWing wing : roster.wings) {
            if (wing != null) score += fighter(wing.profile) * wing.sortieCount;
        }
        return score;
    }

    /**
     * Keeps a stable prefix of enemy wings beneath the budget left by the
     * authored infantry and selected mechs. Wings are indivisible commitments:
     * all of a wing's scheduled sorties remain, or the wing does not deploy.
     */
    static FlybyRoster affordableFighterSupport(FlybyRoster candidates,
                                                DefenderRoster roster,
                                                float attackerScore) {
        if (candidates == null || candidates.isEmpty()) return FlybyRoster.EMPTY;
        if (!Float.isFinite(attackerScore)) return candidates;

        float budget = defenderBudget(attackerScore);
        float score = defenders(roster);
        List<FighterWing> selected = new ArrayList<>();
        for (FighterWing wing : candidates.wings) {
            if (wing == null) continue;
            float next = fighter(wing.profile) * wing.sortieCount;
            if (score + next > budget) break;
            selected.add(wing);
            score += next;
        }
        return selected.isEmpty() ? FlybyRoster.EMPTY : new FlybyRoster(selected);
    }

    /**
     * Retains one stable prefix of the map-authored turret candidates beneath
     * the same attacker-derived cap already consumed by infantry, mechs, and
     * enemy fighter support. Mechs keep priority because the roster is resolved
     * first, fighter wings are retained next, and turrets spend only the
     * remaining score. Fortification geometry is left on the map.
     *
     * <p>Posts with every turret removed are omitted so Conquest guard squads
     * are not linked to an emplacement that can never die. Drone hubs are not
     * turrets and survive unchanged.
     */
    static List<DefensePost> affordableDefensePosts(
            List<DefensePost> candidates, DefenderRoster roster,
            float attackerScore) {
        return affordableDefensePosts(candidates, roster, attackerScore, 0f);
    }

    /** Static turrets spend the score left after ground forces and enemy air. */
    static List<DefensePost> affordableDefensePosts(
            List<DefensePost> candidates, DefenderRoster roster,
            float attackerScore, float reservedSupportScore) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        if (!Float.isFinite(attackerScore)) return List.copyOf(candidates);

        int candidateTurrets = turretCount(candidates);
        int retainedTurrets = candidateTurrets;
        float supportScore = turretScore(candidates, retainedTurrets);
        float budget = defenderBudget(attackerScore);
        float rosterScore = defenders(roster) + Math.max(0f, reservedSupportScore);
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
                selected.add(new DefensePost(post.tier, post.layoutId,
                        post.anchorX, post.anchorY,
                        List.copyOf(post.turrets.subList(0, take)),
                        post.droneHubCellX, post.droneHubCellY));
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
                score += spec.structure().forceScore;
            }
        }
        return score;
    }
}
