package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;

/**
 * Records {@link SwarmPackScene}: the whole ground, and a camera that follows
 * the pack at play zoom (32 px a cell, 30 cells across a 960 px frame) and
 * leans toward the squad once the pack is in the square, so a flank stays in
 * shot.
 */
public final class SwarmPackSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    /** Cells across the close view: 960 px at 32 px a cell. */
    private static final float PLAY_ZOOM_CELLS = 30f;
    private static final float SQUAD_X = 86.5f;
    private static final float SQUAD_Y = 18.5f;

    public SwarmPackSceneSnapshotSuite() {
        super(new SwarmPackScene(), 1920, 1080);
    }

    @Override protected float closeViewScale() { return 0.5f; }

    @Override
    protected ReviewAnnotations annotations(BattleSimulation sim) {
        // A scene's walls have no art, so the blocks either side of the street are boxed.
        return ReviewAnnotations.builder()
                .box(SwarmPackScene.STREET_X0, 0, SwarmPackScene.STREET_X1, SwarmPackScene.STREET_Y0 - 1,
                        "block", ReviewStyle.NOTE)
                .box(SwarmPackScene.STREET_X0, SwarmPackScene.STREET_Y1 + 1, SwarmPackScene.STREET_X1,
                        SwarmPackScene.HEIGHT - 1, "block", ReviewStyle.NOTE)
                .label(SQUAD_X, SQUAD_Y - 4f, "squad", ReviewStyle.NOTE)
                .build();
    }

    @Override
    protected float[] closeView(String loopId) {
        return new float[] {PLAY_ZOOM_CELLS / 2f, SwarmPackScene.HEIGHT / 2f, PLAY_ZOOM_CELLS};
    }

    @Override
    protected float[] closeView(String loopId, BattleSimulation sim) {
        float[] centre = SwarmPackScene.runnerCentre(sim);
        if (centre == null) return closeView(loopId);
        float x = centre[0];
        float y = centre[1];
        float lean = Math.max(0f, Math.min(1f, (x - 66f) / 10f)) * 0.5f;
        x += (SQUAD_X - x) * lean;
        y += (SQUAD_Y - y) * lean;
        float half = PLAY_ZOOM_CELLS / 2f;
        x = Math.max(half, Math.min(SwarmPackScene.WIDTH - half, x));
        y = Math.max(PLAY_ZOOM_CELLS * 9f / 32f, Math.min(SwarmPackScene.HEIGHT - PLAY_ZOOM_CELLS * 9f / 32f, y));
        return new float[] {x, y, PLAY_ZOOM_CELLS};
    }
}
