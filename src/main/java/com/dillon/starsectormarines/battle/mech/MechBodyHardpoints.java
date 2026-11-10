package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;

import java.util.EnumMap;
import java.util.Map;

/**
 * Hardpoints that stand on a machine's body and turn on their own, each within its arc: the derelict security walkers'
 * flank gun ports and back racks (`mechs-nouns.md`, mount). A company mech carries its guns on a torso that twists on
 * its hips and points them all down the torso's bearing ({@link MechHardpointGeometry}); a walker's torso is its body,
 * walking to its own heading, so each gun sits at a fixed place on it and turns on its own mount toward the bearing
 * the walker's fire control holds ({@code MechLoadoutComponent.torsoFacingDegrees}), as far as its arc lets it.
 *
 * <p>Every place is in the walker's body frame: hull widths ({@link LayeredMechAppearance#hullWidthCells}) right and
 * forward of the body's centre, turned with the body's heading (the hips, never the legs). A mount fires only at a
 * bearing its arc covers, and its round leaves the tip of its drawn barrel ({@link #muzzle}), the point the battle
 * draws the gun's flash at.
 */
public final class MechBodyHardpoints {

    /** How much of a gun's drawn length sits behind its mount's pivot, on its ring. */
    public static final float SEAT = 0.30f;

    private static final Map<MechVariant, Hardpoint[]> TABLE = build();

    private MechBodyHardpoints() {
    }

    /**
     * One gun mount on the body.
     *
     * @param slot      the mount it is
     * @param type      what it takes: a port a ballistic gun, a rack a missile pack
     * @param rows      how deep it is, as a weapon point is ({@code MechFittingLayout}): a weapon whose footprint fits
     * @param right     its pivot right of the body's centre, hull widths (left is negative)
     * @param forward   its pivot ahead of the body's centre, hull widths
     * @param arcCentre the middle of its arc against the body's heading, sprite degrees (positive turns left)
     * @param arcHalf   how far either side of that middle it turns, degrees
     * @param gunScale  its gun drawn as on a mech this share of the walker's hull width
     */
    public record Hardpoint(MechMountSlot slot, MechFittingLayout.SocketType type, int rows, float right, float forward,
                            float arcCentre, float arcHalf, float gunScale) {

        /** Whether this mount turns far enough to bear on {@code bearing}, with the body heading {@code bodyFacing}. */
        public boolean covers(float bodyFacing, float bearing) {
            return Math.abs(offCentre(bodyFacing, bearing)) <= arcHalf + 1e-3f;
        }

        /** The bearing this mount's gun points at, turning toward {@code aim} and stopping at its arc's edge. */
        public float gunFacing(float bodyFacing, float aim) {
            float off = Math.max(-arcHalf, Math.min(arcHalf, offCentre(bodyFacing, aim)));
            return LayeredAppearance.wrapDegrees(bodyFacing + arcCentre + off);
        }

        /** Whether {@code weapon} goes on this mount: its kind and its whole footprint. */
        public boolean takes(MechWeaponComponent weapon) {
            if (weapon == null || !weapon.accepts(slot)) return false;
            boolean kind = switch (type) {
                case OMNI -> true;
                case BALLISTIC -> weapon.hardpointType == MechWeaponComponent.HardpointType.BALLISTIC;
                case ENERGY -> weapon.hardpointType == MechWeaponComponent.HardpointType.ENERGY;
                case MISSILE -> weapon.hardpointType == MechWeaponComponent.HardpointType.MISSILE;
                default -> false;
            };
            return kind && MechFittingLayout.hasRoom(MechFittingLayout.WEAPON_POINT_COLUMNS, rows,
                    weapon.footprintColumns, weapon.footprintRows);
        }

        private float offCentre(float bodyFacing, float bearing) {
            return LayeredAppearance.wrapDegrees(bearing - bodyFacing - arcCentre);
        }
    }

    /** Whether {@code variant} carries its guns on body hardpoints rather than on a twisting torso. */
    public static boolean has(MechVariant variant) {
        return variant != null && TABLE.containsKey(variant);
    }

    /** {@code variant}'s hardpoint at {@code slot}, or null when it has none there or no body hardpoints at all. */
    public static Hardpoint of(MechVariant variant, MechMountSlot slot) {
        Hardpoint[] points = variant == null ? null : TABLE.get(variant);
        return points == null || slot == null ? null : points[slot.ordinal()];
    }

    /**
     * The mount's pivot in world cells: the body's centre and heading carry it.
     */
    public static float[] pivot(float bodyX, float bodyY, float bodyFacing, float hullWidthCells, Hardpoint point) {
        float[] r = rotate(point.right() * hullWidthCells, point.forward() * hullWidthCells, bodyFacing);
        return new float[] {bodyX + r[0], bodyY + r[1]};
    }

    /**
     * How long the gun on {@code point} is drawn, cells: its drawing's length on a mech
     * ({@link MechHardpointGeometry#barrelHullWidths(MechMountSlot, int)}) at the mount's gun scale.
     */
    public static float gunLengthCells(Hardpoint point, int appearance, float hullWidthCells) {
        return MechHardpointGeometry.barrelHullWidths(point.slot(), appearance) * point.gunScale() * hullWidthCells;
    }

    /**
     * Where a round leaves the gun on {@code point}: the tip of its drawn barrel, out from the pivot along the gun's
     * own bearing ({@link Hardpoint#gunFacing}).
     */
    public static MechHardpointGeometry.Point muzzle(float bodyX, float bodyY, float bodyFacing, float aim,
                                                     float hullWidthCells, Hardpoint point, int appearance) {
        float[] pivot = pivot(bodyX, bodyY, bodyFacing, hullWidthCells, point);
        float reach = (1f - SEAT) * gunLengthCells(point, appearance, hullWidthCells);
        float[] r = rotate(0f, reach, point.gunFacing(bodyFacing, aim));
        return new MechHardpointGeometry.Point(pivot[0] + r[0], pivot[1] + r[1]);
    }

    /** Local (right, forward) turned to {@code degrees} in sprite degrees: 0 is +y, counter-clockwise positive. */
    static float[] rotate(float x, float y, float degrees) {
        double radians = Math.toRadians(degrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new float[] {x * cos - y * sin, x * sin + y * cos};
    }

    /**
     * The walkers' mounts (`derelict-watch.md`, Medium and large walkers), all on the one sentinel torso drawn at each
     * class's size: a gun port in each flank of its upper band, turning out to its own side and across the front, and
     * a missile rack each side of its back, turning with the fire control over the front and flanks. The torso's drawing
     * is 0.74 hull widths across. The rounds walker's ports swing widest (light guns on a quick frame); the hall frame's
     * casemates are the stiffest and its racks the deepest. Arcs UNTUNED.
     */
    private static Map<MechVariant, Hardpoint[]> build() {
        Map<MechVariant, Hardpoint[]> table = new EnumMap<>(MechVariant.class);
        table.put(MechVariant.SECURITY_ROUNDS, walker(2, 2, 20f, 115f, 110f, 0.72f, 0.52f));
        table.put(MechVariant.SECURITY_WALKER, walker(3, 3, 25f, 105f, 120f, 0.62f, 0.46f));
        table.put(MechVariant.SECURITY_HALL, walker(3, 3, 20f, 95f, 120f, 0.58f, 0.42f));
        return table;
    }

    /**
     * One walker's four mounts: ports at ({@link #PORT_RIGHT}, {@link #PORT_FORWARD}) turned out by {@code portOut},
     * racks at ({@link #RACK_RIGHT}, {@link #RACK_FORWARD}) facing ahead; the racks' packs drawn smaller than the ports'
     * guns, low on the back.
     */
    private static Hardpoint[] walker(int portRows, int rackRows, float portOut, float portHalf, float rackHalf,
                                      float portScale, float rackScale) {
        Hardpoint[] points = new Hardpoint[MechMountSlot.values().length];
        points[MechMountSlot.LEFT_ARM.ordinal()] = new Hardpoint(MechMountSlot.LEFT_ARM,
                MechFittingLayout.SocketType.BALLISTIC, portRows, -PORT_RIGHT, PORT_FORWARD, portOut, portHalf, portScale);
        points[MechMountSlot.RIGHT_ARM.ordinal()] = new Hardpoint(MechMountSlot.RIGHT_ARM,
                MechFittingLayout.SocketType.BALLISTIC, portRows, PORT_RIGHT, PORT_FORWARD, -portOut, portHalf, portScale);
        points[MechMountSlot.LEFT_SHOULDER.ordinal()] = new Hardpoint(MechMountSlot.LEFT_SHOULDER,
                MechFittingLayout.SocketType.MISSILE, rackRows, -RACK_RIGHT, RACK_FORWARD, 0f, rackHalf, rackScale);
        points[MechMountSlot.RIGHT_SHOULDER.ordinal()] = new Hardpoint(MechMountSlot.RIGHT_SHOULDER,
                MechFittingLayout.SocketType.MISSILE, rackRows, RACK_RIGHT, RACK_FORWARD, 0f, rackHalf, rackScale);
        return points;
    }

    /** The flank ports' pivots, hull widths: on the sides of the torso's upper band. */
    static final float PORT_RIGHT = 0.22f;
    static final float PORT_FORWARD = 0.06f;
    /** The back racks' pivots, hull widths: either side of the spine on the torso's rear band. */
    static final float RACK_RIGHT = 0.13f;
    static final float RACK_FORWARD = -0.19f;
}
