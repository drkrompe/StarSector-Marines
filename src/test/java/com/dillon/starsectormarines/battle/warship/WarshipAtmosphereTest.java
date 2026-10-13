package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarshipAtmosphereTest {

    private static final float D = WarshipAtmosphere.DESCENT_SECONDS;
    private static final float C = WarshipAtmosphere.CLIMB_SECONDS;

    @Test
    void aShipThatDidNotEnterFromAboveHoversAtLiftZero() {
        assertEquals(0f, WarshipAtmosphere.lift(-1f, 0f, false));
        assertEquals(1f, WarshipAtmosphere.hullVisibility(-1f, 0f, false));
        assertEquals(0f, WarshipAtmosphere.reentryGlow(-1f));
        assertEquals(0f, WarshipAtmosphere.groundWash(-1f, 0f, false));
        assertTrue(WarshipAtmosphere.ringProgress(-1f) < 0f);
    }

    @Test
    void descentRunsFromTheTopOfTheDeckToTheHoverAndOnlyDownward() {
        assertEquals(1f, WarshipAtmosphere.lift(0f, 0f, false));
        assertEquals(0f, WarshipAtmosphere.lift(D, 0f, false), 1e-6f);
        assertEquals(0f, WarshipAtmosphere.lift(D * 3f, 0f, false));
        float previous = 1f;
        for (float t = 0f; t <= D; t += 0.1f) {
            float lift = WarshipAtmosphere.lift(t, 0f, false);
            assertTrue(lift <= previous + 1e-6f, "lift rose at " + t);
            previous = lift;
        }
    }

    @Test
    void descentSettlesGentlyBecauseItEasesOut() {
        // Most of the height is lost early; the last second barely moves the shadow.
        float early = WarshipAtmosphere.lift(0f, 0f, false) - WarshipAtmosphere.lift(1f, 0f, false);
        float late = WarshipAtmosphere.lift(D - 1f, 0f, false) - WarshipAtmosphere.lift(D, 0f, false);
        assertTrue(early > 4f * late, "early " + early + " late " + late);
    }

    @Test
    void climbRunsBackUpAndGathersPace() {
        assertEquals(0f, WarshipAtmosphere.lift(-1f, 0f, true));
        assertEquals(1f, WarshipAtmosphere.lift(-1f, C, true), 1e-6f);
        float first = WarshipAtmosphere.lift(-1f, 1f, true);
        float last = WarshipAtmosphere.lift(-1f, C, true) - WarshipAtmosphere.lift(-1f, C - 1f, true);
        assertTrue(last > 4f * first);
    }

    @Test
    void shadowLiesFurtherAndSofterTheHigherTheShipIs() {
        float hover = 16f;
        assertEquals(hover, WarshipAtmosphere.altitudeCells(hover, 0f));
        assertEquals(WarshipAtmosphere.DECK_ALTITUDE_CELLS, WarshipAtmosphere.altitudeCells(hover, 1f));
        assertTrue(WarshipAtmosphere.altitudeCells(hover, 0.5f) > hover);
        assertEquals(0f, WarshipAtmosphere.shadowSoftness(0f));
        assertEquals(1f, WarshipAtmosphere.shadowSoftness(1f), 1e-6f);
        assertTrue(WarshipAtmosphere.shadowDensity(1f) < WarshipAtmosphere.shadowDensity(0.3f));
        assertEquals(1f, WarshipAtmosphere.shadowDensity(0f));
    }

    @Test
    void theHullBreaksOutOfTheCloudEarlyAndGoesBackInLate() {
        assertTrue(WarshipAtmosphere.hullVisibility(0f, 0f, false) < 0.2f);
        assertEquals(1f, WarshipAtmosphere.hullVisibility(D * 0.3f, 0f, false), 1e-6f);
        assertEquals(1f, WarshipAtmosphere.hullVisibility(-1f, C * 0.4f, true), 1e-6f);
        assertEquals(0f, WarshipAtmosphere.hullVisibility(-1f, C, true), 1e-6f);
    }

    @Test
    void reentryGlowIsHottestAtTheStartAndGoneAfterItsFadeTime() {
        assertEquals(1f, WarshipAtmosphere.reentryGlow(0f), 1e-6f);
        assertEquals(0f, WarshipAtmosphere.reentryGlow(WarshipAtmosphere.GLOW_SECONDS));
        assertEquals(0f, WarshipAtmosphere.reentryGlow(WarshipAtmosphere.GLOW_SECONDS + 5f));
        float previous = 1f;
        for (float t = 0f; t <= WarshipAtmosphere.GLOW_SECONDS; t += 0.1f) {
            float glow = WarshipAtmosphere.reentryGlow(t);
            assertTrue(glow <= previous + 1e-6f);
            previous = glow;
        }
    }

    @Test
    void cloudClearsAsTheShipComesDown() {
        assertEquals(0f, WarshipAtmosphere.cloudCover(0f));
        assertEquals(1f, WarshipAtmosphere.cloudCover(1f), 1e-6f);
        assertTrue(WarshipAtmosphere.cloudCover(0.6f) > WarshipAtmosphere.cloudCover(0.2f));
    }

    @Test
    void groundWashBuildsOnTheWayDownAndDiesAfterTouchdown() {
        assertEquals(0f, WarshipAtmosphere.groundWash(0f, 0f, false));
        assertTrue(WarshipAtmosphere.groundWash(D * 0.9f, 0f, false) > WarshipAtmosphere.groundWash(D * 0.5f, 0f, false));
        assertEquals(1f, WarshipAtmosphere.groundWash(D, 0f, false), 1e-6f);
        assertEquals(0f, WarshipAtmosphere.groundWash(D + WarshipAtmosphere.WASH_SECONDS, 0f, false), 1e-6f);
        // A lift-off kicks it up and lets it go.
        assertEquals(1f, WarshipAtmosphere.groundWash(-1f, 0f, true), 1e-6f);
        assertEquals(0f, WarshipAtmosphere.groundWash(-1f, WarshipAtmosphere.LIFTOFF_WASH_SECONDS, true), 1e-6f);
    }

    @Test
    void shockRingBeginsNearTouchdownAndWidensWhileThinning() {
        assertTrue(WarshipAtmosphere.ringProgress(D * 0.5f) < 0f);
        float start = WarshipAtmosphere.ringProgress(D * 0.82f);
        assertEquals(0f, start, 1e-6f);
        assertEquals(1f, WarshipAtmosphere.ringProgress(D * 0.82f + WarshipAtmosphere.RING_SECONDS), 1e-6f);
        assertTrue(WarshipAtmosphere.ringRadiusHulls(0.8f) > WarshipAtmosphere.ringRadiusHulls(0.2f));
        assertTrue(WarshipAtmosphere.ringAlpha(0.8f) < WarshipAtmosphere.ringAlpha(0.2f));
        assertEquals(0f, WarshipAtmosphere.ringAlpha(1f));
    }

    @Test
    void climbTrailBuildsFastAndFadesAsTheHullGoesIntoTheDeck() {
        assertEquals(0f, WarshipAtmosphere.climbTrail(3f, false));
        assertEquals(0f, WarshipAtmosphere.climbTrail(0f, true));
        assertTrue(WarshipAtmosphere.climbTrail(1.5f, true) > 0.9f);
        assertEquals(0f, WarshipAtmosphere.climbTrail(C * 1.4f, true), 1e-6f);
    }

    @Test
    void scatterIsStablePerShipAndSpreadAcrossSlots() {
        assertEquals(WarshipAtmosphere.scatter("onslaught", 0, 3), WarshipAtmosphere.scatter("onslaught", 0, 3));
        assertNotEquals(WarshipAtmosphere.scatter("onslaught", 0, 3), WarshipAtmosphere.scatter("paragon", 0, 3));
        float low = 1f;
        float high = 0f;
        for (int i = 0; i < 200; i++) {
            float v = WarshipAtmosphere.scatter("onslaught", 1, i);
            assertTrue(v >= 0f && v < 1f);
            low = Math.min(low, v);
            high = Math.max(high, v);
        }
        assertTrue(low < 0.1f && high > 0.9f, "range " + low + ".." + high);
    }

    @Test
    void surfaceTintsAreDistinctAndRockIsTheBaseline() {
        assertArrayEquals(WarshipAtmosphere.Tint.forPalette(SurfacePalette.ROCK).glow(),
                WarshipAtmosphere.Tint.forPalette(null).glow());
        assertTrue(WarshipAtmosphere.Tint.forPalette(SurfacePalette.FROZEN).glow()[2]
                > WarshipAtmosphere.Tint.forPalette(SurfacePalette.ARID).glow()[2], "ice glows cooler");
        assertNotEquals(WarshipAtmosphere.Tint.forPalette(SurfacePalette.ARID).dust()[0],
                WarshipAtmosphere.Tint.forPalette(SurfacePalette.FROZEN).dust()[0]);
    }
}
