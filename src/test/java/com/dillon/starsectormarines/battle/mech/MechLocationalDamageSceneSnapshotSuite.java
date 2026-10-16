package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

import java.util.stream.IntStream;

/**
 * {@link MechLocationalDamageScene} recorded at 1920x1080, 100% scale, from the same play the
 * verdicts are read from: the Hound walking in, losing its pod, crippled at the legs, firing where
 * it stands, and the torso kill. The arena is forty-eight cells across, so the whole map is already
 * close. Stills are the walk-in (pod still fitted), the first frames at or after the pod loss, the
 * leg loss, the first crippled shot and the death, the frame after each section loss (its burst),
 * and the midpoints between those moments: the pod's dropped layer and smouldering stump, and a
 * crippled Hound smoking at the legs.
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
        IntStream.Builder frames = IntStream.builder();
        // Frame 1 rather than 0: the first frame precedes the first tick, before any body is drawn.
        frames.add(1);
        for (int tick : ticks) frames.add(frameAt(tick));
        // Between key moments, where no fresh impact hides the chassis: the pod's empty mount and
        // its smouldering stump between pod and leg loss, and the leg smoke between the first
        // crippled shot and the kill. Each loss also gets the frame after it, for its burst.
        for (int i = 0; i + 1 < ticks.length; i++) {
            if (ticks[i] < 0 || ticks[i + 1] < 0) continue;
            frames.add(frameAt((ticks[i] + ticks[i + 1]) / 2));
        }
        for (int i = 0; i < Math.min(2, ticks.length); i++) {
            if (ticks[i] >= 0) frames.add(frameAt(ticks[i]) + 1);
        }
        return frames.build().filter(f -> f > 0).distinct().sorted().toArray();
    }

    private static int frameAt(int tick) {
        return tick < 0 ? -1
                : (tick + MechLocationalDamageScene.FRAME_EVERY_TICKS - 1)
                        / MechLocationalDamageScene.FRAME_EVERY_TICKS;
    }
}
