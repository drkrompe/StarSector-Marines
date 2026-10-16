package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

import java.util.Arrays;

/**
 * {@link MechLocationalDamageScene} recorded at 1920x1080, 100% scale, from the same play the
 * verdicts are read from: the Hound walking in, losing its pod, crippled at the legs, firing where
 * it stands, and the torso kill. The arena is forty-eight cells across, so the whole map is already
 * close. Stills are the walk-in (pod still fitted) and the first frames at or after the pod loss, the leg loss, the
 * first crippled shot and the death.
 */
public final class MechLocationalDamageSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    private final MechLocationalDamageScene scene;

    public MechLocationalDamageSceneSnapshotSuite() {
        this(new MechLocationalDamageScene());
    }

    private MechLocationalDamageSceneSnapshotSuite(MechLocationalDamageScene scene) {
        super(scene, WIDTH, HEIGHT);
        this.scene = scene;
    }

    @Override protected boolean showImpactEffects() { return true; }

    /**
     * The Hound's own ground, close enough to read the chassis: the whole map draws it as a marker,
     * and the pod's layer dropping off the appearance is the visible half of losing it.
     */
    @Override
    protected float[] closeView(String loopId) {
        return MechLocationalDamageScene.LOOP.equals(loopId) ? new float[] {17f, 13.5f, 16f} : null;
    }

    @Override protected float closeViewScale() { return 1f; }

    @Override
    protected int[] stillFrames(String loopId) {
        int[] ticks = scene.keyTicks();
        int[] frames = new int[ticks.length + 1];
        // Frame 1 rather than 0: the first frame precedes the first tick, before any body is drawn.
        frames[0] = 1;
        for (int i = 0; i < ticks.length; i++) {
            frames[i + 1] = ticks[i] < 0 ? -1
                    : (ticks[i] + MechLocationalDamageScene.FRAME_EVERY_TICKS - 1)
                            / MechLocationalDamageScene.FRAME_EVERY_TICKS;
        }
        return Arrays.stream(frames).distinct().toArray();
    }
}
