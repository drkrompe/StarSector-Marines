package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VehicleSpriteFramesTest {
    @Test void registeredVehiclesRetainTransparentMarginsInsteadOfMovingThePivot() {
        SpriteSheetFrames detected = new SpriteSheetFrames(1536, 576,
                new SpriteSheetFrames.Frame[]{new SpriteSheetFrames.Frame(50, 90, 670, 390),
                        new SpriteSheetFrames.Frame(830, 170, 440, 230)});
        for (VehicleType type : new VehicleType[]{VehicleType.HEAVY_APC, VehicleType.ESCORT_APC,
                VehicleType.BREAKTHROUGH_TANK}) {
            int height = type.isApc() ? 576 : 768;
            SpriteSheetFrames input = new SpriteSheetFrames(3072, height, detected.frames);
            SpriteSheetFrames actual = type.registeredSpriteFrames(input);
            assertEquals(type.frameCount, actual.frames.length);
            assertEquals(0, actual.frames[0].x);
            assertEquals(768, actual.frames[1].x);
            assertEquals(1536, actual.frames[2].x);
            assertEquals(2304, actual.frames[3].x);
            for (SpriteSheetFrames.Frame frame : actual.frames) {
                assertEquals(0, frame.y);
                assertEquals(768, frame.w);
                assertEquals(height, frame.h);
            }
        }
        assertSame(detected, VehicleType.MILITIA_TECHNICAL.registeredSpriteFrames(detected));
        assertSame(detected, VehicleType.CARGO_CARRIER.registeredSpriteFrames(detected));
    }
}
