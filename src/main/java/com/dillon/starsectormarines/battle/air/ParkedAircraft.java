package com.dillon.starsectormarines.battle.air;

/**
 * Static civilian aircraft occupying a generated spaceport berth. Unlike a
 * live shuttle this has no mission or kinematics; setup stamps its 3x3 ground
 * footprint into navigation and the vehicle render pass draws the hull.
 */
public final class ParkedAircraft {

    public static final int FOOTPRINT_HALF = 1;

    public final ShuttleType type;
    public final int centerX;
    public final int centerY;
    public final float facingDegrees;

    public ParkedAircraft(ShuttleType type, int centerX, int centerY,
                          float facingDegrees) {
        this.type = type;
        this.centerX = centerX;
        this.centerY = centerY;
        this.facingDegrees = facingDegrees;
    }
}
