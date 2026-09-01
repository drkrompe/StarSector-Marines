package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MechEquipmentGridCanvasTest {

    @Test
    void portraitEquipmentArtTurnsSidewaysForTheListPreview() {
        assertEquals(90f, MechEquipmentGridCanvas.previewAngleDegrees(
                LayeredSpriteCache.headless("portrait.png", 40, 96)));
        assertEquals(0f, MechEquipmentGridCanvas.previewAngleDegrees(
                LayeredSpriteCache.headless("landscape.png", 96, 40)));
        assertEquals(0f, MechEquipmentGridCanvas.previewAngleDegrees(
                LayeredSpriteCache.headless("square.png", 64, 64)));
    }
}
