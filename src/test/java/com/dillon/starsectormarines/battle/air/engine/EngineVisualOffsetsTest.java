package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.AirScale;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EngineVisualOffsetsTest {
    private static EngineSlotData[] nativeSlots() {
        return new EngineSlotData[]{
                new EngineSlotData(-.3f, -.8f, 180, 2, .4f, .2f, "HIGH_TECH"),
                new EngineSlotData(.3f, -.8f, 175, 3, .5f, .3f, "MIDLINE"),
                new EngineSlotData(0, .2f, 90, 1, .1f, 0, "LOW_TECH")};
    }

    @Test void ordinaryCraftKeepTheNativeArrayAndOverridesCannotMutateDemandInputs() {
        var nativeSlots = nativeSlots();
        assertSame(nativeSlots, ShuttleType.AEROSHUTTLE.engineVisualOffsets().applyTo(nativeSlots));
        assertSame(nativeSlots, FighterProfile.TALON.engineVisualOffsets().applyTo(nativeSlots));
        var offsets = new EngineVisualOffsets(Map.of(0, new EngineVisualOffsets.Offset(2, -1.25f)));
        var visual = offsets.applyTo(nativeSlots);
        assertSame(visual, offsets.applyTo(nativeSlots), "stable native data reuses its corrected render array");
        assertNotSame(nativeSlots, visual);
        float density = AirScale.METERS_PER_PX / AirScale.METERS_PER_CELL;
        assertEquals(-.3f + 2 * density, visual[0].localX);
        assertEquals(-.8f - 1.25f * density, visual[0].localY);
        assertEquals(-.3f, nativeSlots[0].localX);
        assertEquals(-.8f, nativeSlots[0].localY);
        assertSame(nativeSlots[1], visual[1]);
        assertSame(nativeSlots[2], visual[2]);
        assertEquals(nativeSlots[0].angleDegrees, visual[0].angleDegrees);
        assertEquals(nativeSlots[0].lengthCells, visual[0].lengthCells);
        assertEquals(nativeSlots[0].widthCells, visual[0].widthCells);
        assertEquals(nativeSlots[0].contrailSizeCells, visual[0].contrailSizeCells);
        assertEquals(nativeSlots[0].style, visual[0].style);
        var refreshed = nativeSlots();
        assertNotSame(visual, offsets.applyTo(refreshed), "a refreshed native spec replaces the render cache");
    }

    @Test void replacementNozzlesMoveInwardForWaspAndOutwardForLongbowWithoutReorderingSlots() {
        var source = nativeSlots();
        var wasp = FighterProfile.WASP.engineVisualOffsets().applyTo(source);
        var longbow = FighterProfile.LONGBOW.engineVisualOffsets().applyTo(source);
        assertTrue(wasp[0].localX > source[0].localX);
        assertTrue(wasp[1].localX < source[1].localX);
        assertTrue(wasp[0].localY < source[0].localY);
        assertTrue(longbow[0].localX < source[0].localX);
        assertTrue(longbow[1].localX > source[1].localX);
        assertTrue(longbow[0].localY > source[0].localY);
        for (int i = 0; i < source.length; i++) {
            assertEquals(source[i].angleDegrees, wasp[i].angleDegrees);
            assertEquals(source[i].angleDegrees, longbow[i].angleDegrees);
        }
    }
}
