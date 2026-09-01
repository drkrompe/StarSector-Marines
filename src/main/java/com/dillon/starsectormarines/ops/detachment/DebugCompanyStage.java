package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;

/**
 * A point on the campaign arc, as a whole company rather than a personnel
 * flavor. Each constant is a snapshot the player could plausibly be at: how
 * many squads they field, how experienced those marines are, and what
 * supporting arms come along. Squad equipment is randomized independently.
 *
 * <p>Two axes, kept separate: {@link #squads} is size and {@link #plan} is
 * quality. The briefing's squad dial overrides size without touching quality,
 * because "how many marines does this mission need" is a question a fixed
 * ladder cannot answer — a stage is the convenient default, not a cage.
 *
 * <p><b>The top two stages exceed their officer's command cap on purpose.</b>
 * Conquest cycles every selected squad through its reusable descent pair, so
 * the fixture can exercise more than a thousand marines while {@code Rank.COLONEL}
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
     * The company on its first job — one squad of twelve, all Green.
     */
    FIRST_CONTRACT("First Contract", 1, 0, Rank.LIEUTENANT, DebugBilletPlan.STARTER_ISSUE),

    /** Three squads with a veteran NCO core and a three-mech lance. */
    ESTABLISHED("Established", 3, MechSupport.LANCE_SIZE, Rank.CAPTAIN, DebugBilletPlan.SEASONED),

    /** Six squads of veterans under elite sergeants, two lances. */
    VETERAN_COMPANY("Veteran Company", 6, 2 * MechSupport.LANCE_SIZE,
            Rank.MAJOR, DebugBilletPlan.HARDENED),

    /**
     * Reinforced for a major contract — forty-two squads, five hundred and four
     * marines. In Conquest this is fourteen paired waves per shuttle across the
     * three-lane ferry.
     */
    REINFORCED("Reinforced", MissionForceEnvelope.REINFORCED_CONQUEST_SQUADS,
            3 * MechSupport.LANCE_SIZE,
            Rank.LT_COLONEL, DebugBilletPlan.HARDENED),

    /**
     * Everything the company has — eighty-four squads, 1,008 marines. Conquest
     * delivers the entire force through twenty-eight cycles on each of its six
     * reusable lane shuttles.
     */
    FULL_STRENGTH("Full Strength", MissionForceEnvelope.FULL_STRENGTH_CONQUEST_SQUADS,
            6 * MechSupport.LANCE_SIZE,
            Rank.COLONEL, DebugBilletPlan.HARDENED);

    public final String displayName;
    /** Line squads fielded by default. Reserve squads are not modelled — a fixture has no depot. */
    public final int squads;
    /** Mechs the stage brings, used as the debug mech picker's default count. */
    public final int mechs;
    /** Commanding officer's rank. Readout only — debug missions bypass the command cap. */
    public final Rank officerRank;
    /** How this stage's squads are experienced. */
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

    /** One-line briefing summary at a given size: "Reinforced — 42 squads, 504 marines". */
    public String summary(int squadCount) {
        int count = Math.max(0, squadCount);
        long marines = (long) count * MarineSquad.CAPACITY;
        StringBuilder out = new StringBuilder(displayName);
        out.append(" — ").append(count).append(count == 1 ? " squad, " : " squads, ")
                .append(marines).append(" marines");
        if (mechs > 0) out.append(", ").append(mechs).append(" mechs");
        if (count > officerRank.squadCommandCap()) {
            out.append(" · over ").append(officerRank.displayName()).append("'s command");
        }
        return out.toString();
    }
}
