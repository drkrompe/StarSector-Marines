package com.dillon.starsectormarines.campaign.ruins;

/**
 * The old colony's defence batteries ({@code ruins-expedition.md}, The vault and its prize; owner pick
 * {@code ruins-theme-defences} a). A site built round a valuable prize (an AI core or a colony item) still has the
 * guns that once kept it, on the approach outside, wired to the site's power hall.
 *
 * <ul>
 *   <li>They are dark while the hall is cold, so they are dark when the company lands.</li>
 *   <li>Restarting the hall, which powers the vault, wakes them too, unless a junction cuts their branch: power for the
 *       prize against guns on the way out.</li>
 *   <li>They fire on anyone. The occupant never commands them.</li>
 *   <li>The grade sets how many.</li>
 * </ul>
 *
 * <p>Placement on the ground and the guns themselves are the battle's; this class holds the rules.
 */
public final class RuinsBatteries {

    /** What a battery is doing. */
    public enum State {
        /** The hall is cold: the battery does nothing. */
        DARK,
        /** The hall runs and its branch is fed: it fires on anyone in range. */
        AWAKE,
        /** The hall runs but a junction cuts the battery's branch. */
        CUT
    }

    private RuinsBatteries() {}

    /** How many batteries a site keeps: none unless its prize is a core or a colony item, then one more per grade. */
    public static int count(RuinsGrade grade, RuinsVaultPrize prize) {
        if (grade == null || prize == null || !keepsBatteries(prize)) return 0;
        return grade.ordinal() + 1;
    }

    /** Whether a site built round this prize keeps the old colony's batteries. */
    public static boolean keepsBatteries(RuinsVaultPrize prize) {
        return prize != null && prize.breaksWhenForced();
    }

    /**
     * A battery's state from the site's power.
     *
     * @param hallRunning whether the power hall has been restarted
     * @param branchFed   whether the junctions feed the batteries' branch
     */
    public static State state(boolean hallRunning, boolean branchFed) {
        if (!hallRunning) return State.DARK;
        return branchFed ? State.AWAKE : State.CUT;
    }
}
