package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.Gantry;

import java.util.List;

/**
 * Eased presentation camera for the wide Mech Lab and its focused fitting view.
 *
 * <p>The easing is the whole of this class; <b>where</b> the camera can sit is
 * not. A lab looks at a bay on whichever ship the company is living aboard, so
 * the wide shot is that bay and a fitting shot is a berth in it — neither is a
 * constant, and a controller that knew the coordinates would only work on one
 * ship. It is handed the anchors and interpolates between them.
 */
public final class MechLabCameraController {

    /** Where the lab's camera can sit on the ship it is looking at. */
    public interface Anchors {

        /** The whole room. */
        CameraPose wide();

        /** One berth, close enough to fit a machine standing in it. */
        CameraPose berth(int index);
    }

    static final float WIDE_ZOOM_NOTCHES = 0f;
    static final float FITTING_ZOOM_NOTCHES = 6.5f;
    static final float TRANSITION_SECONDS = 0.72f;
    /**
     * How far right of centre the fitting shot stands its machine, as a share of the view's width:
     * the schematic takes the left of the panel, and the machine is shown in the middle of the rest.
     */
    static final float FITTING_SIDE_SHIFT = (1f + MechLabDollCanvas.SCHEMATIC_SHARE) * 0.5f - 0.5f;

    private final Anchors anchors;
    private CameraPose current;
    private CameraPose transitionFrom;
    private CameraPose target;
    private float transitionSeconds = TRANSITION_SECONDS;

    public MechLabCameraController(Anchors anchors) {
        if (anchors == null) throw new IllegalArgumentException("camera anchors are required");
        this.anchors = anchors;
        current = anchors.wide();
        transitionFrom = current;
        target = current;
    }

    public void target(boolean fittingFocused, int gantryIndex, int assignedAssets) {
        CameraPose requested = fittingFocused
                ? anchors.berth(gantryIndex) : anchors.wide();
        if (same(requested, target)) return;
        transitionFrom = current;
        target = requested;
        transitionSeconds = 0f;
    }

    public void advance(float seconds) {
        transitionSeconds = Math.min(TRANSITION_SECONDS,
                transitionSeconds + Math.max(0f, seconds));
        float linear = transitionSeconds / TRANSITION_SECONDS;
        float eased = linear * linear * (3f - 2f * linear);
        current = interpolate(transitionFrom, target, eased);
    }

    public CameraPose pose() {
        return current;
    }

    public void snap(boolean fittingFocused, int gantryIndex, int assignedAssets) {
        current = fittingFocused ? anchors.berth(gantryIndex) : anchors.wide();
        transitionFrom = current;
        target = current;
        transitionSeconds = TRANSITION_SECONDS;
    }

    /**
     * Anchors on a compartment and the berths standing in it.
     *
     * <p>The wide shot looks at the middle of the room rather than at the
     * machines, because a bay with one mech in it is still a bay and the camera
     * should not swing to the corner the company happens to have filled.
     */
    public static Anchors on(ShipDeckBattleScene.RoomView room, List<Gantry> berths) {
        if (room == null) throw new IllegalArgumentException("a room framing is required");
        List<Gantry> standing = berths == null ? List.of() : List.copyOf(berths);
        return new Anchors() {
            @Override
            public CameraPose wide() {
                return new CameraPose(room.centerCellX(), room.centerCellY(),
                        WIDE_ZOOM_NOTCHES, 0f);
            }

            @Override
            public CameraPose berth(int index) {
                if (standing.isEmpty()) return wide();
                Gantry berth = standing.get(
                        Math.max(0, Math.min(standing.size() - 1, index)));
                return new CameraPose(berth.centerX + 0.5f, berth.centerY + 0.5f,
                        FITTING_ZOOM_NOTCHES, FITTING_SIDE_SHIFT);
            }
        };
    }

    private static CameraPose interpolate(CameraPose from, CameraPose to, float amount) {
        return new CameraPose(
                lerp(from.worldX(), to.worldX(), amount),
                lerp(from.worldY(), to.worldY(), amount),
                lerp(from.zoomNotches(), to.zoomNotches(), amount),
                lerp(from.sideShift(), to.sideShift(), amount));
    }

    private static boolean same(CameraPose left, CameraPose right) {
        return Float.compare(left.worldX(), right.worldX()) == 0
                && Float.compare(left.worldY(), right.worldY()) == 0
                && Float.compare(left.zoomNotches(), right.zoomNotches()) == 0
                && Float.compare(left.sideShift(), right.sideShift()) == 0;
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    /**
     * @param sideShift how far right of the view's centre the subject stands, as a share of the view's
     *                  width; the camera itself knows no pixels, so the canvas turns it into cells
     */
    public record CameraPose(float worldX, float worldY, float zoomNotches, float sideShift) { }
}
