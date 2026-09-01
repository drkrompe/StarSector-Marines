package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationTierSliderWidgetTest {

    @Test
    void plusMovesOneCampaignScaleStep() {
        AtomicReference<OperationTier> changed = new AtomicReference<>();
        OperationTierSliderWidget slider = new OperationTierSliderWidget(
                10f, 20f, 400f, OperationTierSliderWidget.DEFAULT_HEIGHT,
                MissionType.ASSAULT, OperationTier.ESTABLISHED, changed::set);

        assertTrue(slider.onMouseDown(400, 25));
        assertTrue(slider.onMouseUp(400, 25));

        assertEquals(OperationTier.VETERAN, slider.selectedTier());
        assertEquals(OperationTier.VETERAN, changed.get());
    }

    @Test
    void conquestSliderStartsAtItsRealFloor() {
        AtomicReference<OperationTier> changed = new AtomicReference<>();
        OperationTierSliderWidget slider = new OperationTierSliderWidget(
                10f, 20f, 400f, OperationTierSliderWidget.DEFAULT_HEIGHT,
                MissionType.CONQUEST, OperationTier.FIRST_CONTRACT, changed::set);

        assertEquals(OperationTier.REINFORCED, slider.selectedTier());
        assertTrue(slider.onMouseDown(12, 25));
        assertTrue(slider.onMouseUp(12, 25));
        assertEquals(OperationTier.REINFORCED, slider.selectedTier());
        assertNull(changed.get());
    }
}
