package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;

/** Carrier-owned muzzle geometry shared by mech firing and layered rendering. */
public final class MechHardpointGeometry {

    private MechHardpointGeometry() {}

    /**
     * Resolves one installed component's launch point in world cells, reading a body hardpoint's gun as standing on a
     * body that faces the torso's bearing; {@link #muzzle(float, float, float, float, float, float,
     * MechLoadoutComponent, MechWeaponMount, int)} where the hips' heading is known.
     */
    public static Point muzzle(float bodyX, float bodyY,
                               float waistOffsetX, float waistOffsetY,
                               float torsoFacingDegrees,
                               MechLoadoutComponent loadout,
                               MechWeaponMount mount, int releaseIndex) {
        return muzzle(bodyX, bodyY, waistOffsetX, waistOffsetY, torsoFacingDegrees, torsoFacingDegrees, loadout, mount,
                releaseIndex);
    }

    /**
     * Resolves one installed component's launch point in world cells. A mount on a twisting torso fires down the
     * torso's bearing from its place on the torso; a body hardpoint ({@link MechBodyHardpoints}) from the tip of its own
     * gun, standing where the body's heading {@code hipFacingDegrees} carries it and turned toward the torso's bearing
     * within its arc.
     */
    public static Point muzzle(float bodyX, float bodyY,
                               float waistOffsetX, float waistOffsetY,
                               float hipFacingDegrees, float torsoFacingDegrees,
                               MechLoadoutComponent loadout,
                               MechWeaponMount mount, int releaseIndex) {
        MechBodyHardpoints.Hardpoint point = MechBodyHardpoints.of(loadout.variant, mount.slot);
        if (point != null) {
            return MechBodyHardpoints.muzzle(bodyX + waistOffsetX, bodyY + waistOffsetY, hipFacingDegrees,
                    torsoFacingDegrees, LayeredMechAppearance.hullWidthCells(loadout.variant.renderScale), point,
                    mount.component.appearanceSelector(mount.slot));
        }
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
        if (mount.slot.isArm()) {
            return armsMuzzle(mount.component.appearanceSelector(mount.slot), mount.slot);
        }
        int left = appearance(loadout.mount(MechMountSlot.LEFT_SHOULDER));
        int right = appearance(loadout.mount(MechMountSlot.RIGHT_SHOULDER));
        return podMuzzle(loadout.variant.chassisAppearance, left, right,
                mount.slot == MechMountSlot.LEFT_SHOULDER,
                mount.component.appearanceSelector);
    }

    /**
     * Local muzzle of one arm gun on {@code slot}, in hull-width units. Each gun is one barrel: on
     * a side arm it sits out at {@link MechMountSlot#armLateral}, pivoted further back than a
     * centreline gun, whose barrel starts under the nose.
     */
    public static LocalPoint armsMuzzle(int armsAppearance, MechMountSlot slot) {
        boolean side = slot != null && slot.isSideArm();
        float forward = side ? sideArmForward(armsAppearance) : centrelineForward(armsAppearance);
        if (Float.isNaN(forward)) return new LocalPoint(0f, 0f);
        return new LocalPoint(side ? slot.armLateral() : 0f, forward);
    }

    /** Muzzle forward of a gun out on an arm; its barrel's rear pivot sits at -0.15. */
    private static float sideArmForward(int armsAppearance) {
        return switch (armsAppearance) {
            case LayeredMechAppearance.ARMS_CHAINGUN, LayeredMechAppearance.ARMS_PL_2_AP,
                 LayeredMechAppearance.ARMS_NOSE_CHAINGUN -> 0.39f;
            // The HC-10 is 116 px long: 0.56 hull widths from the rear pivot.
            case LayeredMechAppearance.ARMS_HC_10 -> 0.41f;
            case LayeredMechAppearance.ARMS_AC_6 -> 0.49f;
            case LayeredMechAppearance.ARMS_AC_6_QS,
                 LayeredMechAppearance.ARMS_NOSE_AC_6_QS -> 0.46f;
            case LayeredMechAppearance.ARMS_HC_20 -> 0.47f;
            case LayeredMechAppearance.ARMS_DC_5,
                 LayeredMechAppearance.ARMS_DC_4_QS -> 0.42f;
            // The light weapons are 84 to 88 px long: about 0.41 hull widths from the rear pivot.
            case LayeredMechAppearance.ARMS_LIGHT_MG,
                 LayeredMechAppearance.ARMS_BL_1,
                 LayeredMechAppearance.ARMS_PL_1 -> 0.27f;
            // The laser ladder: 136 px long (0.65 hull widths) for the heavy two, 112 px (0.54) for the medium two.
            case LayeredMechAppearance.ARMS_PL_3,
                 LayeredMechAppearance.ARMS_PL_3_SWEEP -> 0.50f;
            case LayeredMechAppearance.ARMS_BL_2,
                 LayeredMechAppearance.ARMS_PL_2 -> 0.41f;
            // The FL-1 burner is 118 px long: 0.57 hull widths from the rear pivot.
            case LayeredMechAppearance.ARMS_FL_1 -> 0.42f;
            default -> Float.NaN;
        };
    }

    /**
     * Where every laser's muzzle stands on the centreline mount, in hull widths: level with the heavy laser, which is
     * the one that shows past the nose cowl, so each size of the ladder pokes out of it the same distance.
     */
    private static final float CENTRELINE_LASER_TIP = 0.60f;

    /**
     * How far forward a laser's drawing is set on the centreline mount, in hull widths, so its muzzle is at
     * {@link #CENTRELINE_LASER_TIP}: the shorter lasers are drawn short and would end inside the nose cowl. Zero for
     * the heavy lasers and every other gun, which end where they are drawn.
     */
    public static float centrelineDrawingShift(int armsAppearance) {
        return switch (armsAppearance) {
            case LayeredMechAppearance.ARMS_BL_1,
                 LayeredMechAppearance.ARMS_PL_1 -> CENTRELINE_LASER_TIP - 0.37f;
            case LayeredMechAppearance.ARMS_BL_2,
                 LayeredMechAppearance.ARMS_PL_2 -> CENTRELINE_LASER_TIP - 0.51f;
            default -> 0f;
        };
    }

    /** Muzzle forward of a gun on the centreline mount. */
    private static float centrelineForward(int armsAppearance) {
        return switch (armsAppearance) {
            case LayeredMechAppearance.ARMS_NOSE_CHAINGUN, LayeredMechAppearance.ARMS_CHAINGUN,
                 LayeredMechAppearance.ARMS_DC_5,
                 LayeredMechAppearance.ARMS_DC_4_QS -> 0.52f;
            case LayeredMechAppearance.ARMS_HC_20 -> 0.57f;
            case LayeredMechAppearance.ARMS_NOSE_AC_6_QS,
                 LayeredMechAppearance.ARMS_AC_6_QS -> 0.55f;
            case LayeredMechAppearance.ARMS_HC_10 -> 0.51f;
            case LayeredMechAppearance.ARMS_PL_2_AP -> 0.49f;
            case LayeredMechAppearance.ARMS_AC_6 -> 0.59f;
            case LayeredMechAppearance.ARMS_LIGHT_MG -> 0.37f;
            // Its nozzle just out of the nose channel, past the prongs' tips.
            case LayeredMechAppearance.ARMS_FL_1 -> 0.52f;
            case LayeredMechAppearance.ARMS_BL_1, LayeredMechAppearance.ARMS_PL_1,
                 LayeredMechAppearance.ARMS_PL_3, LayeredMechAppearance.ARMS_PL_3_SWEEP,
                 LayeredMechAppearance.ARMS_BL_2, LayeredMechAppearance.ARMS_PL_2 ->
                    CENTRELINE_LASER_TIP;
            default -> Float.NaN;
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
        if (podAppearance == LayeredMechAppearance.POD_LL_3
                || podAppearance == LayeredMechAppearance.POD_THERMAL_LANCE) return 0.32f;
        if (podAppearance == LayeredMechAppearance.POD_SRM_3_QS) return 0.22f;
        // The FL-2 lance is 156 px long: 0.75 hull widths from the rear pivot, its cowl well ahead of the shoulder.
        if (podAppearance == LayeredMechAppearance.POD_FL_2) return 0.45f;
        return isSmallPod(podAppearance) ? 0.12f : 0.16f;
    }

    /** Where an arm gun's barrel pivots on its mount, and a shoulder pod's, in hull widths (the rear of its drawing). */
    private static final float ARM_PIVOT = -0.15f;
    private static final float POD_PIVOT = -0.30f;

    /**
     * How long a weapon's drawing is from its rear pivot to its muzzle, in hull widths; zero for a weapon with no
     * drawing. A vehicle carrying the weapon draws and fires it at this length ({@code VehicleGun}).
     */
    public static float barrelHullWidths(MechWeaponComponent weapon) {
        return barrelHullWidths(weapon.mountFamily == MechWeaponComponent.MountFamily.ARMS,
                weapon.appearanceSelector);
    }

    /**
     * As {@link #barrelHullWidths(MechWeaponComponent)}, for the drawing {@code appearance} names on {@code slot}: an arm
     * gun's on an arm mount, a pod's on a shoulder (the two families number their selectors alike).
     */
    public static float barrelHullWidths(MechMountSlot slot, int appearance) {
        return barrelHullWidths(slot.isArm(), appearance);
    }

    private static float barrelHullWidths(boolean armGun, int appearance) {
        if (armGun) {
            float arm = sideArmForward(appearance);
            return Float.isNaN(arm) ? 0f : arm - ARM_PIVOT;
        }
        boolean pod = appearance >= LayeredMechAppearance.POD_SMALL_SRM
                && appearance <= LayeredMechAppearance.POD_FL_2;
        return pod ? podForward(appearance) - POD_PIVOT : 0f;
    }

    /**
     * How far a weapon's drawing kicks back as it fires, in hull widths, as the mech renderer draws it: the rapid
     * guns and the missile pods kick, the cannons and lasers stay planted.
     */
    public static float recoilHullWidths(MechWeaponComponent weapon) {
        int appearance = weapon.appearanceSelector;
        if (weapon.mountFamily == MechWeaponComponent.MountFamily.ARMS) {
            return switch (appearance) {
                case LayeredMechAppearance.ARMS_CHAINGUN, LayeredMechAppearance.ARMS_NOSE_CHAINGUN,
                     LayeredMechAppearance.ARMS_PL_2_AP, LayeredMechAppearance.ARMS_AC_6,
                     LayeredMechAppearance.ARMS_AC_6_QS, LayeredMechAppearance.ARMS_NOSE_AC_6_QS,
                     LayeredMechAppearance.ARMS_LIGHT_MG -> 0.025f;
                default -> 0f;
            };
        }
        return switch (appearance) {
            case LayeredMechAppearance.POD_SMALL_SRM, LayeredMechAppearance.POD_LARGE_SRM,
                 LayeredMechAppearance.POD_SRM_3_QS -> 0.018f;
            case LayeredMechAppearance.POD_SMALL_LRM, LayeredMechAppearance.POD_LARGE_LRM -> 0.024f;
            default -> 0f;
        };
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

    private static boolean isSmallPod(int appearance) {
        return appearance == LayeredMechAppearance.POD_SMALL_SRM
                || appearance == LayeredMechAppearance.POD_SMALL_LRM;
    }

    public record LocalPoint(float xHullWidths, float yHullWidths) { }

    public record Point(float x, float y) { }
}
