package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.evacuation.BlastImpulseRules.Response;
import com.dillon.starsectormarines.battle.evacuation.BruteChargeRules.Kind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.HullImpact;
import com.dillon.starsectormarines.battle.vehicle.HullSlide;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.weapon.BlastImpulse;
import com.dillon.starsectormarines.marine.ArmorClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlastImpulseRulesTest {

    @Test
    void theShoveFallsOffToNothingAtTheRadiusAndASharpBlastFallsFaster() {
        BlastImpulse even = new BlastImpulse(1f, 2f, 1f), sharp = new BlastImpulse(1f, 2f, 2f);
        assertEquals(1f, even.at(0f), 1e-6f);
        assertEquals(.5f, even.at(1f), 1e-6f);
        assertEquals(.25f, sharp.at(1f), 1e-6f);
        assertEquals(0f, even.at(2f));
        assertEquals(0f, even.at(5f));
        assertTrue(even.at(.5f) > even.at(1.5f));
        assertThrows(IllegalArgumentException.class, () -> new BlastImpulse(0f, 1f, 1f));
    }

    @Test
    void aSingleShoveStepsOrStaggersAndOnlyEnoughOfThemKnocksDown() {
        assertEquals(Response.NONE, BlastImpulseRules.respond(BlastImpulseRules.MIN_DV * .5f, 0f));
        assertEquals(Response.STEP, BlastImpulseRules.respond(BlastImpulseRules.STAGGER_DV * .5f, 0f));
        assertEquals(Response.STAGGER, BlastImpulseRules.respond(BlastImpulseRules.STAGGER_DV, 0f));
        assertEquals(Response.TUMBLE, BlastImpulseRules.respond(BlastImpulseRules.TUMBLE_DV, 0f));
        // The same stagger-sized shove on a body that has already taken most of a knockdown puts it down.
        assertEquals(Response.TUMBLE, BlastImpulseRules.respond(BlastImpulseRules.STAGGER_DV,
                BlastImpulseRules.TUMBLE_DV - BlastImpulseRules.STAGGER_DV));
    }

    /** Lands {@code dv} every {@code gapSeconds}, {@code rounds} times, as {@code BlastImpulseSystem} does. */
    private static Response salvo(int rounds, float dv, float gapSeconds) {
        float taken = 0f;
        Response last = Response.NONE;
        for (int i = 0; i < rounds; i++) {
            float before = BlastImpulseRules.faded(taken, i == 0 ? 0f : gapSeconds);
            last = BlastImpulseRules.respond(dv, before);
            if (last == Response.TUMBLE) return last;
            if (last != Response.NONE) taken = before + dv;
        }
        return last;
    }

    @Test
    void theRoundsOfASalvoAddUpWhereSeparateShotsDoNot() {
        float dv = BlastImpulseRules.STAGGER_DV;
        assertEquals(Response.TUMBLE, salvo(5, dv, .1f), "five rounds 0.1 s apart, a rack's salvo");
        assertEquals(Response.STAGGER, salvo(5, dv, 5f), "five rounds 5 s apart");
        // Pure and deterministic: the same sequence always ends the same way.
        assertEquals(salvo(4, .9f, .2f), salvo(4, .9f, .2f));
        assertEquals(1f, BlastImpulseRules.faded(1f, 0f));
        assertTrue(BlastImpulseRules.faded(1f, BlastImpulseRules.MEMORY) < .4f);
    }

    @Test
    void heavierBodiesResistAndTheHeavyClassesStand() {
        assertTrue(BlastImpulseRules.infantryTonnes(ArmorClass.POWER_SUIT)
                > 2f * BlastImpulseRules.infantryTonnes(ArmorClass.STANDARD));
        // A shove that knocks a marine down only staggers a power suit.
        float impulse = BlastImpulseRules.TUMBLE_DV * BlastImpulseRules.INFANTRY_TONNES;
        assertEquals(Response.TUMBLE, BlastImpulseRules.respond(
                impulse / BlastImpulseRules.infantryTonnes(ArmorClass.STANDARD), 0f));
        assertEquals(Response.STAGGER, BlastImpulseRules.respond(
                impulse / BlastImpulseRules.infantryTonnes(ArmorClass.POWER_SUIT), 0f));
        assertTrue(BlastImpulseRules.moves(Kind.INFANTRY));
        assertTrue(BlastImpulseRules.moves(Kind.LIGHT_VEHICLE));
        assertTrue(BlastImpulseRules.moves(Kind.LIGHT_MECH));
        assertFalse(BlastImpulseRules.moves(Kind.HEAVY_VEHICLE));
        assertFalse(BlastImpulseRules.moves(Kind.HEAVY_MECH));
        assertFalse(BlastImpulseRules.moves(Kind.EMPLACEMENT));
    }

    @Test
    void aHullIsPushedAwayFromTheBlastTurnedByAnOffCentreOneAndALightOneGoesFurther() {
        // A 2 x 1 hull facing north (+y); a blast a cell off its left side.
        HullImpact.Result light = HullImpact.blast(0f, 0f, 0f, 1f, .5f, 2.5f, -1.5f, 0f, 1f, 1f, 0f);
        HullImpact.Result heavy = HullImpact.blast(0f, 0f, 0f, 1f, .5f, 12f, -1.5f, 0f, 1f, 1f, 0f);
        assertEquals(1f / 2.5f, light.hullVx(), 1e-5f);
        assertEquals(0f, light.hullVy(), 1e-5f);
        assertEquals(0f, light.hullOmega(), 1e-6f, "abeam its centre, it does not turn");
        assertTrue(light.hullVx() > 4f * heavy.hullVx());
        HullImpact.Result forward = HullImpact.blast(0f, 0f, 0f, 1f, .5f, 2.5f, -1.5f, .8f, 1f, 1f, 0f);
        assertTrue(forward.hullOmega() < 0f, "pushed right ahead of its centre, it turns clockwise");
        // A burst on the deck pushes toward the centre; one dead on it along the fallback.
        HullImpact.Result deck = HullImpact.blast(0f, 0f, 0f, 1f, .5f, 2.5f, 0f, 0f, 1f, 0f, 1f);
        assertEquals(1f / 2.5f, deck.hullVy(), 1e-5f);
        assertEquals(0f, deck.hullOmega(), 1e-6f);
    }

    @Test
    void aShovedHullStopsAtAWall() {
        NavigationGrid grid = new NavigationGrid(12, 6);
        for (int y = 0; y < 6; y++) for (int x = 0; x < 12; x++) if (x != 8) grid.setWalkableFloor(x, y);
        GroundBody body = new GroundBody() {
            @Override public void tick(float carrotX, float carrotY, float targetSpeed, float dt) { }
        };
        body.teleport(6.5f, 3f, 0f);
        VehicleType type = VehicleType.SCOUT_CAR;
        // Shoved east, hard, toward the wall in column 8.
        HullSlide.apply(body, HullImpact.blast(body.x, body.y, 0f, type.visualLengthCells / 2f,
                type.visualWidthCells / 2f, type.massTonnes(), body.x - 2f, body.y, 20f, 1f, 0f));
        for (int i = 0; i < 120; i++) HullSlide.tick(body, type, grid, .5f, 1f / 30f);
        assertTrue(body.x + .5f <= 8f + 1e-3f, "stopped short of the wall at " + body.x);
        assertTrue(body.x > 6.5f, "but pushed toward it");
    }
}
