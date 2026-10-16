package com.dillon.starsectormarines.cinematic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptionPagerTest {
    /** One unit a character, so the width is a character count. */
    private static List<List<String>> pages(String text, int width) {
        return CaptionPager.pages(text, String::length, width);
    }

    private static String joined(List<List<String>> pages) {
        return pages.stream().map(p -> String.join(" ", p)).reduce((a, b) -> a + " " + b).orElse("");
    }

    @Test void aShortLineIsOnePageOfOneRow() {
        assertEquals(List.of(List.of("Nothing to declare.")), pages("Nothing to declare.", 40));
    }

    @Test void aLineOverOneRowIsTwoBalancedRowsOnOnePage() {
        var result = pages("Ore dust and tired people aboard", 20);
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).size());
        // Balanced: greedy fill would leave "aboard" alone on the second row.
        assertTrue(Math.abs(result.get(0).get(0).length() - result.get(0).get(1).length()) <= 8, result.toString());
    }

    @Test void twoSentencesThatFitTogetherShareAPageAndOtherwiseSplitAtTheFullStop() {
        assertEquals(1, pages("Well. Somebody knows.", 30).size());
        var apart = pages("Corridor four, mounts cold. Thank you, Approach.", 20);
        assertEquals(2, apart.size());
        assertEquals("Corridor four, mounts cold.", String.join(" ", apart.get(0)));
        assertEquals("Thank you, Approach.", String.join(" ", apart.get(1)));
    }

    @Test void aSentenceTooLongForTwoRowsIsCutAtItsCommasThenPackedAgain() {
        String text = "Declare any cores, harvested organs, recreational drugs or heavy armaments.";
        var result = pages(text, 20);
        assertTrue(result.size() > 1);
        for (var page : result) assertTrue(page.size() <= 2);
        assertEquals(text, joined(result));
    }

    @Test void aClauseWithNoCommaStillBreaksByWidth() {
        String text = "alpha bravo charlie delta echo foxtrot golf hotel india juliet";
        var result = pages(text, 12);
        for (var page : result) {
            assertTrue(page.size() <= 2);
            for (String row : page) assertTrue(row.length() <= 12, row);
        }
        assertEquals(text, joined(result));
    }

    @Test void blankTextHasNoPagesAndSharesSumToOne() {
        assertTrue(pages("   ", 20).isEmpty());
        double[] shares = CaptionPager.shares(pages("One two three. Four five six seven eight nine.", 15));
        double sum = 0;
        for (double share : shares) sum += share;
        assertEquals(1.0, sum, 1e-9);
        assertTrue(shares[1] > shares[0]);
    }
}
