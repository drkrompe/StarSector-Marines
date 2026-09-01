package com.dillon.starsectormarines.ops;

/**
 * Where on the campaign arc a piece of work sits — the <b>scale</b> axis,
 * split out from {@link RiskLevel} so that enum can go back to meaning
 * variance.
 *
 * <p><b>Why.</b> `RiskLevel` had three constants and was deciding map size,
 * lift, enemy count, enemy quality, payout, loot, requirements text and
 * which missions existed. That conflation is why a CONQUEST at LOW risk was
 * 36 defenders and one at HIGH was 320 — the same mission type, one ninth
 * the fight — and why the opening ladder had to bypass the axis entirely and
 * author its own setup rather than "overload generic LOW risk". A mission is
 * now <em>(type, tier, risk)</em>.
 *
 * <p><b>The names are deliberately the debug company's.</b>
 * {@code DebugCompanyStage} describes what the player brings; this describes
 * what the work demands. One vocabulary read from two sides, so a briefing
 * can say "this job wants a Reinforced company and you field Established"
 * instead of leaving the player to infer it from a colour.
 *
 * <p>{@link #defenderBase} is the defender count at {@link RiskLevel#MEDIUM}
 * before {@code MissionType.defenderWeight} is applied — the tier curve that
 * replaces fifteen hand-written numbers in {@code DefenderRoster.totalFor},
 * each of which encoded tier inside type.
 *
 * <p>See `mission-tier-nouns.md`.
 */
public enum OperationTier {

    /** The opening ladder — militia, no air, a finite enemy. One green squad. */
    FIRST_CONTRACT("First Contract", 14, 3, 1),

    /** Ordinary contract work for a company that has found its feet. */
    ESTABLISHED("Established", 44, 8, 3),

    /** A real operation. Where today's non-CONQUEST "HIGH risk" work landed. */
    VETERAN("Veteran", 105, 18, 6),

    /** Multi-officer work — past what one Major may command. */
    REINFORCED("Reinforced", 175, 28, 17),

    /** The top of the ladder. CONQUEST lives here; nothing outgrows it. */
    FULL_STRENGTH("Full Strength", 280, 40, 34);

    public final String displayName;
    /** Defenders at {@link RiskLevel#MEDIUM}, before the mission type's weight. */
    public final int defenderBase;
    /** Lift the work is written for, in drops, before the mission type's weight. */
    public final int drops;
    /** Ordinary-operation squad baseline; mission shape may raise it substantially. */
    public final int squadsDemanded;

    OperationTier(String displayName, int defenderBase, int drops, int squadsDemanded) {
        this.displayName = displayName;
        this.defenderBase = defenderBase;
        this.drops = drops;
        this.squadsDemanded = squadsDemanded;
    }

    public boolean atLeast(OperationTier floor) {
        return floor == null || ordinal() >= floor.ordinal();
    }

    /** Zero is reserved for contracts written before production tiers persisted. */
    public byte toPersistedByte() {
        return (byte) (ordinal() + 1);
    }

    /** Returns {@code null} for the legacy/unset sentinel or an invalid value. */
    public static OperationTier fromPersistedByte(byte encoded) {
        int ordinal = (encoded & 0xFF) - 1;
        OperationTier[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    /** The lowest tier this type may be offered at, never below {@code floor}. */
    public static OperationTier clampTo(OperationTier tier, OperationTier floor) {
        OperationTier resolved = tier != null ? tier : ESTABLISHED;
        if (floor == null) return resolved;
        return resolved.ordinal() < floor.ordinal() ? floor : resolved;
    }

    /**
     * Compatibility bridge for the paths that still only know a risk level —
     * the narrow {@code BattleSetup} overloads used by headless tests and
     * previews, which have no mission behind them.
     *
     * <p><b>Temporary.</b> Every caller that has a real mission should read
     * {@code Mission.tier} instead; this exists so the migration did not have
     * to touch twenty factory overloads at once. Deleting it is the marker
     * that the split is finished.
     */
    public static OperationTier forRisk(RiskLevel risk) {
        if (risk == null) return ESTABLISHED;
        switch (risk) {
            case LOW:    return FIRST_CONTRACT;
            case HIGH:   return VETERAN;
            case MEDIUM:
            default:     return ESTABLISHED;
        }
    }
}
