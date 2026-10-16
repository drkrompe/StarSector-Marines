package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VehicleSpriteFramesTest {
    @Test void registeredVehiclesRetainTransparentMarginsInsteadOfMovingThePivot() {
        SpriteSheetFrames detected = new SpriteSheetFrames(1536, 576,
                new SpriteSheetFrames.Frame[]{new SpriteSheetFrames.Frame(50, 90, 670, 390),
                        new SpriteSheetFrames.Frame(830, 170, 440, 230)});
        for (VehicleType type : VehicleType.values()) {
            int height = type == VehicleType.BREAKTHROUGH_TANK ? 768
                    : type == VehicleType.CARGO_CARRIER ? 384 : 576;
            int width = type == VehicleType.CARGO_CARRIER ? 1024 : 768;
            SpriteSheetFrames input = new SpriteSheetFrames(width * type.frameCount, height, detected.frames);
            SpriteSheetFrames actual = type.registeredSpriteFrames(input);
            assertEquals(type.frameCount, actual.frames.length);
            assertEquals(0, actual.frames[0].x);
            assertEquals(width, actual.frames[1].x);
            assertTrue(type.wreckHullFrame() < actual.frames.length);
            if (type.turretFrame >= 0) {
                assertEquals(2 * width, actual.frames[2].x);
                assertEquals(3 * width, actual.frames[type.wreckTurretFrame()].x);
            } else assertEquals(-1, type.wreckTurretFrame());
            for (SpriteSheetFrames.Frame frame : actual.frames) {
                assertEquals(0, frame.y);
                assertEquals(width, frame.w);
                assertEquals(height, frame.h);
            }
        }
    }
}
