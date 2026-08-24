package com.dillon.starsectormarines.battle.turret.preview;

import com.dillon.starsectormarines.battle.turret.TurretKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurretCatalogPreviewOrientationTest {

    private static final float EPS = 1e-5f;

    @Test
    void eastboundStoryboardUsesTheCounterClockwiseSpriteConvention() {
        assertEquals(-90f, TurretCatalogPreviewDocument.FACING_DEGREES, EPS);
        assertEquals(1f, TurretCatalogPreviewDocument.directionX(), EPS);
        assertEquals(0f, TurretCatalogPreviewDocument.directionY(), EPS);
    }

    @Test
    void locustStoryboardAdvertisesItsSalvoAndBoostedArtilleryFlight() {
        assertEquals("SALVO LAUNCH ×8", TurretCatalogPreviewDocument
                .stateLabelsFor(TurretKind.LOCUST.mount()).get(1));
        assertEquals("BOOSTED ARC + TRAIL", TurretCatalogPreviewDocument
                .stateLabelsFor(TurretKind.LOCUST.mount()).get(2));
        assertEquals("SCATTER IMPACT", TurretCatalogPreviewDocument
                .stateLabelsFor(TurretKind.LOCUST.mount()).get(3));
        assertEquals(3, TurretCatalogPreviewDocument
                .visibleRoundCount(TurretKind.LOCUST.mount()));
        assertEquals(0.08f / 1.5f * 2f, TurretCatalogPreviewDocument
                .previewBurstProgressSpacing(TurretKind.LOCUST.mount()), EPS);
        assertTrue(TurretCatalogPreviewDocument.previewBearingDegrees(
                TurretKind.LOCUST.mount(), 0.25f) > -90f,
                "ascending missile should pitch above east");
        assertTrue(TurretCatalogPreviewDocument.previewBearingDegrees(
                TurretKind.LOCUST.mount(), 0.75f) < -90f,
                "descending missile should pitch below east");
    }
}
