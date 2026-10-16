package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarshipPlatingTest {

    private static final float RATING = 1750f;
    private static final float HIT_POINTS = 20000f;

    /** A solid 10 x 20 hull: every cell inside, plate two deep, core inside that. */
    private static WarshipPlating box() {
        boolean[] mask = new boolean[10 * 20];
        Arrays.fill(mask, true);
        return WarshipPlating.ofMask(10, 20, mask, RATING, HIT_POINTS);
    }

    private static int cell(WarshipPlating p, int col, int row) {
        return row * p.cols + col;
    }

    @Test
    void aFreshEdgeReadsTheFullRatingAndTheCoreOnlyTheFloor() {
        WarshipPlating p = box();
        assertEquals(RATING, p.localArmour(cell(p, 0, 10)), 1e-3f, "a fresh side reads the rating");
        assertEquals(RATING, p.localArmour(cell(p, 1, 10)), 1e-3f, "the second plate cell too");
        assertFalse(p.isPlated(5, 10));
        assertEquals(WarshipPlating.Section.CORE, p.section(5, 10));
        assertEquals(RATING * WarshipPlating.MIN_ARMOUR_FRACTION, p.localArmour(cell(p, 5, 10)), 1e-3f,
                "a block with no plate in it meets only the floor");
    }

    @Test
    void underTheWearControlAFreshHitTakesWhatTheFlatRuleTook() {
        boolean[] mask = new boolean[10 * 20];
        Arrays.fill(mask, true);
        WarshipPlating p = WarshipPlating.ofMaskWearing(10, 20, mask, RATING, HIT_POINTS);
        float through = p.hit(cell(p, 0, 10), 600f, 12f, 0f);
        assertEquals(Warship.effectiveDamage(600f, 12f, RATING), through, 1e-2f);
    }

    @Test
    void aFreshPlateSoaksAHitAndTheHullTakesItOnceThePlateIsGone() {
        WarshipPlating p = box();
        float flat = Warship.effectiveDamage(600f, 12f, RATING);
        int side = cell(p, 0, 10);
        float first = p.hit(side, 600f, 12f, 0f);
        assertTrue(first < 0.3f * flat, "a fresh plate soaks most of it: " + first + " of " + flat);
        float later = 0f;
        for (int i = 0; i < 20; i++) later = p.hit(side, 600f, 12f, i);
        assertTrue(later > 0.9f * flat, "a stripped plate passes it on: " + later + " against " + flat);
    }

    @Test
    void repeatedHitsStripThePlateAndLetMoreThrough() {
        WarshipPlating p = box();
        int side = cell(p, 0, 10);
        float first = p.hit(side, 100f, 12f, 0f);
        float last = first;
        for (int i = 0; i < 30; i++) last = p.hit(side, 100f, 12f, i);
        assertTrue(p.plate(0, 10) < 0.5f, "the struck plate is going: " + p.plate(0, 10));
        assertTrue(last > first * 1.5f, "a stripped side lets more through: " + first + " then " + last);
        assertEquals(1f, p.plate(9, 10), 1e-6f, "the far side is untouched");
        assertTrue(p.plateLeft(WarshipPlating.Section.PORT) < p.plateLeft(WarshipPlating.Section.STARBOARD));
    }

    @Test
    void aGlancingRoundStillGlancesOnBarePlate() {
        WarshipPlating p = box();
        int side = cell(p, 0, 10);
        for (int i = 0; i < 60; i++) p.hit(side, 600f, 12f, i);
        assertEquals(0f, p.hit(side, 18f, 5f, 99f), "a rifle round cannot hurt the hull however bare the plate");
    }

    @Test
    void aBreachLetsTheNextRoundThroughToDeeperCells() {
        WarshipPlating p = box();
        // From far to port, along row 10.
        float fromU = -2f;
        float v = (10 + 0.5f) / 20f;
        int first = p.entryCell(fromU, v, 0.5f, v);
        assertEquals(cell(p, 0, 10), first, "a round from port meets the port edge");
        for (int i = 0; i < 400 && !p.breached(0, 10); i++) p.hit(p.entryCell(fromU, v, 0.5f, v), 600f, 12f, i);
        assertTrue(p.breached(0, 10), "the struck edge is holed through");
        int next = p.entryCell(fromU, v, 0.5f, v);
        assertTrue(next % p.cols > 0, "the next round goes deeper: col " + next % p.cols);
        assertTrue(p.breachCount() > 0);
    }

    @Test
    void aStrippedSideHoldsUntilItsSectionGivesWay() {
        WarshipPlating p = box();
        int side = cell(p, 0, 10);
        float taken = 0f;
        while (taken + 600f < HIT_POINTS * WarshipPlating.SECTION_BREACH_SHARE * 0.8f) taken += p.hit(side, 600f, 12f, 0f);
        assertTrue(p.plate(0, 10) <= 0.05f && p.structure(0, 10) <= 0f, "the struck cell is spent");
        assertFalse(p.breached(0, 10), "but the port side has not given way yet");
        assertFalse(p.givenWay(WarshipPlating.Section.PORT));
        while (!p.givenWay(WarshipPlating.Section.PORT)) p.hit(side, 600f, 12f, 1f);
        p.hit(side, 600f, 12f, 2f);
        assertTrue(p.breached(0, 10), "once it has, the spent cell is holed through");
    }

    @Test
    void theNoseIsRowZeroAndPortIsTheLeftOfTheSprite() {
        // A sprite 100 wide and 200 tall, centre in the middle; bounds a box over the forward half and the port half.
        float[] bounds = {100f, 50f, 100f, 0f, 0f, 0f, 0f, 50f};
        WarshipPlating p = WarshipPlating.fromHull(bounds, 100f, 200f, 50f, 100f, RATING, HIT_POINTS);
        boolean forwardPort = false;
        for (int r = 0; r < p.rows; r++) {
            for (int c = 0; c < p.cols; c++) {
                if (!p.isHull(c, r)) continue;
                assertTrue(r < p.rows / 2, "hull only forward: row " + r);
                assertTrue(c < p.cols / 2, "hull only to port: col " + c);
                forwardPort = true;
            }
        }
        assertTrue(forwardPort);
    }
}
