package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;

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
 * <p>Composition holds across mission types and shifts with risk: LOW skews
 * conscript-heavy (70% militia / 30% regulars), MEDIUM tightens (60/35 + 1
 * mech if applicable), HIGH stiffens further (50/40 + mech lance).
 */
public final class DefenderRoster {

    /** A mech lance bundles this many HEAVY_MECH units into one garrison so they spawn together rather than dispersed. */
    public static final int MECH_LANCE_SIZE = 3;

    /** Total defender count across all squads (garrisons + patrols + mechs). */
    public final int totalCount;
    /** MARINE_RED stiffening regulars. The rest of non-mech defenders are MILITIA. */
    public final int eliteCount;
    /** HEAVY_MECH count. Always a multiple of {@link #MECH_LANCE_SIZE} when {@code lanceCount > 0}, otherwise 0 or 1. */
    public final int mechCount;
    /** MILITIA filler — the bulk of the force. {@code totalCount = eliteCount + mechCount + militiaCount}. */
    public final int militiaCount;
    /** Members per non-garrison patrol squad. Scales with risk so 200-defender HIGH maps don't end up with 60+ three-member patrols. */
    public final int patrolSquadSize;

    private DefenderRoster(int totalCount, int eliteCount, int mechCount,
                           int militiaCount, int patrolSquadSize) {
        this.totalCount = totalCount;
        this.eliteCount = eliteCount;
        this.mechCount = mechCount;
        this.militiaCount = militiaCount;
        this.patrolSquadSize = patrolSquadSize;
    }

    /**
     * Build the roster for one mission. {@code hasHeavyArmor} gates mech
     * presence — driven upstream by whether the target planet's industries
     * produce or demand heavy armaments.
     */
    public static DefenderRoster forMission(MissionType type, RiskLevel risk, boolean hasHeavyArmor) {
        int total = totalFor(type, risk);
        int mechs = mechCountFor(type, risk, hasHeavyArmor);
        // Mechs come out of the total. Elites take their slice of what's left;
        // the rest fills with militia.
        int nonMech = Math.max(0, total - mechs);
        int elites = Math.round(nonMech * eliteRatioFor(risk));
        if (elites > nonMech) elites = nonMech;
        int militia = nonMech - elites;
        return new DefenderRoster(total, elites, mechs, militia, patrolSizeFor(risk));
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
     * Mech count breakdown:
     * <ul>
     *   <li>{@code !hasHeavyArmor} or {@code LOW} risk: 0.</li>
     *   <li>{@code MEDIUM}: 1 lone mech (no lance).</li>
     *   <li>{@code HIGH}: lance(s) of {@link #MECH_LANCE_SIZE}. CONQUEST gets 2 lances (6),
     *       SABOTAGE stays at 1 lone mech for covert flavor, everything else 1 lance (3).</li>
     * </ul>
     */
    private static int mechCountFor(MissionType type, RiskLevel risk, boolean hasHeavyArmor) {
        if (!hasHeavyArmor || risk == RiskLevel.LOW) return 0;
        if (risk == RiskLevel.MEDIUM) return 1;
        // HIGH risk
        if (type == MissionType.CONQUEST) return MECH_LANCE_SIZE * 2;
        if (type == MissionType.SABOTAGE) return 1;
        return MECH_LANCE_SIZE;
    }
}
