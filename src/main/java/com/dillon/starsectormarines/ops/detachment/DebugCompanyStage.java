package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;

/**
 * A point on the campaign arc, as a whole company rather than a personnel
 * flavor. Each constant is a snapshot the player could plausibly be at:
 * how many squads they field, how experienced those marines are, what the
 * armory has issued them, and what supporting arms come along.
 *
 * <p>Replaces the per-seat {@code DebugPersonnelPreset}. A preset described
 * the texture of individual marines and had nowhere to hang a squad, which
 * is why a debug mission could not exercise anything
 * {@code c1-fireteam-identity-through-the-drop.md} or
 * {@code c8-lift-capacity-and-multi-pass-drops.md} shipped. A stage is
 * consumed by {@link DebugCompany}, which builds a real
 * {@link com.dillon.starsectormarines.marine.MarineRoster} from it.
 *
 * <p><b>The kit accessors may return {@code null}</b>, meaning "leave the
 * campaign's own auto-issue alone" rather than "issue nothing". That is how
 * {@link #FIRST_CONTRACT} stays honest: it is the new-game opening
 * complement untouched, not a reconstruction of it.
 *
 * <p>See `c12-the-debug-company.md`.
 */
public enum DebugCompanyStage {

    /**
     * The company on its first job — one squad of twelve, all Green, wearing
     * whatever a new campaign issues. Equivalent to
     * {@code bootstrapInitialComplement(MarineSquad.CAPACITY)}, which is why
     * every kit accessor defers.
     */
    FIRST_CONTRACT("First Contract", 1, 0, Rank.LIEUTENANT) {
        @Override public int experienceXp(int billet) { return 0; }
        @Override public MarineWeapon primary(int billet) { return null; }
        @Override public EquipmentGrade grade(int billet) { return null; }
        @Override public MarineSecondary secondary(int billet) { return null; }
        @Override public MarineArmorPattern armor(int billet) { return null; }
    },

    /**
     * Three squads with a Veteran NCO core over Regulars, plus a couple of
     * green replacements per squad and a three-mech lance. The shape most
     * mid-campaign tuning should be measured against.
     */
    ESTABLISHED("Established", 3, MechSupport.LANCE_SIZE, Rank.CAPTAIN) {
        @Override public int experienceXp(int billet) {
            if (billet == 0) return 400;              // Sergeant — squad leader
            if (billet % TEAM == 0) return 200;       // the other two team leads
            if (billet >= REPLACEMENTS) return 40;    // recent replacements
            return 130;
        }
        @Override public MarineWeapon primary(int billet) {
            if (billet == 1) return MarineWeapon.SMG;
            if (billet == 3) return MarineWeapon.DMR;
            return MarineWeapon.PULSE_RIFLE;
        }
        @Override public EquipmentGrade grade(int billet) {
            return billet == 3 ? EquipmentGrade.MILSPEC : EquipmentGrade.SERVICE;
        }
        @Override public MarineSecondary secondary(int billet) {
            return billet == MarineSquad.CAPACITY - 1
                    ? MarineSecondary.ROCKET_LAUNCHER : null;
        }
        @Override public MarineArmorPattern armor(int billet) {
            return MarineArmorPattern.ARMY_GREEN;
        }
    },

    /**
     * Six squads of Veterans under Elite sergeants, Milspec across the board,
     * and two lances. Bounded by lift, not by the roster — a manifest that
     * cannot carry seventy-two marines simply lands the ones that fit.
     */
    VETERAN_COMPANY("Veteran Company", 6, 2 * MechSupport.LANCE_SIZE, Rank.MAJOR) {
        @Override public int experienceXp(int billet) {
            if (billet == 0) return 900;              // Elite sergeant
            if (billet % TEAM == 0) return 500;
            if (billet >= REPLACEMENTS) return 200;
            return 400;
        }
        @Override public MarineWeapon primary(int billet) {
            if (billet == 1) return MarineWeapon.SMG;
            if (billet == 3) return MarineWeapon.DMR;
            return MarineWeapon.PULSE_RIFLE;
        }
        @Override public EquipmentGrade grade(int billet) {
            return billet == 3 ? EquipmentGrade.MASTERWORK : EquipmentGrade.MILSPEC;
        }
        @Override public MarineSecondary secondary(int billet) {
            return billet == MarineSquad.CAPACITY - 1
                    ? MarineSecondary.ROCKET_LAUNCHER : null;
        }
        @Override public MarineArmorPattern armor(int billet) {
            return MarineArmorPattern.RED_ELITE;
        }
    };

    /** Billets from here up are recent replacements — the green tail every squad carries. */
    private static final int REPLACEMENTS = MarineSquad.CAPACITY - 2;
    /** Local alias so the billet plans read in fire teams. */
    private static final int TEAM = MarineSquad.TEAM_SIZE;

    public final String displayName;
    /** Line squads fielded. Reserve squads are not modelled — a fixture has no depot. */
    public final int squads;
    /** Mechs the stage brings, used as the debug mech picker's default count. */
    public final int mechs;
    /** Commanding officer's rank. Readout only today — debug bypasses the command cap. */
    public final Rank officerRank;

    DebugCompanyStage(String displayName, int squads, int mechs, Rank officerRank) {
        this.displayName = displayName;
        this.squads = squads;
        this.mechs = mechs;
        this.officerRank = officerRank;
    }

    /** Experience for one billet, keyed on position within the squad (0-based). */
    public abstract int experienceXp(int billet);

    /** Primary weapon for one billet, or {@code null} to keep the campaign auto-issue. */
    public abstract MarineWeapon primary(int billet);

    /** Grade for {@link #primary}, or {@code null} to keep the campaign auto-issue. */
    public abstract EquipmentGrade grade(int billet);

    /** Secondary for one billet, or {@code null} for none. */
    public abstract MarineSecondary secondary(int billet);

    /** Armor for one billet, or {@code null} to keep the campaign auto-issue. */
    public abstract MarineArmorPattern armor(int billet);

    public int marines() {
        return squads * MarineSquad.CAPACITY;
    }

    public DebugCompanyStage next() {
        DebugCompanyStage[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** One-line briefing summary: "Established — 3 squads, 36 marines, 3 mechs". */
    public String summary() {
        StringBuilder out = new StringBuilder(displayName);
        out.append(" — ").append(squads).append(squads == 1 ? " squad, " : " squads, ")
                .append(marines()).append(" marines");
        if (mechs > 0) out.append(", ").append(mechs).append(" mechs");
        return out.toString();
    }
}
