package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.weapon.WeaponDef.Guidance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuidedMissileTest {
    @Test void guidanceTurnsAfterTheTargetMovesAndContactUsesTheTravelledSegment() {
        var missile = new GuidedMissile(0, 0, -90f, 25f, new Guidance(140, 20, 8));
        for (int i = 0; i < 30; i++) missile.advance(1f / 30, true, 50, 0, 3);
        float initial = missile.heading;
        missile.advance(1f / 30, true, 40, 20, 3);
        assertTrue(missile.heading > initial);
        assertTrue(missile.heading - initial <= Math.toRadians(140f / 30) + .0001);
        assertTrue(missile.contacts((missile.previousX + missile.x) * .5f,
                (missile.previousY + missile.y) * .5f, (missile.previousZ + missile.z) * .5f, .01f));
        assertFalse(missile.contacts(100, 100, 3, 1));
    }

    @Test void targetLossDoesNotTeleportOrKeepAMissileAliveForever() {
        var missile = new GuidedMissile(0, 0, -90f, 25f, new Guidance(140, 20, 2));
        float heading = missile.heading;
        for (int i = 0; i < 90; i++) missile.advance(1f / 30, false, -100, -100, -100);
        assertEquals(heading, missile.heading);
        assertEquals(0f, missile.remaining);
        assertTrue(missile.x > 0);
    }
}
