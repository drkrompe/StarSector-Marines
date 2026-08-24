package com.dillon.starsectormarines.battle.turret.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TurretCatalogPreviewOrientationTest {

    private static final float EPS = 1e-5f;

    @Test
    void eastboundStoryboardUsesTheCounterClockwiseSpriteConvention() {
        assertEquals(-90f, TurretCatalogPreviewDocument.FACING_DEGREES, EPS);
        assertEquals(1f, TurretCatalogPreviewDocument.directionX(), EPS);
        assertEquals(0f, TurretCatalogPreviewDocument.directionY(), EPS);
    }
}
