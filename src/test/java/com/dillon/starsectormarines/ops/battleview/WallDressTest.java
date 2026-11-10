package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WallDressTest {

    @Test
    void aWallIsHurtAgainstWhatItHeldNotAFixedScale() {
        assertEquals(0f, WallDress.hurt(80, 80), 1e-6f, "a light wall untouched is whole");
        assertEquals(0f, WallDress.hurt(300, 300), 1e-6f);
        assertEquals(.5f, WallDress.hurt(150, 300), 1e-6f, "a heavy wall half gone is half hurt");
        assertEquals(0f, WallDress.hurt(0, 0), 1e-6f, "a wall never seeded is not hurt");
    }

    @Test
    void aBrokenWallKeepsItsLowerStoreysAndARaggedTop() {
        for (int cell = 0; cell < 200; cell++) {
            for (float t = .1f; t < 1f; t += .3f) {
                float top = WallDress.brokenTop(cell, 3, t);
                assertTrue(top > 1f / 3f * .7f && top < 1f, "the upper storey falls, the ground storey stands: " + top);
            }
        }
    }

    @Test
    void aWorksIsBlindButForTheGlazedBandUnderItsEave() {
        WallDress.Rhythm r = WallDress.rhythm(BuildingKind.INDUSTRIAL, BuildingFaces.Look.OPEN, .3f);
        assertEquals(WallDress.Bay.BLIND, WallDress.upper(r, BuildingKind.INDUSTRIAL, BuildingFaces.Look.OPEN,
                BuildingFaces.Segment.WINDOW, false, 4, 1, 3));
        assertEquals(WallDress.Bay.CLERESTORY,
                WallDress.upper(r, BuildingKind.INDUSTRIAL, BuildingFaces.Look.OPEN,
                        BuildingFaces.Segment.WINDOW, false, 4, 2, 3));
    }
}
