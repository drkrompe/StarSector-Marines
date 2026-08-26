package com.dillon.starsectormarines.ops.battleview;

/** Eased presentation camera for the wide Mech Lab and its focused fitting view. */
public final class MechLabCameraController {

    static final float WIDE_ZOOM_NOTCHES = 0f;
    static final float FITTING_ZOOM_NOTCHES = 6.25f;
    static final float TRANSITION_SECONDS = 0.72f;

    private CameraPose current = widePose(0);
    private CameraPose transitionFrom = current;
    private CameraPose target = current;
    private float transitionSeconds = TRANSITION_SECONDS;

    public void target(boolean fittingFocused, int gantryIndex, int assignedAssets) {
        CameraPose requested = fittingFocused
                ? fittingPose(gantryIndex) : widePose(assignedAssets);
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
        current = fittingFocused ? fittingPose(gantryIndex) : widePose(assignedAssets);
        transitionFrom = current;
        target = current;
        transitionSeconds = TRANSITION_SECONDS;
    }

    static CameraPose widePose(int assignedAssets) {
        return new CameraPose(MechLabBattleScene.GRID_WIDTH * 0.5f,
                MechLabBattleScene.GRID_HEIGHT * 0.5f, WIDE_ZOOM_NOTCHES);
    }

    static CameraPose fittingPose(int gantryIndex) {
        return new CameraPose(MechLabBattleScene.mechWorldX(gantryIndex),
                MechLabBattleScene.mechWorldY(gantryIndex), FITTING_ZOOM_NOTCHES);
    }

    private static CameraPose interpolate(CameraPose from, CameraPose to, float amount) {
        return new CameraPose(
                lerp(from.worldX(), to.worldX(), amount),
                lerp(from.worldY(), to.worldY(), amount),
                lerp(from.zoomNotches(), to.zoomNotches(), amount));
    }

    private static boolean same(CameraPose left, CameraPose right) {
        return Float.compare(left.worldX(), right.worldX()) == 0
                && Float.compare(left.worldY(), right.worldY()) == 0
                && Float.compare(left.zoomNotches(), right.zoomNotches()) == 0;
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    public record CameraPose(float worldX, float worldY, float zoomNotches) { }
}
