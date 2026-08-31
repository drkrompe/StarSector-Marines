package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;

/** Carrier-owned muzzle geometry shared by mech firing and layered rendering. */
public final class MechHardpointGeometry {

    private MechHardpointGeometry() {}

    /** Resolves one installed component's launch point in world cells. */
    public static Point muzzle(float bodyX, float bodyY,
                               float waistOffsetX, float waistOffsetY,
                               float torsoFacingDegrees,
                               MechLoadoutComponent loadout,
                               MechWeaponMount mount, int releaseIndex) {
        LocalPoint local = localMuzzle(loadout, mount, releaseIndex);
        float hullWidth = LayeredMechAppearance.hullWidthCells(
                loadout.variant.renderScale);
        float radians = (float) Math.toRadians(torsoFacingDegrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float localX = local.xHullWidths * hullWidth;
        float localY = local.yHullWidths * hullWidth;
        return new Point(
                bodyX + waistOffsetX + localX * cos - localY * sin,
                bodyY + waistOffsetY + localX * sin + localY * cos);
    }

    public static LocalPoint localMuzzle(MechLoadoutComponent loadout,
                                         MechWeaponMount mount, int releaseIndex) {
        if (mount.slot == MechMountSlot.ARMS) {
            return armsMuzzle(mount.component.appearanceSelector, releaseIndex);
        }
        int left = appearance(loadout.mount(MechMountSlot.LEFT_SHOULDER));
        int right = appearance(loadout.mount(MechMountSlot.RIGHT_SHOULDER));
        return podMuzzle(loadout.variant.chassisAppearance, left, right,
                mount.slot == MechMountSlot.LEFT_SHOULDER,
                mount.component.appearanceSelector);
    }

    /** Local muzzle for an arms appearance, in hull-width units. */
    public static LocalPoint armsMuzzle(int armsAppearance, int releaseIndex) {
        return switch (armsAppearance) {
            case LayeredMechAppearance.ARMS_CHAINGUN -> new LocalPoint(
                    alternatingX(0.37f, releaseIndex), 0.39f);
            case LayeredMechAppearance.ARMS_LINEAR_CANNON -> new LocalPoint(
                    alternatingX(0.37f, releaseIndex), 0.51f);
            case LayeredMechAppearance.ARMS_NOSE_CHAINGUN -> new LocalPoint(0f, 0.52f);
            case LayeredMechAppearance.ARMS_HEAVY_CANNON -> new LocalPoint(0f, 0.57f);
            case LayeredMechAppearance.ARMS_PULSE_LASER -> new LocalPoint(
                    alternatingX(0.37f, releaseIndex), 0.39f);
            default -> new LocalPoint(0f, 0f);
        };
    }

    /** Local muzzle for one visible shoulder pod, in hull-width units. */
    public static LocalPoint podMuzzle(int chassisAppearance,
                                      int leftShoulderAppearance,
                                      int rightShoulderAppearance,
                                      boolean leftSlot, int podAppearance) {
        float x;
        if (chassisAppearance == LayeredMechAppearance.CHASSIS_HOUND) {
            boolean leftInstalled = leftShoulderAppearance != LayeredMechAppearance.POD_NONE;
            boolean rightInstalled = rightShoulderAppearance != LayeredMechAppearance.POD_NONE;
            x = leftInstalled != rightInstalled ? 0f : leftSlot ? -0.24f : 0.24f;
        } else {
            x = leftSlot ? -0.40f : 0.40f;
        }
        float y = podForward(podAppearance);
        return new LocalPoint(x, y);
    }

    public static float podForward(int podAppearance) {
        if (podAppearance == LayeredMechAppearance.POD_SHOULDER_LASER) return 0.32f;
        return isSmallPod(podAppearance) ? 0.12f : 0.16f;
    }

    /** Zero-based release about to leave this mount. */
    public static int nextReleaseIndex(MechWeaponMount mount) {
        return mount.burstRemaining > 0
                ? Math.max(0, mount.component.projectilesPerTrigger - mount.burstRemaining)
                : 0;
    }

    private static int appearance(MechWeaponMount mount) {
        return mount != null ? mount.component.appearanceSelector
                : LayeredMechAppearance.POD_NONE;
    }

    private static float alternatingX(float magnitude, int releaseIndex) {
        return (releaseIndex & 1) == 0 ? -magnitude : magnitude;
    }

    private static boolean isSmallPod(int appearance) {
        return appearance == LayeredMechAppearance.POD_SMALL_SRM
                || appearance == LayeredMechAppearance.POD_SMALL_LRM;
    }

    public record LocalPoint(float xHullWidths, float yHullWidths) { }

    public record Point(float x, float y) { }
}
