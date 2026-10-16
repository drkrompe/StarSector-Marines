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
            SpriteSheetFrames input = type == VehicleType.BREAKTHROUGH_TANK
                    ? new SpriteSheetFrames(3072, 576, detected.frames) : detected;
            SpriteSheetFrames actual = type.registeredSpriteFrames(input);
            assertEquals(type.frameCount, actual.frames.length);
            assertEquals(0, actual.frames[0].x);
            assertEquals(768, actual.frames[1].x);
            for (SpriteSheetFrames.Frame frame : actual.frames) {
                assertEquals(0, frame.y);
                assertEquals(768, frame.w);
                assertEquals(576, frame.h);
            }
        }
        assertSame(detected, VehicleType.MILITIA_TECHNICAL.registeredSpriteFrames(detected));
        assertSame(detected, VehicleType.CARGO_CARRIER.registeredSpriteFrames(detected));
    }
}
