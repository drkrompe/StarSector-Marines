package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.warship.MissileWindow.Kind;
import com.dillon.starsectormarines.battle.warship.MissileWindow.Role;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissileWindowTest {

    @Test
    void aTorpedoWaitsForAnOpeningAShieldWouldOtherwiseStop() {
        assertNull(MissileWindow.launch(Role.TORPEDO, EnumSet.of(Kind.SHIELD_UP)));
        assertNull(MissileWindow.launch(Role.TORPEDO, EnumSet.of(Kind.SHIELD_UP, Kind.HIGH_FLUX, Kind.SALVO)));
        assertEquals(Kind.SHIELD_DOWN, MissileWindow.launch(Role.TORPEDO, EnumSet.of(Kind.SHIELD_DOWN, Kind.WORN)));
        assertEquals(Kind.FLANK, MissileWindow.launch(Role.TORPEDO, EnumSet.of(Kind.FLANK, Kind.SHIELD_UP)));
        assertEquals(Kind.UNSHIELDED, MissileWindow.launch(Role.TORPEDO, EnumSet.of(Kind.UNSHIELDED)));
    }

    @Test
    void aFinisherTakesAWornHullButNotAFreshUnshieldedOne() {
        assertEquals(Kind.WORN, MissileWindow.launch(Role.FINISHER, EnumSet.of(Kind.WORN, Kind.SHIELD_UP)));
        assertNull(MissileWindow.launch(Role.FINISHER, EnumSet.of(Kind.UNSHIELDED)));
        assertEquals(Kind.OVERLOAD, MissileWindow.launch(Role.FINISHER, EnumSet.of(Kind.OVERLOAD, Kind.WORN)));
    }

    @Test
    void anAntiShieldMissileGoesIntoARaisedShieldAndIsHeldWhileItIsDown() {
        assertEquals(Kind.HIGH_FLUX, MissileWindow.launch(Role.ANTI_SHIELD, EnumSet.of(Kind.HIGH_FLUX, Kind.SHIELD_UP)));
        assertEquals(Kind.SHIELD_UP, MissileWindow.launch(Role.ANTI_SHIELD, EnumSet.of(Kind.SHIELD_UP)));
        assertNull(MissileWindow.launch(Role.ANTI_SHIELD, EnumSet.of(Kind.SHIELD_DOWN, Kind.HIGH_FLUX)));
        assertNull(MissileWindow.launch(Role.ANTI_SHIELD, EnumSet.of(Kind.OVERLOAD)));
    }

    @Test
    void aShieldLoweredAtRestIsNoOpeningButASabotMayGoIntoIt() {
        assertTrue(!Kind.SHIELD_IDLE.opening());
        assertNull(MissileWindow.launch(Role.TORPEDO, EnumSet.of(Kind.SHIELD_IDLE)));
        assertNull(MissileWindow.launch(Role.FINISHER, EnumSet.of(Kind.SHIELD_IDLE)));
        assertNull(MissileWindow.launch(Role.SATURATION, EnumSet.of(Kind.SHIELD_IDLE)));
        assertEquals(Kind.SHIELD_IDLE, MissileWindow.launch(Role.ANTI_SHIELD, EnumSet.of(Kind.SHIELD_IDLE)));
    }

    @Test
    void saturationGoesWithASalvoOrAnyOpeningButNotIntoAQuietShield() {
        assertNull(MissileWindow.launch(Role.SATURATION, EnumSet.of(Kind.SHIELD_UP)));
        assertNull(MissileWindow.launch(Role.SATURATION, EnumSet.of(Kind.WORN, Kind.SHIELD_UP)));
        assertEquals(Kind.SALVO, MissileWindow.launch(Role.SATURATION, EnumSet.of(Kind.SALVO, Kind.SHIELD_UP)));
        assertEquals(Kind.STRIKE, MissileWindow.launch(Role.SATURATION, EnumSet.of(Kind.STRIKE, Kind.SALVO)));
    }

    @Test
    void aGeneralLauncherFiresWhateverIsOpen() {
        assertEquals(Kind.NONE, MissileWindow.launch(Role.GENERAL, EnumSet.of(Kind.NONE)));
        assertEquals(Kind.SHIELD_UP, MissileWindow.label(EnumSet.of(Kind.SHIELD_UP)));
        assertTrue(Kind.SALVO.opening());
        assertTrue(!Kind.UNSHIELDED.opening() && !Kind.SHIELD_UP.opening());
    }

    @Test
    void aHullFlightLeavesAlongTheTubeAndComesStraightIn() {
        MissileFlightSpec spec = new MissileFlightSpec(MissileFlightSpec.Guidance.HOMING, 100f, 400f, 600f, 0f, 120f,
                0f, 0f, 100f, null, null, 0f, 0f, false, 0f, 0f, null);
        // Fired east from a tube laid north (heading 0 points up the y axis): it swings north before it comes in.
        MissilePath path = MissilePath.atHull(spec, 0f, 0f, 100f, 0f, 7L, 1f, 0f, false);
        float end = path.totalSeconds();
        assertEquals(100f, path.landX(), 1e-3f);
        assertEquals(0f, path.landY(), 1e-3f);
        assertTrue(path.y(end * 0.1f) > 0.5f, "leaves toward the tube's side: " + path.y(end * 0.1f));
        // The terminal run lies on the line itself.
        assertEquals(0f, path.y(end * 0.97f), 1e-3f);
        assertTrue(path.terminalShare() > 0f);
    }
}
