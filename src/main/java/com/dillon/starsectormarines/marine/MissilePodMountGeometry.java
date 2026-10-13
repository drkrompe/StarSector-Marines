package com.dillon.starsectormarines.marine;

/** Shared physical mount calculation. Bearings match SpriteAPI: zero along world +Y, positive counter-clockwise. */
public final class MissilePodMountGeometry {
    private MissilePodMountGeometry() {}

    public record Pose(float mountX, float mountY, float muzzleX, float muzzleY,
                       float bearingDegrees) {}

    public static Pose resolve(float actorX, float actorY, float bodyBearingDegrees,
                               float podBearingDegrees, MissilePodMountSpec mount) {
        if (mount == null) return new Pose(actorX, actorY, actorX, actorY, podBearingDegrees);
        double body = Math.toRadians(bodyBearingDegrees);
        float x = actorX + (float) (Math.cos(body) * mount.rightCells()
                - Math.sin(body) * mount.forwardCells());
        float y = actorY + (float) (Math.sin(body) * mount.rightCells()
                + Math.cos(body) * mount.forwardCells());
        double aim = Math.toRadians(podBearingDegrees);
        return new Pose(x, y, x - (float) Math.sin(aim) * mount.muzzleForwardCells(),
                y + (float) Math.cos(aim) * mount.muzzleForwardCells(), podBearingDegrees);
    }

    public static float bearing(float dx, float dy) {
        float degrees = (float) Math.toDegrees(Math.atan2(dy, dx)) - 90f;
        return degrees < -180f ? degrees + 360f : degrees;
    }
}
