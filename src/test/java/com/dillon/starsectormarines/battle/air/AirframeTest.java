package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every airframe owes a berth.
 *
 * <p>The two lists are separate enums on purpose — a transport and a fighter
 * are different kinds of aircraft — so nothing but a test keeps them answering
 * the same questions.
 */
class AirframeTest {

    private static List<Airframe> everyAirframe() {
        List<Airframe> all = new ArrayList<>();
        for (ShuttleType type : ShuttleType.values()) all.add(type);
        for (FighterProfile fighter : FighterProfile.values()) all.add(fighter);
        return all;
    }

    /**
     * An airframe with no sprite draws nothing and one with no hull is sized
     * by a fallback, and both of those are a berth that silently looks empty.
     */
    @Test
    void everyAirframeCanBeDrawnAndShot() {
        for (Airframe frame : everyAirframe()) {
            String name = frame.toString();
            assertFalse(frame.spritePath() == null || frame.spritePath().isEmpty(),
                    name + " has no sprite");
            assertFalse(frame.renderHullId() == null || frame.renderHullId().isEmpty(),
                    name + " names no hull to size it");
            assertTrue(frame.maxHp() > 0f, name + " has no hull to shoot at");
        }
    }

    /**
     * A fighter parked on concrete is a lighter thing to write off than any
     * transport.
     *
     * <p>The ladder is authored twice, in two enums that know nothing about
     * each other, and the raid it governs only works if it holds: a fire team
     * that walks onto an apron should get through several fighters in the time
     * a single transport costs it.
     */
    @Test
    void aFighterIsAThinnerTargetThanAnyTransport() {
        float lightestTransport = Float.MAX_VALUE;
        for (ShuttleType type : ShuttleType.values()) {
            lightestTransport = Math.min(lightestTransport, type.maxHp());
        }
        for (FighterProfile fighter : FighterProfile.values()) {
            assertTrue(fighter.maxHp() < lightestTransport,
                    fighter + " parks harder to kill (" + fighter.maxHp()
                            + ") than the lightest transport (" + lightestTransport + ")");
        }
    }

    /**
     * Ground durability follows drawn size.
     *
     * <p>The only thing that matters about a parked aircraft is how much of it
     * there is, so a bomber must not be softer than an interceptor merely
     * because somebody tuned its guns.
     */
    @Test
    void aBiggerFighterIsAHarderOneToBurn() {
        for (FighterProfile a : FighterProfile.values()) {
            for (FighterProfile b : FighterProfile.values()) {
                if (a.visualLengthCells >= b.visualLengthCells) continue;
                assertTrue(a.maxHp() < b.maxHp(),
                        a + " is drawn smaller than " + b + " and parks tougher");
            }
        }
    }
}
