package com.dillon.starsectormarines.battle.world.gen.ship;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a hull's form out of the polygon the game collides against.
 *
 * <p>What matters is that the shape survives the conversion: bow at the bow,
 * asymmetry kept, and proportions that describe the ship rather than the
 * numbers she happens to be written in.
 */
class HullOutlineTest {

    /** A slender hull: fine bow, full midships, tapered stern. Bow at +x, port at +y. */
    private static final float[] SHIP = {
            120f, 0f,
            60f, 30f,
            -40f, 40f,
            -100f, 20f,
            -100f, -20f,
            -40f, -40f,
            60f, -30f };

    @Test
    @DisplayName("the bow is where the hull is finest")
    void theOutlineRunsBowToStern() {
        HullSilhouette outline = HullOutline.fromBounds(SHIP, "test");

        float atBow = outline.portAt(0f) + outline.starboardAt(0f);
        float amidships = outline.portAt(0.5f) + outline.starboardAt(0.5f);
        assertTrue(atBow < amidships,
                "a hull narrows towards the bow, and the outline starts there");
    }

    @Test
    @DisplayName("a hull that is not symmetrical does not come back symmetrical")
    void asymmetryIsKept() {
        // Bulged to port amidships only.
        float[] lopsided = {
                100f, 0f, 0f, 60f, -100f, 10f, -100f, -10f, 0f, -20f };
        HullSilhouette outline = HullOutline.fromBounds(lopsided, "lopsided");

        assertTrue(outline.portAt(0.5f) > outline.starboardAt(0.5f),
                "the bulge is to port and should stay there");
    }

    @Test
    @DisplayName("proportions describe the ship, not the units she is drawn in")
    void aspectIsScaleFree() {
        float[] doubled = new float[SHIP.length];
        for (int i = 0; i < SHIP.length; i++) doubled[i] = SHIP[i] * 2f;

        assertEquals(HullOutline.fromBounds(SHIP, "one").aspect(),
                HullOutline.fromBounds(doubled, "two").aspect(), 1e-5f,
                "the same hull drawn twice as large is the same shape");
    }

    @Test
    @DisplayName("a hull's typical beam fills the deck, and an outlier clips")
    void occupancyIsNormalizedAgainstTheTypicalBeam() {
        HullSilhouette outline = HullOutline.fromBounds(SHIP, "test");

        for (float along = 0f; along <= 1f; along += 0.05f) {
            assertTrue(outline.portAt(along) >= 0f && outline.portAt(along) <= 1f,
                    "occupancy is a fraction of the deck");
            assertTrue(outline.starboardAt(along) >= 0f && outline.starboardAt(along) <= 1f,
                    "occupancy is a fraction of the deck");
        }
        assertTrue(outline.portAt(0.5f) > 0.5f,
                "amidships should use most of the deck's depth, not a sliver of it");
    }

    @Test
    @DisplayName("a polygon that is not a hull is refused rather than invented")
    void adegeneratePolygonHasNoOutline() {
        assertNull(HullOutline.fromBounds(null, "none"));
        assertNull(HullOutline.fromBounds(new float[] { 1f, 1f }, "a point"));
        assertNull(HullOutline.fromBounds(new float[] { 0f, 5f, 0f, -5f, 0f, 0f }, "no length"));
    }
}
