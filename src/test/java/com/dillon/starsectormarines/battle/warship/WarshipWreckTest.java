package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A lost hull's fall: it strikes after the fall time, a hovering hull slides off, a moving one keeps going, and it comes to rest. */
class WarshipWreckTest {

    private static final float DT = 1f / 30f;

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    private static Warship hound(float vx, float vy) throws Exception {
        Warship ship = Warship.fit("hound", "hound_Standard", Faction.DEFENDER, 50f, 50f, 0f);
        ship.serial(3);
        ship.velocityX = vx;
        ship.velocityY = vy;
        return ship;
    }

    private static WarshipWreck.Step runUntilChange(WarshipWreck wreck, WarshipWreck.Step from) {
        for (int i = 0; i < 10_000; i++) {
            WarshipWreck.Step step = wreck.advance(DT);
            if (step != from) return step;
        }
        throw new AssertionError("the wreck never left " + from);
    }

    @Test
    void aHoveringHullSlidesOffAsItFallsAndStrikesAfterTheFallTime() throws Exception {
        Warship ship = hound(0f, 0f);
        WarshipWreck wreck = new WarshipWreck(ship, -1L);
        assertEquals(1f, wreck.lift(), 1e-6f);
        assertEquals(WarshipWreck.Step.STRUCK, runUntilChange(wreck, WarshipWreck.Step.FALLING));
        assertEquals(WarshipWreck.FALL_SECONDS, wreck.struckAt(), 1e-6f);
        assertEquals(0f, wreck.lift(), 1e-6f);
        float slid = (float) Math.hypot(wreck.struckX() - 50f, wreck.struckY() - 50f);
        // Uniform push from rest to the slip speed over the fall: half the slip speed times the fall time.
        assertEquals(0.5f * WarshipWreck.SLIP_SPEED * WarshipWreck.FALL_SECONDS, slid, 0.05f);
    }

    @Test
    void aMovingHullKeepsItsMomentumDownAndSkidsToRest() throws Exception {
        Warship ship = hound(5f, 0f);
        WarshipWreck wreck = new WarshipWreck(ship, -2L);
        runUntilChange(wreck, WarshipWreck.Step.FALLING);
        assertEquals(50f + 5f * WarshipWreck.FALL_SECONDS, wreck.struckX(), 0.05f);
        assertEquals(50f, wreck.struckY(), 1e-3f);
        WarshipWreck.Step step = runUntilChange(wreck, WarshipWreck.Step.SKIDDING);
        assertEquals(WarshipWreck.Step.CAME_TO_REST, step);
        assertEquals(WarshipWreck.Phase.DOWN, wreck.phase());
        // v^2 / 2a on the ground, give or take the tick it struck on.
        float skid = ship.x - wreck.struckX();
        assertEquals(25f / (2f * WarshipWreck.SKID_DECELERATION), skid, 0.3f);
        assertEquals(WarshipWreck.Step.NONE, wreck.advance(DT));
    }

    @Test
    void theSameHullFallsTheSameWayEveryTime() throws Exception {
        WarshipWreck a = new WarshipWreck(hound(0f, 0f), -1L);
        WarshipWreck b = new WarshipWreck(hound(0f, 0f), -1L);
        runUntilChange(a, WarshipWreck.Step.FALLING);
        runUntilChange(b, WarshipWreck.Step.FALLING);
        assertEquals(a.struckX(), b.struckX(), 0f);
        assertEquals(a.struckY(), b.struckY(), 0f);
        assertTrue(a.ship.facingDegrees == b.ship.facingDegrees);
    }

    @Test
    void aHullDrawnBelowTheStandardPresenceLiesNoLargerThanItFlew() throws Exception {
        Warship small = Warship.fit(new StockVariantLoadout("hound", "hound_Standard"), Faction.DEFENDER, 50f, 50f, 0f,
                WarshipScale.HULL_PRESENCE / 3f);
        WarshipWreck wreck = new WarshipWreck(small, -4L);
        runUntilChange(wreck, WarshipWreck.Step.FALLING);
        assertTrue(wreck.bodyScale() <= 1f, "lies at " + wreck.bodyScale());
    }
}
