package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.battle.mech.MechVariant;

import java.util.List;

/**
 * Defender composition for one battle, derived from {@link MissionType} +
 * {@link RiskLevel} + whether the target planet fields heavy armor. Consumed
 * by {@link BattleSetup#allocateDefenders} to size and stiffen the opposing
 * force.
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
 * <p>Composition shifts with risk: LOW has no mechs, MEDIUM introduces one
 * Bulwark when heavy armor is available, and HIGH replaces the old flat mech
 * count with complementary Bulwark/Hound/Sirocco groups. Infantry ratios also
 * tighten from 70/30 at LOW to 50/40 at HIGH.
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
    public static DefenderRoster forMission(MissionType type, RiskLevel risk, boolean hasHeavyArmor) {
        int total = totalFor(type, risk);
        List<MechVariant> mechVariants = mechVariantsFor(type, risk, hasHeavyArmor);
        int mechs = mechVariants.size();
        // Mechs come out of the total. Elites take their slice of what's left;
        // the rest fills with militia.
        int nonMech = Math.max(0, total - mechs);
        int elites = Math.round(nonMech * eliteRatioFor(risk));
        if (elites > nonMech) elites = nonMech;
        int militia = nonMech - elites;
        return new DefenderRoster(total, elites, mechVariants, militia, patrolSizeFor(risk), risk);
    }

    private static int totalFor(MissionType type, RiskLevel risk) {
        switch (type) {
            case ASSAULT:
                switch (risk) { case LOW: return 16; case MEDIUM: return 50;  case HIGH: return 120; }
                break;
            case SABOTAGE:
                switch (risk) { case LOW: return 12; case MEDIUM: return 30;  case HIGH: return 70;  }
                break;
            case RAID:
                switch (risk) { case LOW: return 14; case MEDIUM: return 38;  case HIGH: return 90;  }
                break;
            case EXTRACTION:
                switch (risk) { case LOW: return 14; case MEDIUM: return 42;  case HIGH: return 100; }
                break;
            case CONQUEST:
                switch (risk) { case LOW: return 36; case MEDIUM: return 120; case HIGH: return 320; }
                break;
        }
        return 12;
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
}
