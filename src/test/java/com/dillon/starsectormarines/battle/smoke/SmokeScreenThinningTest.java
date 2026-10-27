package com.dillon.starsectormarines.battle.smoke;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.marine.SmokeGrenadeSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A long-lasting screen: it holds its radius, then gives back its rim before it is gone, and the pot in the catalog is one. */
class SmokeScreenThinningTest {

    /** Ten seconds, the last four thinning; a radius of three cells. */
    private static final SmokeGrenadeSpec SCREEN = new SmokeGrenadeSpec(8f, 0.5f, 0.5f, 1f, 3f, 10f, 4f);

    @Test
    void aScreenHoldsItsFullRadiusUntilTheThinBegins() {
        NavigationGrid grid = open(21, 9);
        SmokeFieldService smoke = new SmokeFieldService(grid);
        smoke.launch(1L, Faction.MARINE, 2.5f, 4.5f, 10.5f, 4.5f, SCREEN);
        smoke.tick(0.5f);
        SmokeFieldService.SmokeFieldView whole = smoke.activeFields().get(0);
        assertEquals(3f, whole.radius());
        assertEquals(3f, whole.fullRadius());
        assertEquals(6f, whole.coverSeconds(), 1e-4f, "ten seconds of life less four of thin");

        smoke.tick(5.9f);
        assertEquals(3f, smoke.activeFields().get(0).radius(), "still whole just before the thin");
        assertFalse(grid.hasLineOfSight(6, 4, 14, 4));
    }

    @Test
    void aThinningScreenGivesBackItsRimBeforeItsCoreAndThenClears() {
        NavigationGrid grid = open(21, 9);
        SmokeFieldService smoke = new SmokeFieldService(grid);
        smoke.launch(1L, Faction.MARINE, 2.5f, 4.5f, 10.5f, 4.5f, SCREEN);
        smoke.tick(0.5f);
        smoke.tick(8.5f);
        // 1.5 s left of a 4 s thin: the radius is partway from whole toward the core fraction.
        SmokeFieldService.SmokeFieldView thinning = smoke.activeFields().get(0);
        assertTrue(thinning.radius() < 3f && thinning.radius() > 3f * SmokeFieldService.CORE_FRACTION);
        assertEquals(0f, thinning.coverSeconds());
        assertEquals(3f, thinning.fullRadius());
        assertTrue(grid.hasTransientOpacity(10, 4), "the core still stands");
        assertFalse(grid.hasTransientOpacity(12, 4), "the rim, two and a half cells out, has been given back");

        smoke.tick(1.4f);
        float last = smoke.activeFields().get(0).radius();
        assertEquals(3f * SmokeFieldService.CORE_FRACTION, last, 0.2f, "the core is what is left at the last moment");
        assertTrue(grid.hasTransientOpacity(10, 4));

        smoke.tick(0.2f);
        assertTrue(smoke.activeFields().isEmpty());
        assertFalse(grid.hasTransientOpacity(), "every cell is given back exactly once, none held after the cloud is gone");
        assertTrue(grid.hasLineOfSight(6, 4, 14, 4));
    }

    @Test
    void radiusOnlyEverContracts() {
        NavigationGrid grid = open(21, 9);
        SmokeFieldService smoke = new SmokeFieldService(grid);
        smoke.launch(1L, Faction.MARINE, 2.5f, 4.5f, 10.5f, 4.5f, SCREEN);
        smoke.tick(0.5f);
        float previous = Float.MAX_VALUE;
        for (int i = 0; i < 95; i++) {
            smoke.tick(0.1f);
            if (smoke.activeFields().isEmpty()) break;
            float radius = smoke.activeFields().get(0).radius();
            assertTrue(radius <= previous + 1e-6f, "a thinning screen never grows back");
            previous = radius;
        }
    }

    @Test
    void aCloudWithNoThinStaysWholeAndSimplyGoes() {
        NavigationGrid grid = open(21, 9);
        SmokeFieldService smoke = new SmokeFieldService(grid);
        SmokeGrenadeSpec grenade = new SmokeGrenadeSpec(8f, 0.5f, 0.5f, 1f, 2.25f, 4f);
        smoke.launch(1L, Faction.MARINE, 2.5f, 4.5f, 10.5f, 4.5f, grenade);
        smoke.tick(0.5f);
        smoke.tick(3.4f);
        SmokeFieldService.SmokeFieldView view = smoke.activeFields().get(0);
        assertEquals(2.25f, view.radius());
        assertEquals(view.remaining(), view.coverSeconds(), 1e-4f, "with no thin it is whole to the end");
        smoke.tick(0.7f);
        assertTrue(smoke.activeFields().isEmpty());
    }

    private static NavigationGrid open(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
