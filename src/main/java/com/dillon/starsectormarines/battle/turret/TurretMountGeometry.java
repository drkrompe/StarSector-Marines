package com.dillon.starsectormarines.battle.turret;

/** Pure mount-local geometry shared by turret simulation and presentation. */
public final class TurretMountGeometry {

    private TurretMountGeometry() {}

    /**
     * Resolves the active muzzle in world cells. Facing follows the sprite
     * convention: zero points north and positive angles turn counter-clockwise.
     */
    public static Point muzzle(float mountX, float mountY, float facingDegrees,
                               TurretMountDef mount, int releaseIndex) {
        float lateral = mount.muzzleLateralOffsetCells;
        if (lateral > 0f && (releaseIndex & 1) == 0) lateral = -lateral;
        float radians = (float) Math.toRadians(facingDegrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float x = mountX + lateral * cos - mount.muzzleOffsetCells * sin;
        float y = mountY + lateral * sin + mount.muzzleOffsetCells * cos;
        return new Point(x, y);
    }

    /** Zero-based release number for the next round in a latched burst. */
    public static int releaseIndex(int burstCount, int burstRemaining) {
        return Math.max(0, burstCount - Math.max(0, burstRemaining));
    }

    public record Point(float x, float y) { }
}
