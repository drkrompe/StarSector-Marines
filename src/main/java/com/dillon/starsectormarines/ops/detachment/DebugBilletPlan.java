package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.MarineSquad;

/**
 * How one squad's twelve billets are manned and equipped — the quality axis
 * of a {@link DebugCompanyStage}, separated from the size axis so the two can
 * vary independently.
 *
 * <p>Split out when the stage ladder grew past three points: five stages each
 * carrying their own copy of a billet plan was five copies of the same three
 * patterns. A stage now names a plan and a size, and the briefing's squad
 * dial overrides the size without touching the quality.
 *
 * <p><b>{@code null} means "leave the campaign's own auto-issue alone"</b>,
 * not "issue nothing" — that is how {@link #STARTER_ISSUE} stays a faithful
 * copy of a new game's opening complement rather than a reconstruction of it.
 *
 * <p>See `c12-the-debug-company.md`.
 */
public enum DebugBilletPlan {

    /** A green squad in whatever a new campaign hands out. Nothing is overridden. */
    STARTER_ISSUE {
        @Override public int experienceXp(int billet) { return 0; }
        @Override public MarineWeapon primary(int billet) { return null; }
        @Override public EquipmentGrade grade(int billet) { return null; }
        @Override public MarineSecondary secondary(int billet) { return null; }
        @Override public MarineArmorPattern armor(int billet) { return null; }
    },

    /**
     * A veteran sergeant over regulars, with a green tail. The tail is
     * deliberate: a company that has been growing carries recent hires, and a
     * squad of uniformly-experienced marines is not a shape the campaign
     * actually produces.
     */
    SEASONED {
        @Override public int experienceXp(int billet) {
            if (billet == 0) return 400;                 // Sergeant — squad leader
            if (billet % TEAM == 0) return 200;          // the other two team leads
            if (billet >= REPLACEMENTS) return 40;       // recent replacements
            return 130;
        }
        @Override public MarineWeapon primary(int billet) { return standardPrimary(billet); }
        @Override public EquipmentGrade grade(int billet) {
            return billet == MARKSMAN ? EquipmentGrade.MILSPEC : EquipmentGrade.SERVICE;
        }
        @Override public MarineSecondary secondary(int billet) { return antiArmor(billet); }
        @Override public MarineArmorPattern armor(int billet) {
            return MarineArmorPattern.ARMY_GREEN;
        }
    },

    /** An elite sergeant over veterans, Milspec throughout, masterwork marksman. */
    HARDENED {
        @Override public int experienceXp(int billet) {
            if (billet == 0) return 900;                 // Elite sergeant
            if (billet % TEAM == 0) return 500;
            if (billet >= REPLACEMENTS) return 200;
            return 400;
        }
        @Override public MarineWeapon primary(int billet) { return standardPrimary(billet); }
        @Override public EquipmentGrade grade(int billet) {
            return billet == MARKSMAN ? EquipmentGrade.MASTERWORK : EquipmentGrade.MILSPEC;
        }
        @Override public MarineSecondary secondary(int billet) { return antiArmor(billet); }
        @Override public MarineArmorPattern armor(int billet) {
            return MarineArmorPattern.RED_ELITE;
        }
    };

    /** Billets from here up are recent replacements — the green tail every squad carries. */
    private static final int REPLACEMENTS = MarineSquad.CAPACITY - 2;
    /** Local alias so the billet plans read in fire teams. */
    private static final int TEAM = MarineSquad.TEAM_SIZE;
    /** The squad's designated marksman. */
    private static final int MARKSMAN = 3;
    /** The squad's single anti-armor billet — one per squad, decided in C7. */
    private static final int ANTI_ARMOR = MarineSquad.CAPACITY - 1;

    private static MarineWeapon standardPrimary(int billet) {
        if (billet == 1) return MarineWeapon.SMG;
        if (billet == MARKSMAN) return MarineWeapon.DMR;
        return MarineWeapon.PULSE_RIFLE;
    }

    private static MarineSecondary antiArmor(int billet) {
        return billet == ANTI_ARMOR ? MarineSecondary.ROCKET_LAUNCHER : null;
    }

    /** Experience for one billet, keyed on position within the squad (0-based). */
    public abstract int experienceXp(int billet);

    /** Primary weapon, or {@code null} to keep the campaign auto-issue. */
    public abstract MarineWeapon primary(int billet);

    /** Grade for {@link #primary}, or {@code null} to keep the campaign auto-issue. */
    public abstract EquipmentGrade grade(int billet);

    /** Secondary, or {@code null} for none. */
    public abstract MarineSecondary secondary(int billet);

    /** Armor, or {@code null} to keep the campaign auto-issue. */
    public abstract MarineArmorPattern armor(int billet);
}
