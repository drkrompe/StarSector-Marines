package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins each {@link OfficerMoodReader#bucket} boundary plus the precedence
 * order DESPERATE → SEASONED → GREEN → STEADY. The static seam keeps these
 * tests free of Sector / Global plumbing — only the pure function is
 * exercised; the wiring inside {@code currentMood()} is verified by playtest.
 *
 * <p>Runway-band scenarios use a stylized 30k/month upkeep so the 6-month
 * DESPERATE boundary lands at 180k and the 12-month SEASONED boundary at
 * 360k — round numbers that are easy to scan in the test bodies.
 *
 * <p>Helper signatures mirror {@code bucket}'s parameter order so the
 * intent of each scenario stays readable inline.
 */
public class OfficerMoodReaderTest {

    private static OfficerMood mood(float credits, float net, float upkeep,
                                    int debt, int prevDebt,
                                    int captains, int ships, int mrbRep) {
        return OfficerMoodReader.bucket(credits, net, upkeep, debt, prevDebt,
                captains, ships, mrbRep);
    }

    // ---------------- DESPERATE ----------------

    @Test
    public void desperate_whenCreditsBelowSixMonthsUpkeep() {
        // Established 8-captain operation, in the black on paper — but cash
        // on hand is under the six-month runway floor. DESPERATE still wins.
        // 100k < 6 * 30k = 180k.
        assertEquals(OfficerMood.DESPERATE,
                mood(/*credits*/ 100_000f, /*net*/ 10_000f, /*upkeep*/ 30_000f,
                     0, 0, 8, 5, 10));
    }

    @Test
    public void desperate_whenTwoConsecutiveDebtMonths() {
        // Wallet looks fine, no upkeep this month — but debt has bitten two
        // months running.
        assertEquals(OfficerMood.DESPERATE,
                mood(500_000f, 0f, 0f, /*debt*/ 5_000, /*prevDebt*/ 3_000,
                     8, 5, 0));
    }

    @Test
    public void desperate_whenBothTriggersFire() {
        // Belt-and-suspenders: noRunway AND cashTrendBleeding both hold.
        // Either one alone would qualify; together still DESPERATE.
        assertEquals(OfficerMood.DESPERATE,
                mood(50_000f, -5_000f, 30_000f,
                     /*debt*/ 8_000, /*prevDebt*/ 4_000,
                     8, 5, 0));
    }

    @Test
    public void notDesperate_whenSingleMonthOfDebt() {
        // One month of debt is a blip, not a trend.
        assertEquals(OfficerMood.STEADY,
                mood(500_000f, 50_000f, 30_000f, /*debt*/ 8_000, /*prevDebt*/ 0,
                     5, 5, 5));
    }

    @Test
    public void notDesperate_whenDebtJustPaidOff() {
        // Player paid off the debt this month — `debt == 0` short-circuits the
        // two-month trend check even though previousDebt is large. Confirms the
        // trend check uses AND, not OR.
        assertEquals(OfficerMood.STEADY,
                mood(500_000f, 5_000f, 30_000f, /*debt*/ 0, /*prevDebt*/ 50_000,
                     5, 5, 0));
    }

    @Test
    public void notDesperate_whenUpkeepIsZero_emptyFirstMonthReport() {
        // First-month empty MonthlyReport: totalUpkeep == 0, so noRunway's
        // upkeep > 0 guard disables that branch. Confirms a flush wallet on
        // a fresh save doesn't spuriously DESPERATE.
        assertEquals(OfficerMood.GREEN,
                mood(50_000f, 0f, /*upkeep*/ 0f, 0, 0,
                     1, 1, 0));
    }

    @Test
    public void desperate_atRunwayBoundary_strictlyLess() {
        // Exactly 6 months of upkeep on hand → NOT desperate (strict < check).
        // One credit under → desperate. Pins the boundary precisely.
        assertEquals(OfficerMood.STEADY,
                mood(/*credits*/ 180_000f, 10_000f, /*upkeep*/ 30_000f,
                     0, 0, 5, 5, 0));
        assertEquals(OfficerMood.DESPERATE,
                mood(/*credits*/ 179_999f, 10_000f, /*upkeep*/ 30_000f,
                     0, 0, 5, 5, 0));
    }

    // ---------------- SEASONED ----------------

    @Test
    public void seasoned_whenAllFourTriggersHold() {
        // 6 captains, positive net, MRB non-negative, 12+ months runway.
        // 500k >= 12 * 30k = 360k.
        assertEquals(OfficerMood.SEASONED,
                mood(500_000f, /*net*/ 20_000f, /*upkeep*/ 30_000f, 0, 0,
                     /*captains*/ 6, 5, /*mrbRep*/ 0));
    }

    @Test
    public void seasoned_atCaptainFloor() {
        // Exactly SEASONED_CAPTAIN_FLOOR (6) qualifies (>= check).
        assertEquals(OfficerMood.SEASONED,
                mood(500_000f, 20_000f, 30_000f, 0, 0,
                     6, 5, 0));
    }

    @Test
    public void notSeasoned_belowCaptainFloor() {
        // 5 captains falls below the floor — STEADY.
        assertEquals(OfficerMood.STEADY,
                mood(500_000f, 20_000f, 30_000f, 0, 0,
                     /*captains*/ 5, 5, 0));
    }

    @Test
    public void notSeasoned_whenMrbRepNegative() {
        assertEquals(OfficerMood.STEADY,
                mood(500_000f, 20_000f, 30_000f, 0, 0,
                     6, 5, /*mrbRep*/ -1));
    }

    @Test
    public void notSeasoned_whenNetIsZero() {
        // Strict positive net required; break-even is STEADY.
        assertEquals(OfficerMood.STEADY,
                mood(500_000f, /*net*/ 0f, 30_000f, 0, 0,
                     6, 5, 5));
    }

    @Test
    public void notSeasoned_whenRunwayBelowTwelveMonths() {
        // Veteran company in the black with good MRB — but only ~8 months of
        // upkeep on hand. STEADY, not SEASONED. Pins the comfortableRunway gate.
        // 240k < 12 * 30k = 360k.
        assertEquals(OfficerMood.STEADY,
                mood(/*credits*/ 240_000f, 20_000f, /*upkeep*/ 30_000f, 0, 0,
                     6, 5, 0));
    }

    // ---------------- GREEN ----------------

    @Test
    public void green_whenFewCaptains() {
        assertEquals(OfficerMood.GREEN,
                mood(500_000f, 10_000f, 30_000f, 0, 0,
                     /*captains*/ 2, 5, 0));
    }

    @Test
    public void green_whenFewShips() {
        // Plenty of captains, plenty of cash — but a single-ship fleet still
        // reads as a brand-new company.
        assertEquals(OfficerMood.GREEN,
                mood(500_000f, 10_000f, 30_000f, 0, 0,
                     5, /*ships*/ 1, 0));
    }

    @Test
    public void notGreen_atCaptainCeiling() {
        // Exactly GREEN_CAPTAIN_CEILING (3) does NOT trigger GREEN
        // (strict less-than). Pins the boundary.
        assertEquals(OfficerMood.STEADY,
                mood(500_000f, 10_000f, 30_000f, 0, 0,
                     /*captains*/ 3, 5, 0));
    }

    // ---------------- STEADY ----------------

    @Test
    public void steady_default() {
        // Comfortable middle: 4 captains, 4 ships, in the black, no debt,
        // 12+ months runway. Below SEASONED's 6-captain floor, above GREEN's
        // ceilings on both axes.
        assertEquals(OfficerMood.STEADY,
                mood(400_000f, 5_000f, 30_000f, 0, 0,
                     4, 4, 0));
    }

    // ---------------- Precedence ----------------

    @Test
    public void desperate_beatsSeasoned() {
        // Both could apply: noRunway triggers AND SEASONED qualifications hold.
        // DESPERATE precedence wins.
        assertEquals(OfficerMood.DESPERATE,
                mood(/*credits*/ 10_000f, 50_000f, /*upkeep*/ 30_000f,
                     0, 0, 8, 5, 10));
    }

    @Test
    public void seasoned_beatsGreen() {
        // Captains plenty, ships at the GREEN ceiling boundary — SEASONED
        // gate wins because its captain floor is met.
        assertEquals(OfficerMood.SEASONED,
                mood(500_000f, 10_000f, 30_000f, 0, 0,
                     6, /*ships*/ 2, 0));
    }

    // ---------------- Save-compat ----------------

    @Test
    public void allEnumOrdinalsRoundTrip() {
        // Ordinals are persisted (toByte/fromByte). If anyone reorders the
        // enum, this stays green only if the mapping is still bijective.
        for (OfficerMood m : OfficerMood.values()) {
            assertEquals(m, OfficerMood.fromByte(m.toByte()));
        }
    }
}
