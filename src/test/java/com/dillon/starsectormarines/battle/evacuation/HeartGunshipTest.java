package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeartGunshipTest {

    @Test
    void aShootIsSizedToKillItsTargetUpToItsCap() {
        // A fresh outpost: 320 structure behind 140 armour takes six 80-damage shells to strip, and one over.
        assertEquals(7, HeartGunship.shellsFor(320f, 140f));
        assertEquals(2, HeartGunship.shellsFor(40f, 0f));
        assertEquals(HeartGunship.MOST_SHELLS, HeartGunship.shellsFor(2000f, 500f));
    }
}
