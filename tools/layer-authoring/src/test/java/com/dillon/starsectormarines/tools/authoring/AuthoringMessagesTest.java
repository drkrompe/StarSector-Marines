package com.dillon.starsectormarines.tools.authoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A dialog is the one part of this tool that cannot be checked by looking at a
 * PNG, and the failure mode is specific: a paragraph laid out on one line makes
 * a dialog as wide as the desktop with its own text running off the edge.
 */
class AuthoringMessagesTest {

    @Test
    void aParagraphIsMeasuredInWrappedLinesNotInOne() {
        String note = "This sheet is RGB with no alpha channel, so slicing finds one piece "
                + "covering the whole sheet. That is correct for a fixed-grid plate: select "
                + "the single piece and use \"Split selected on grid\" at the grid size below.";

        int rows = AuthoringMessages.estimateRows(note);

        assertTrue(rows >= 3, "a 200-character note needs several rows at 72 columns, got " + rows);
        assertTrue(rows <= 8, "it should not be over-estimated either, got " + rows);
    }

    @Test
    void authoredLineBreaksSurviveTheEstimate() {
        assertEquals(3, AuthoringMessages.estimateRows("one\ntwo\nthree"));
        assertEquals(1, AuthoringMessages.estimateRows("short"));
        assertEquals(1, AuthoringMessages.estimateRows(""));
    }

    @Test
    void aFailureIsDescribedWithEnoughToActOn() {
        // Copying "IOException: nope" into a session is not enough to work with;
        // the frames are the part that says where to look.
        Exception failure = new IllegalStateException("no such tileset");

        String described = AuthoringMessages.describe(failure);

        assertTrue(described.startsWith("java.lang.IllegalStateException: no such tileset"), described);
        assertTrue(described.contains("    at "), "the frames are the useful half:\n" + described);
    }

    @Test
    void aWrappedCauseIsNamedRatherThanSwallowed() {
        Exception root = new java.io.IOException("disk is on fire");
        Exception wrapper = new IllegalStateException("Could not read urban.png", root);

        String described = AuthoringMessages.describe(wrapper);

        assertTrue(described.contains("Caused by: java.io.IOException: disk is on fire"), described);
    }

    @Test
    void describingNothingIsNotAFailure() {
        assertEquals("", AuthoringMessages.describe(null));
    }
}
