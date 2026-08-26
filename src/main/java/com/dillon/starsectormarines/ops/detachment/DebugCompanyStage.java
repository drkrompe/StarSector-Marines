package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;

/**
 * A point on the campaign arc, as a whole company rather than a personnel
 * flavor. Each constant is a snapshot the player could plausibly be at: how
 * many squads they field, how experienced those marines are, what the armory
 * has issued them, and what supporting arms come along.
 *
 * <p>Two axes, kept separate: {@link #squads} is size and {@link #plan} is
 * quality. The briefing's squad dial overrides size without touching quality,
 * because "how many marines does this mission need" is a question a fixed
 * ladder cannot answer — a stage is the convenient default, not a cage.
 *
 * <p><b>The top two stages exceed their officer's command cap on purpose.</b>
 * Conquest cycles every selected squad through its reusable descent pair, so
 * the fixture can exercise hundreds of marines while {@code Rank.COLONEL}
 * tops out at 24 squads. {@link #exceedsCommandCap()} reports that rather than
 * hiding it: the mission ladder has outgrown the command ladder, and the
 * fixture is where that shows up first. Debug missions bypass the cap
 * ({@code captainCommandReady} returns true for them), so the stage still
 * deploys.
 *
 * <p>See `c12-the-debug-company.md`.
 */
public enum DebugCompanyStage {

    /**
     * The company on its first job — one squad of twelve, all Green, wearing
     * whatever a new campaign issues. Equivalent to
     * {@code bootstrapInitialComplement(MarineSquad.CAPACITY)}.
     */
    FIRST_CONTRACT("First Contract", 1, 0, Rank.LIEUTENANT, DebugBilletPlan.STARTER_ISSUE),

    /** Three squads with a veteran NCO core and a three-mech lance. */
    ESTABLISHED("Established", 3, MechSupport.LANCE_SIZE, Rank.CAPTAIN, DebugBilletPlan.SEASONED),

    /** Six squads of veterans under elite sergeants, two lances. */
    VETERAN_COMPANY("Veteran Company", 6, 2 * MechSupport.LANCE_SIZE,
            Rank.MAJOR, DebugBilletPlan.HARDENED),

    /**
     * Reinforced for a major contract — seventeen squads, a little over two
     * hundred marines, at the quality a rapidly-grown company actually
     * carries. Sized from play: roughly what a CONQUEST at HIGH risk was
     * reported to need.
     */
    REINFORCED("Reinforced", 17, 3 * MechSupport.LANCE_SIZE,
            Rank.LT_COLONEL, DebugBilletPlan.SEASONED),

    /**
     * Everything the company has — thirty-four squads, four hundred and eight
     * marines. Conquest extends its shuttle cycles to carry the entire force.
     */
    FULL_STRENGTH("Full Strength", 34, 6 * MechSupport.LANCE_SIZE,
            Rank.COLONEL, DebugBilletPlan.HARDENED);

    public final String displayName;
    /** Line squads fielded by default. Reserve squads are not modelled — a fixture has no depot. */
    public final int squads;
    /** Mechs the stage brings, used as the debug mech picker's default count. */
    public final int mechs;
    /** Commanding officer's rank. Readout only — debug missions bypass the command cap. */
    public final Rank officerRank;
    /** How this stage's squads are manned and equipped. */
    public final DebugBilletPlan plan;

    DebugCompanyStage(String displayName, int squads, int mechs,
                      Rank officerRank, DebugBilletPlan plan) {
        this.displayName = displayName;
        this.squads = squads;
        this.mechs = mechs;
        this.officerRank = officerRank;
        this.plan = plan;
    }

    public int marines() {
        return squads * MarineSquad.CAPACITY;
    }

    /**
     * True when the stage fields more squads than its officer may command.
     * Reported, not corrected — see the class doc.
     */
    public boolean exceedsCommandCap() {
        return squads > officerRank.squadCommandCap();
    }

    public DebugCompanyStage next() {
        DebugCompanyStage[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** One-line briefing summary at a given size: "Reinforced — 17 squads, 204 marines". */
    public String summary(int squadCount) {
        int count = Math.max(0, squadCount);
        StringBuilder out = new StringBuilder(displayName);
        out.append(" — ").append(count).append(count == 1 ? " squad, " : " squads, ")
                .append(count * MarineSquad.CAPACITY).append(" marines");
        if (mechs > 0) out.append(", ").append(mechs).append(" mechs");
        if (count > officerRank.squadCommandCap()) {
            out.append(" · over ").append(officerRank.displayName()).append("'s command");
        }
        return out.toString();
    }
}
