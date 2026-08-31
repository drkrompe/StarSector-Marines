package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.HullFootprintResolver;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
     * Ground durability is a role ladder, and the hull a fighter is drawn from
     * has nothing to do with it.
     *
     * <p>This used to assert the opposite, against a second authored length
     * that existed only to make the assertion pass and that nothing else ever
     * read. The hulls the game actually ships do not rank that way: order the
     * six by real drawn length and it runs Wasp, Talon, Broadsword, Dagger,
     * Longbow, Thunder, while the authored structure on them runs 25, 30,
     * <b>45</b>, 40, 38, <b>35</b>. The Broadsword is a heavy fighter — armour
     * and role rather than sprite length — and the Thunder is the longest hull
     * on the list and nearly the softest thing on the apron.
     *
     * <p>So what is asked here is the thing that could go wrong quietly:
     * whether somebody has re-derived toughness from how much aircraft there
     * is. The sizes come off the real specs, which is why every hull is checked
     * for being measured first — unprimed they are all one length, and a
     * comparison between them would be a comparison of nothing.
     *
     * <p>Drawn size is not idle. It decides how easily a shot finds the
     * aircraft, through {@link Airframe#targetRadiusCells}. It is simply a
     * different fact from how much killing the aircraft takes.
     */
    @Test
    void groundDurabilityIsARoleLadderRatherThanAFunctionOfHullSize() {
        FighterProfile toughest = null;
        FighterProfile longestHull = null;
        for (FighterProfile fighter : FighterProfile.values()) {
            assertTrue(HullFootprintResolver.isMeasured(fighter.hullId),
                    fighter + " is standing in at a fallback size rather than its own hull's");
            if (toughest == null || fighter.maxHp() > toughest.maxHp()) toughest = fighter;
            if (longestHull == null || drawnLengthCells(fighter) > drawnLengthCells(longestHull)) {
                longestHull = fighter;
            }
        }

        assertEquals(FighterProfile.BROADSWORD, toughest,
                "the heavy fighter is meant to be the toughest thing on an apron; "
                        + toughest + " parks tougher");
        assertNotEquals(longestHull, toughest,
                "the toughest fighter is also the longest hull, so the ladder now tracks "
                        + "drawn size — which is a re-balance of the roster, not a tidy-up");
        assertTrue(longestHull.maxHp() < toughest.maxHp(),
                longestHull + " is the longest hull and no longer parks softer than "
                        + toughest);
    }

    private static float drawnLengthCells(FighterProfile fighter) {
        return HullFootprintResolver.visualLengthCells(fighter.hullId);
    }
}
