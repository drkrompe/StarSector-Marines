package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;

/**
 * The fire-support APC scene's frames, from the same run that produced its verdicts: both loops at 1920x1080 and
 * 100% scale, whole map plus a full-size close view framing the mortar carrier, the position and the troop
 * carrier's landing, with impact effects drawn so the shells are visible landing. Every fourth close frame is also written as a
 * PNG still.
 */
public final class FireSupportApcSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public FireSupportApcSceneSnapshotSuite() {
        super(new FireSupportApcScene(15), WIDTH, HEIGHT);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected float[] closeView(String loopId) {
        return new float[] {
                (FireSupportApcScene.FIRE_SUPPORT_X + FireSupportApcScene.POSITION_X) / 2f + 1f,
                (FireSupportApcScene.FIRE_SUPPORT_Y + FireSupportApcScene.LZ_Y) / 2f + 1f,
                44f};
    }

    @Override protected float closeViewScale() { return 1f; }

    @Override protected ReviewAnnotations annotations(BattleSimulation simulation) {
        int px = (int) FireSupportApcScene.POSITION_X;
        int py = (int) FireSupportApcScene.POSITION_Y;
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder()
                .box(px - 4, py - 4, px + 4, py + 4, "POSITION", ReviewStyle.OBJECTIVE)
                .label(FireSupportApcScene.LZ_X, FireSupportApcScene.LZ_Y + 2.5f, "LZ", ReviewStyle.LANDING)
                .label(FireSupportApcScene.SPOTTER_X - 1.5f, FireSupportApcScene.SPOTTER_Y - 1.5f, "SPOTTERS",
                        ReviewStyle.NOTE);
        for (long id : simulation.getConvoyVehicleIds()) {
            if (simulation.convoy().vehicleType(id) == VehicleType.FIRE_SUPPORT_APC) {
                marks.label(FireSupportApcScene.FIRE_SUPPORT_X, FireSupportApcScene.FIRE_SUPPORT_Y - 2.5f,
                        "FIRE SUPPORT APC", ReviewStyle.NOTE);
            }
        }
        return marks.build();
    }

    @Override protected int[] stillFrames(String loopId) {
        if (!loopId.endsWith("-close")) return new int[0];
        int[] all = new int[20];
        for (int i = 0; i < all.length; i++) all[i] = i * 4;
        return all;
    }
}
