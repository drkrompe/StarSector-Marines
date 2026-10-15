package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.world.gen.MarketGarrison;

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
 * mechs ordinarily fit beneath the combined attacker's
 * {@link BattleForceScore}; enemy fighter wings and static turret candidates
 * spend the score left after that roster is resolved. Conquest is the authored
 * set-piece exception: setup retains all of its eligible support independently
 * of the attacking manifest.
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
        return forMission(type, tier, risk, hasHeavyArmor, attackerScore, null);
    }

    /** Faction-aware heavy-support selection; force size and budget remain mission-owned. */
    public static DefenderRoster forMission(MissionType type, OperationTier tier,
                                            RiskLevel risk, boolean hasHeavyArmor,
                                            float attackerScore,
                                            GroundRosterProfile groundRoster) {
        return forMission(type, tier, risk, hasHeavyArmor, attackerScore, groundRoster, MarketGarrison.NONE);
    }

    /**
     * As above, with the target market's own garrison estimate. A market's {@link MarketGarrison#quality} sets the
     * elite share of the infantry in place of the risk curve, so a military world is more professional than a mining
     * outpost at the same risk, and its {@link MarketGarrison#strength} places the headcount inside the tier's band
     * ({@link #bandedTotal}). {@link MarketGarrison#NONE} keeps the risk curve and the tier's whole count.
     */
    public static DefenderRoster forMission(MissionType type, OperationTier tier,
                                            RiskLevel risk, boolean hasHeavyArmor,
                                            float attackerScore,
                                            GroundRosterProfile groundRoster,
                                            MarketGarrison garrison) {
        int total = bandedTotal(totalFor(type, tier, risk), garrison);
        float eliteRatio = eliteRatio(risk, garrison);
        List<MechVariant> mechVariants = affordableMechVariants(total, eliteRatio,
                bandedMechs(mechVariantsFor(type, risk, hasHeavyArmor, groundRoster), garrison), attackerScore);
        int mechs = mechVariants.size();
        // Mechs come out of the total. Elites take their slice of what's left;
        // the rest fills with militia.
        int nonMech = Math.max(0, total - mechs);
        int elites = eliteCountFor(nonMech, eliteRatio);
        int militia = nonMech - elites;
        return new DefenderRoster(total, elites, mechVariants, militia, patrolSizeFor(risk), risk);
    }

    /**
     * A composition stated outright rather than derived from a mission, for a controlled trial: so many regulars, so
     * much militia, these mechs, patrols of this size. {@code risk} still rolls individual gear and experience.
     */
    public static DefenderRoster explicit(int eliteCount, int militiaCount, List<MechVariant> mechVariants,
                                          int patrolSquadSize, RiskLevel risk) {
        if (eliteCount < 0 || militiaCount < 0 || patrolSquadSize < 1) {
            throw new IllegalArgumentException("defender counts must be non-negative and patrols at least one strong");
        }
        List<MechVariant> mechs = mechVariants != null ? mechVariants : List.of();
        return new DefenderRoster(eliteCount + militiaCount + mechs.size(), eliteCount, mechs, militiaCount,
                patrolSquadSize, risk);
    }

    /** Smallest defending force worth generating — below this a battle has no shape. */
    private static final int MINIMUM_DEFENDERS = 8;

    /**
     * The least of the tier's count a market-backed operation fields: the weakest garrison at a tier still turns out
     * this share. UNTUNED: chosen so no fight grows and none collapses; change it once play settles how much lighter an
     * outpost should be than a fortress world at the same tier.
     */
    static final float BAND_FLOOR = 0.7f;

    /**
     * Vanilla ground-defence strength worth one defender, for placing a market inside its tier's band and for sizing a
     * garrison faced whole. UNTUNED: at this rate an Established contract fills at about a size 5 colony and a Full
     * Strength one at about a fortified size 7 world.
     */
    public static final float STRENGTH_PER_DEFENDER = 4f;

    /**
     * The tier's count, placed inside its band by what the market keeps on its ground. A contract is an operation the
     * market's owner meets with the share of its garrison the situation calls for, never the whole of it, so the tier
     * is the ceiling and {@link #BAND_FLOOR} of it the floor; the market's strength picks the place between. A
     * garrison able to fill the tier's count fields all of it. No market backing the battle keeps the tier's count.
     */
    static int bandedTotal(int tierTotal, MarketGarrison garrison) {
        if (garrison == null || garrison.isNone()) return tierTotal;
        float fill = Math.min(1f, garrison.strength() / (tierTotal * STRENGTH_PER_DEFENDER));
        float share = BAND_FLOOR + (1f - BAND_FLOOR) * fill;
        return Math.max(Math.min(tierTotal, MINIMUM_DEFENDERS), Math.round(tierTotal * share));
    }

    /**
     * The least of the risk curve's mech candidates a market-backed operation keeps: a market with no heavy lean at all
     * still keeps this share. UNTUNED, on the headcount band's reasoning: the risk curve is the ceiling, so nothing
     * grows, and a market's armour picks the place between.
     */
    static final float MECH_BAND_FLOOR = 0.5f;

    /** Heavy lean at which a market keeps every mech candidate the risk curve offers. UNTUNED. */
    static final float HEAVY_LEAN_FOR_FULL_MECHS = 0.25f;

    /**
     * The risk curve's mech candidates, kept in proportion to the market's heavy lean. The candidates are an ordered
     * prefix (anchor first), so the kept ones are the front of it, as {@link #affordableMechVariants} keeps them. No
     * market backing the battle keeps them all; a garrison with any candidates keeps at least one.
     */
    static List<MechVariant> bandedMechs(List<MechVariant> candidates, MarketGarrison garrison) {
        if (candidates.isEmpty() || garrison == null || garrison.isNone()) return candidates;
        float fill = Math.min(1f, garrison.heavy() / HEAVY_LEAN_FOR_FULL_MECHS);
        float share = MECH_BAND_FLOOR + (1f - MECH_BAND_FLOOR) * fill;
        int kept = Math.max(1, (int) Math.ceil(candidates.size() * share - 1e-4f));
        return List.copyOf(candidates.subList(0, Math.min(candidates.size(), kept)));
    }

    /**
     * The whole garrison as a headcount, for an operation that faces all of it rather than a tier's share: a player
     * invasion. Never fewer than the smallest force worth generating. See {@code nexerelin-nouns.md}.
     */
    public static int wholeGarrisonCount(MarketGarrison garrison) {
        if (garrison == null || garrison.isNone()) return MINIMUM_DEFENDERS;
        return Math.max(MINIMUM_DEFENDERS, Math.round(garrison.strength() / STRENGTH_PER_DEFENDER));
    }

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

    /** The market's garrison quality when a market backs the battle, otherwise the risk curve. */
    static float eliteRatio(RiskLevel risk, MarketGarrison garrison) {
        return garrison != null && !garrison.isNone() ? garrison.quality() : eliteRatioFor(risk);
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
        return mechVariantsFor(type, risk, hasHeavyArmor, null);
    }

    private static List<MechVariant> mechVariantsFor(MissionType type, RiskLevel risk,
                                                     boolean hasHeavyArmor,
                                                     GroundRosterProfile groundRoster) {
        if (!hasHeavyArmor || risk == null || risk == RiskLevel.LOW) return List.of();
        if (groundRoster != null) {
            int count = risk == RiskLevel.MEDIUM ? 1
                    : type == MissionType.SABOTAGE ? 1
                    : type == MissionType.CONQUEST ? 6 : 3;
            return groundRoster.heavySupportCycle(count);
        }
        if (risk == RiskLevel.MEDIUM) return List.of(MechVariant.BULWARK);
        if (type == MissionType.SABOTAGE) return List.of(MechVariant.HOUND);
        if (type == MissionType.CONQUEST) {
            return List.of(MechVariant.BULWARK, MechVariant.HOUND, MechVariant.SIROCCO,
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
            int total, float eliteRatio, List<MechVariant> candidates,
            float attackerScore) {
        if (!Float.isFinite(attackerScore) || candidates.isEmpty()) return candidates;
        List<MechVariant> selected = new ArrayList<>(candidates);
        float budget = BattleForceScore.defenderBudget(attackerScore);
        while (!selected.isEmpty()
                && defenderScore(total, eliteRatio, selected) > budget) {
            selected.remove(selected.size() - 1);
        }
        return List.copyOf(selected);
    }

    private static float defenderScore(int total, float eliteRatio,
                                       List<MechVariant> mechVariants) {
        int nonMech = Math.max(0, total - mechVariants.size());
        int elites = eliteCountFor(nonMech, eliteRatio);
        return BattleForceScore.defenders(nonMech - elites, elites, mechVariants);
    }

    private static int eliteCountFor(int nonMech, float eliteRatio) {
        return Math.min(nonMech, Math.round(nonMech * eliteRatio));
    }

    /**
     * The infantry in the order squads draw it: elites spread evenly through the bulk rather than queued ahead of it.
     * Squads fill from the front, so a queue of every elite first gave the top posts all-elite squads and every other
     * post an all-bulk one. Spread, every squad mixes the two in about the roster's proportion, each soldier rolling
     * their own kit from the faction's issue. The spread is centred, so a short squad near the front still draws an
     * elite when the share calls for one.
     */
    public List<GroundRosterProfile.ForceTier> infantryOrder() {
        return infantryOrder(eliteCount, militiaCount);
    }

    static List<GroundRosterProfile.ForceTier> infantryOrder(int elites, int bulk) {
        int total = Math.max(0, elites) + Math.max(0, bulk);
        List<GroundRosterProfile.ForceTier> order = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            boolean elite = elitesBefore(i + 1, elites, total) > elitesBefore(i, elites, total);
            order.add(elite ? GroundRosterProfile.ForceTier.ELITE : GroundRosterProfile.ForceTier.BULK);
        }
        return order;
    }

    /** How many of the first {@code position} places hold an elite: a rounded, centred running share. */
    private static long elitesBefore(int position, int elites, int total) {
        return ((long) position * elites + total / 2) / total;
    }
}
