package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.battle.mech.MechVariant;

import java.util.ArrayList;
import java.util.List;

/**
 * Defender composition for one battle, derived from {@link MissionType} +
 * {@link OperationTier} + {@link RiskLevel} + whether the target planet fields
 * heavy armor. Consumed by {@link BattleSetup#allocateDefenders} to size and
 * stiffen the opposing force.
 *
 * <p>Per-mission flavor:
 * <ul>
 *   <li><b>ASSAULT</b> — straight-up fight. Mid count, mix of garrisons + patrols.</li>
 *   <li><b>SABOTAGE</b> — covert. Lightest count overall; even HIGH skips lances
 *       (one lone mech max) since a heavy mech parade contradicts the flavor.</li>
 *   <li><b>RAID</b> — mid-light count, mid lance.</li>
 *   <li><b>EXTRACTION</b> — mid-heavy; the gravity well is the target PoI itself.</li>
 *   <li><b>CONQUEST</b> — marquee. Biggest count, double-lance at HIGH (6 mechs in
 *       two coordinated lances).</li>
 * </ul>
 *
 * <p>Risk authors candidate composition: LOW has no mechs, MEDIUM introduces
 * one Bulwark when heavy armor is available, and HIGH replaces the old flat
 * mech count with complementary Bulwark/Hound/Sirocco groups. The candidate
 * mechs must then fit beneath the combined attacker's
 * {@link BattleForceScore}; this keeps small unsupported operations playable
 * while allowing allied or otherwise reinforced attacks to face armor.
 * Infantry ratios also tighten from 70/30 at LOW to 50/40 at HIGH.
 */
public final class DefenderRoster {

    /** A mech lance bundles this many HEAVY_MECH units into one garrison so they spawn together rather than dispersed. */
    public static final int MECH_LANCE_SIZE = 3;

    /** Total defender count across all squads (garrisons + patrols + mechs). */
    public final int totalCount;
    /** MARINE_RED stiffening regulars. The rest of non-mech defenders are MILITIA. */
    public final int eliteCount;
    /** Number of concrete profiles in {@link #mechVariants}. */
    public final int mechCount;
    /** Concrete, deterministic chassis composition consumed by defender spawn. */
    public final List<MechVariant> mechVariants;
    /** MILITIA filler — the bulk of the force. {@code totalCount = eliteCount + mechCount + militiaCount}. */
    public final int militiaCount;
    /** Members per non-garrison patrol squad. Scales with risk so 200-defender HIGH maps don't end up with 60+ three-member patrols. */
    public final int patrolSquadSize;
    /** Mission pressure used to roll individual gear quality and experience. */
    public final RiskLevel risk;

    private DefenderRoster(int totalCount, int eliteCount, List<MechVariant> mechVariants,
                           int militiaCount, int patrolSquadSize, RiskLevel risk) {
        this.totalCount = totalCount;
        this.eliteCount = eliteCount;
        this.mechVariants = List.copyOf(mechVariants);
        this.mechCount = mechVariants.size();
        this.militiaCount = militiaCount;
        this.patrolSquadSize = patrolSquadSize;
        this.risk = risk != null ? risk : RiskLevel.LOW;
    }

    /**
     * Build the roster for one mission. {@code hasHeavyArmor} gates mech
     * presence — driven upstream by whether the target planet's industries
     * produce or demand heavy armaments.
     */
    /**
     * Compatibility bridge — prefer the tier-aware overload. Applies the
     * type's floor, so a CONQUEST reached through a legacy path still gets a
     * late-game force rather than a beginner-sized one.
     * See {@code OperationTier.forRisk}.
     */
    public static DefenderRoster forMission(MissionType type, RiskLevel risk, boolean hasHeavyArmor) {
        return forMission(type,
                OperationTier.clampTo(OperationTier.forRisk(risk),
                        type != null ? type.tierFloor : null),
                risk, hasHeavyArmor);
    }

    public static DefenderRoster forMission(MissionType type, OperationTier tier,
                                            RiskLevel risk, boolean hasHeavyArmor) {
        return forMission(type, tier, risk, hasHeavyArmor,
                Float.POSITIVE_INFINITY);
    }

    /**
     * Battle-start roster with mech candidates capped by the attacking force.
     * The score covers player and allied waves; tier continues to own the base
     * infantry count, so this does not scale the whole encounter to whatever
     * the player happened to bring.
     */
    public static DefenderRoster forMission(MissionType type, OperationTier tier,
                                            RiskLevel risk, boolean hasHeavyArmor,
                                            float attackerScore) {
        int total = totalFor(type, tier, risk);
        List<MechVariant> mechVariants = affordableMechVariants(total, risk,
                mechVariantsFor(type, risk, hasHeavyArmor), attackerScore);
        int mechs = mechVariants.size();
        // Mechs come out of the total. Elites take their slice of what's left;
        // the rest fills with militia.
        int nonMech = Math.max(0, total - mechs);
        int elites = eliteCountFor(nonMech, risk);
        int militia = nonMech - elites;
        return new DefenderRoster(total, elites, mechVariants, militia, patrolSizeFor(risk), risk);
    }

    /** Smallest defending force worth generating — below this a battle has no shape. */
    private static final int MINIMUM_DEFENDERS = 8;

    /**
     * Defenders for an operation: the tier's curve, weighted by what the
     * mission type is, nudged by risk.
     *
     * <p>Replaces fifteen hand-written numbers indexed by (type, risk). Those
     * encoded tier <em>inside</em> type — CONQUEST/HIGH's 320 was the top of
     * the ladder while ASSAULT/HIGH's 120 was mid — so no type but CONQUEST
     * could reach late-game size and CONQUEST could not avoid being offered
     * at beginner size. Tier now says how big and type says what shape.
     */
    static int totalFor(MissionType type, OperationTier tier, RiskLevel risk) {
        OperationTier resolved = tier != null ? tier : OperationTier.ESTABLISHED;
        float weight = type != null ? type.defenderWeight : 0.6f;
        float variance = risk != null ? risk.forceMult : 1f;
        return Math.max(MINIMUM_DEFENDERS,
                Math.round(resolved.defenderBase * weight * variance));
    }

    private static float eliteRatioFor(RiskLevel risk) {
        if (risk == null) return 0.30f;
        switch (risk) {
            case LOW:    return 0.30f;
            case MEDIUM: return 0.35f;
            case HIGH:   return 0.40f;
        }
        return 0.30f;
    }

    private static int patrolSizeFor(RiskLevel risk) {
        if (risk == null) return 5;
        switch (risk) {
            case LOW:    return 3;
            case MEDIUM: return 5;
            case HIGH:   return 7;
        }
        return 5;
    }

    /**
     * Mech profile breakdown:
     * <ul>
     *   <li>{@code !hasHeavyArmor} or {@code LOW} risk: 0.</li>
     *   <li>{@code MEDIUM}: one Bulwark.</li>
     *   <li>{@code HIGH}: a complementary Bulwark/Hound/Sirocco group.
     *       CONQUEST gets two groups; SABOTAGE stays at one Hound for covert
     *       flavor.</li>
     * </ul>
     */
    private static List<MechVariant> mechVariantsFor(MissionType type, RiskLevel risk,
                                                     boolean hasHeavyArmor) {
        if (!hasHeavyArmor || risk == null || risk == RiskLevel.LOW) return List.of();
        if (risk == RiskLevel.MEDIUM) return List.of(MechVariant.BULWARK);
        if (type == MissionType.SABOTAGE) return List.of(MechVariant.HOUND);
        if (type == MissionType.CONQUEST) {
            return List.of(
                    MechVariant.BULWARK, MechVariant.HOUND, MechVariant.SIROCCO,
                    MechVariant.BULWARK, MechVariant.HOUND, MechVariant.SIROCCO);
        }
        return List.of(MechVariant.BULWARK, MechVariant.HOUND, MechVariant.SIROCCO);
    }

    /**
     * Keeps the stable candidate prefix and removes the least essential rear
     * profiles until the whole defender roster fits the attacker-derived cap.
     * A HIGH mixed group therefore loses Sirocco, then Hound, then its anchor;
     * it never degenerates into a random lone specialist.
     */
    private static List<MechVariant> affordableMechVariants(
            int total, RiskLevel risk, List<MechVariant> candidates,
            float attackerScore) {
        if (!Float.isFinite(attackerScore) || candidates.isEmpty()) return candidates;
        List<MechVariant> selected = new ArrayList<>(candidates);
        float budget = BattleForceScore.defenderBudget(attackerScore);
        while (!selected.isEmpty()
                && defenderScore(total, risk, selected) > budget) {
            selected.remove(selected.size() - 1);
        }
        return List.copyOf(selected);
    }

    private static float defenderScore(int total, RiskLevel risk,
                                       List<MechVariant> mechVariants) {
        int nonMech = Math.max(0, total - mechVariants.size());
        int elites = eliteCountFor(nonMech, risk);
        return BattleForceScore.defenders(nonMech - elites, elites, mechVariants);
    }

    private static int eliteCountFor(int nonMech, RiskLevel risk) {
        return Math.min(nonMech, Math.round(nonMech * eliteRatioFor(risk)));
    }
}
