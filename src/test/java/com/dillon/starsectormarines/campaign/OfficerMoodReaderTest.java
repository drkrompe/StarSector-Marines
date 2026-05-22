package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins each {@link OfficerMoodReader#bucket} boundary plus the precedence
 * order DESPERATE → SEASONED → GREEN → STEADY. The static seam keeps these
 * tests free of Sector / Global plumbing — only the pure function is
 * exercised; the wiring inside {@code currentMood()} is verified by playtest.
 *
 * <p>Helper signatures mirror {@code bucket}'s parameter order so the
 * intent of each scenario stays readable inline.
 */
public class OfficerMoodReaderTest {

    /** Steady-state benchmark: established, modestly profitable, no debt. */
    private static OfficerMood mood(float credits, float net, float upkeep,
                                    int debt, int prevDebt,
                                    int captains, int ships, int mrbRep) {
        return OfficerMoodReader.bucket(credits, net, upkeep, debt, prevDebt,
                captains, ships, mrbRep);
    }

    @Test
    public void desperate_whenCreditsBelowOneMonthUpkeep() {
        // Established 6-captain veteran company, in the black, but the wallet
        // has dropped below one month of running costs — DESPERATE still wins.
        assertEquals(OfficerMood.DESPERATE,
                mood(/*credits*/ 5_000f, /*net*/ 10_000f, /*upkeep*/ 25_000f,
                     0, 0, 8, 5, 10));
    }

    @Test
    public void desperate_whenTwoConsecutiveDebtMonths() {
        // Cash on hand looks fine, no upkeep this month — but the debt
        // trend has bitten two months running.
        assertEquals(OfficerMood.DESPERATE,
                mood(100_000f, 0f, 0f, /*debt*/ 5_000, /*prevDebt*/ 3_000,
                     8, 5, 0));
    }

    @Test
    public void notDesperate_whenSingleMonthOfDebt() {
        // One month of debt is a blip, not a trend — fall through to SEASONED
        // (the other triggers all hold).
        assertEquals(OfficerMood.SEASONED,
                mood(500_000f, 50_000f, 30_000f, /*debt*/ 8_000, /*prevDebt*/ 0,
                     8, 5, 5));
    }

    @Test
    public void notDesperate_whenUpkeepIsZero_emptyFirstMonthReport() {
        // First-month empty MonthlyReport: totalUpkeep == 0, so credits < 0
        // is the only way to trip the noRunway branch — and credits can't go
        // negative in vanilla. Confirms the empty-report case doesn't
        // spuriously DESPERATE a flush wallet.
        assertEquals(OfficerMood.GREEN,
                mood(50_000f, 0f, /*upkeep*/ 0f, 0, 0,
                     1, 1, 0));
    }

    @Test
    public void seasoned_whenAllThreeTriggersHold() {
        assertEquals(OfficerMood.SEASONED,
                mood(500_000f, /*net*/ 20_000f, 100_000f, 0, 0,
                     /*captains*/ 6, 5, /*mrbRep*/ 0));
    }

    @Test
    public void notSeasoned_whenMrbRepNegative() {
        // Six captains and profitable — but MRB has soured. Falls through to
        // STEADY (not GREEN; captain count is above the GREEN ceiling).
        assertEquals(OfficerMood.STEADY,
                mood(500_000f, 20_000f, 100_000f, 0, 0,
                     6, 5, /*mrbRep*/ -1));
    }

    @Test
    public void notSeasoned_whenNetIsZero() {
        // Strict positive net required for SEASONED; break-even is STEADY.
        assertEquals(OfficerMood.STEADY,
                mood(500_000f, /*net*/ 0f, 100_000f, 0, 0,
                     6, 5, 5));
    }

    @Test
    public void green_whenFewCaptains() {
        assertEquals(OfficerMood.GREEN,
                mood(500_000f, 10_000f, 30_000f, 0, 0,
                     /*captains*/ 2, 5, 0));
    }

    @Test
    public void green_whenFewShips() {
        // Plenty of captains, plenty of cash — but a single-ship fleet still
        // reads as a brand-new company to the officer.
        assertEquals(OfficerMood.GREEN,
                mood(500_000f, 10_000f, 30_000f, 0, 0,
                     5, /*ships*/ 1, 0));
    }

    @Test
    public void steady_default() {
        // Comfortable middle: 4 captains, 4 ships, in the black, no debt,
        // non-negative MRB. Below SEASONED's 6-captain floor, above GREEN's
        // ceilings on both axes.
        assertEquals(OfficerMood.STEADY,
                mood(200_000f, 5_000f, 30_000f, 0, 0,
                     4, 4, 0));
    }

    @Test
    public void desperate_beatsSeasoned() {
        // Both could apply: noRunway triggers AND SEASONED qualifications hold.
        // Precedence puts DESPERATE first.
        assertEquals(OfficerMood.DESPERATE,
                mood(/*credits*/ 1_000f, 50_000f, /*upkeep*/ 100_000f,
                     0, 0, 8, 5, 10));
    }

    @Test
    public void seasoned_beatsGreen() {
        // Ship count == GREEN ceiling (not strictly below). Confirms the
        // GREEN branch uses strict-less-than, not less-or-equal — exactly
        // 2 ships at 6 captains is SEASONED, not GREEN.
        assertEquals(OfficerMood.SEASONED,
                mood(500_000f, 10_000f, 30_000f, 0, 0,
                     6, /*ships*/ 2, 0));
    }

    @Test
    public void allEnumOrdinalsRoundTrip() {
        // Defensive: ordinals are persisted (toByte/fromByte). If anyone
        // reorders the enum, this test stays green only if the mapping is
        // still bijective — but a save-compat breakage would be obvious in
        // hand-review at the same time.
        for (OfficerMood m : OfficerMood.values()) {
            assertEquals(m, OfficerMood.fromByte(m.toByte()));
        }
    }
}
