package com.dillon.starsectormarines.ops.battleview;

/** Eased presentation camera for the wide Mech Lab and its focused fitting view. */
public final class MechLabCameraController {

    static final float WIDE_ZOOM_NOTCHES = 1.25f;
    static final float FITTING_ZOOM_NOTCHES = 5f;
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
        int count = Math.max(0, Math.min(MechLabSceneLayout.GANTRIES.size(), assignedAssets));
        if (count == 0) {
            return new CameraPose(MechLabBattleScene.GRID_WIDTH * 0.5f,
                    MechLabBattleScene.GRID_HEIGHT * 0.5f, WIDE_ZOOM_NOTCHES);
        }
        float firstX = MechLabBattleScene.mechWorldX(0);
        float lastX = MechLabBattleScene.mechWorldX(count - 1);
        float zoomNotches = switch (count) {
            case 1, 2 -> 3.2f;
            case 3 -> 2.5f;
            default -> WIDE_ZOOM_NOTCHES;
        };
        return new CameraPose((firstX + lastX) * 0.5f,
                MechLabBattleScene.GRID_HEIGHT * 0.5f, zoomNotches);
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
