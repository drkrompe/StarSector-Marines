package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void aLargerHullNeverComesDownSofter() {
        ShipClass.HullSize[] sizes = ShipClass.HullSize.values();
        for (int i = 1; i < sizes.length; i++) {
            WarshipWreck.Weight lighter = WarshipWreck.Weight.of(sizes[i - 1]);
            WarshipWreck.Weight heavier = WarshipWreck.Weight.of(sizes[i]);
            assertTrue(heavier.crush() >= lighter.crush() && heavier.skid() >= lighter.skid(), sizes[i] + " damage");
            assertTrue(heavier.penetration() >= lighter.penetration(), sizes[i] + " penetration");
            assertTrue(heavier.structure() >= lighter.structure()
                    && heavier.skidStructure() >= lighter.skidStructure(), sizes[i] + " structure");
            assertTrue(heavier.secondaries() >= lighter.secondaries() && heavier.fires() >= lighter.fires(),
                    sizes[i] + " secondaries and fires");
        }
    }

    @Test
    void secondariesGoOffInTheAirThenRippleDownTheKeelAfterTheStrikeEvenAtRest() throws Exception {
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.DEFENDER, 50f, 50f, 0f);
        ship.serial(5);
        WarshipWreck wreck = new WarshipWreck(ship, -5L);
        WarshipWreck.Weight weight = wreck.weight();
        assertEquals(WarshipWreck.Weight.of(ShipClass.HullSize.CAPITAL), weight);
        int inAir = 0, onGround = 0;
        for (int i = 0; i < 600; i++) {
            float before = wreck.seconds();
            wreck.advance(DT);
            for (WarshipWreck.Secondary blast : wreck.secondariesBetween(before, wreck.seconds())) {
                assertTrue(onHull(ship, blast.u(), blast.v()), "on the hull at " + blast.u() + "," + blast.v());
                assertEquals(before >= WarshipWreck.FALL_SECONDS || wreck.seconds() > WarshipWreck.FALL_SECONDS,
                        blast.grounded(), "a blast is grounded exactly when the hull has struck");
                if (blast.grounded()) onGround++;
                else inAir++;
            }
        }
        assertEquals(WarshipWreck.Phase.DOWN, wreck.phase());
        assertEquals(weight.fallSecondaries(), inAir);
        assertEquals(weight.secondaries(), onGround);
    }

    @Test
    void theKeelRunsFromTheNoseAlongTheFacingAsTheHullIsDrawn() throws Exception {
        Warship ship = hound(0f, 0f);
        WarshipWreck wreck = new WarshipWreck(ship, -6L);
        // Facing 0 draws the nose toward +y, as the renderer rotates the sprite.
        assertTrue(wreck.keelPoint(0f)[1] > wreck.keelPoint(1f)[1] + ship.lengthCells * 0.5f);
        ship.facingDegrees = 90f;
        assertTrue(wreck.keelPoint(0f)[0] < wreck.keelPoint(1f)[0] - ship.lengthCells * 0.5f);
    }

    @Test
    void aLargerHullGoesUpInMoreBlastsWithinTheBounds() {
        assertEquals(WarshipWreck.MIN_BLASTS, WarshipWreck.blastCount(1f));
        assertEquals(WarshipWreck.MAX_BLASTS, WarshipWreck.blastCount(100_000f));
        assertTrue(WarshipWreck.blastCount(600f) > WarshipWreck.blastCount(100f));
    }

    @Test
    void theBlastFieldsLieOnTheHullsShapeSpreadOverItAndTheStrikeRollsOutward() throws Exception {
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.DEFENDER, 50f, 50f, 0f);
        ship.serial(7);
        WarshipWreck wreck = new WarshipWreck(ship, -7L);
        List<WarshipWreck.Blast> burst = wreck.blastsBetween(0f, WarshipWreck.BURST_FIELD_SECONDS);
        List<WarshipWreck.Blast> strike = wreck.blastsBetween(WarshipWreck.FALL_SECONDS,
                WarshipWreck.FALL_SECONDS + WarshipWreck.STRIKE_FIELD_SECONDS);
        assertTrue(burst.size() >= 2, "burst " + burst.size());
        assertTrue(strike.size() >= WarshipWreck.MIN_BLASTS, "strike " + strike.size());
        assertTrue(wreck.blastsBetween(WarshipWreck.BURST_FIELD_SECONDS, WarshipWreck.FALL_SECONDS).isEmpty());
        int[] quarters = new int[4];
        for (WarshipWreck.Blast blast : strike) {
            assertTrue(onHull(ship, blast.u(), blast.v()), "on the hull at " + blast.u() + "," + blast.v());
            assertTrue(blast.radius() >= WarshipWreck.MIN_BLAST_RADIUS * 0.85f - 1e-6f, "radius " + blast.radius());
            quarters[(blast.u() < 0.5f ? 0 : 1) + (blast.v() < 0.5f ? 0 : 2)]++;
        }
        // Spread over the hull, not heaped in a line: every quarter of the sprite's box has some.
        for (int q = 0; q < 4; q++) assertTrue(quarters[q] > 0, "quarter " + q + " of " + strike.size());
        float mid = WarshipWreck.FALL_SECONDS + WarshipWreck.STRIKE_FIELD_SECONDS * 0.5f;
        for (WarshipWreck.Blast inner : wreck.blastsBetween(WarshipWreck.FALL_SECONDS, mid)) {
            for (WarshipWreck.Blast outer : wreck.blastsBetween(mid, WarshipWreck.FALL_SECONDS + 1f)) {
                assertTrue(WarshipWreck.reach(inner) <= WarshipWreck.reach(outer) + 1e-6f);
            }
        }
    }

    /** Whether {@code (u, v)} is one of the hull's armour-grid cells (or inside its box, for a hull without a grid). */
    private static boolean onHull(Warship ship, float u, float v) {
        WarshipPlating plating = ship.plating();
        if (plating == null) return u >= 0f && u <= 1f && v >= 0f && v <= 1f;
        int cell = plating.cellAt(u, v);
        return cell >= 0 && plating.isHull(cell % plating.cols, cell / plating.cols);
    }
}
