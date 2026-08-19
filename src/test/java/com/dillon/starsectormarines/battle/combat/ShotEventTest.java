package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShotEventTest {

    @Test
    void legacyEventsRemainGroundProjectedPhysicalImpacts() {
        ShotEvent shot = new ShotEvent(1f, 2f, 3f, 4f,
                false, Faction.MARINE, 0.2f);

        assertEquals(0f, shot.fromZ);
        assertEquals(0f, shot.toZ);
        assertEquals(2f, shot.visualFromY());
        assertEquals(4f, shot.visualToY());
        assertTrue(shot.impacts());
    }

    @Test
    void elevatedOvershootProjectsIntoScreenYWithoutClaimingAnImpact() {
        ShotEvent shot = new ShotEvent(1f, 2f, 0f, 3f, 4f, 1.25f,
                false, Faction.MARINE, 0.2f,
                null, null, null, null, 1f, false,
                BallisticResolver.StopKind.OVERSHOOT);

        assertEquals(5.25f, shot.visualToY());
        assertFalse(shot.impacts());
    }
}
