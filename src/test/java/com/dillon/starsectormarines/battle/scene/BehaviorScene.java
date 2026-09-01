package com.dillon.starsectormarines.battle.scene;

import java.util.List;

/**
 * One discoverable behaviour scene: builds its world, plays its loops, and
 * judges them.
 *
 * <p>Registered through {@code META-INF/services} so the evidence task and the
 * snapshot adapter find it the same way. A scene owns its loop list — subject
 * and control, infantry and mech — and returns one {@link SceneReport} per
 * loop; the frame sink is how a snapshot suite gets the picture of the very
 * same play the verdicts were read from.
 */
public interface BehaviorScene {

    /** Stable kebab-case id, used as the report directory and the snapshot selector. */
    String id();

    /** One line saying what question the scene asks. */
    String label();

    /**
     * Plays every loop and returns one report per loop, in loop order. Frames
     * go to {@code frames} at the scene's own cadence; pass {@link FrameSink#NONE}
     * to play for verdicts alone.
     */
    List<SceneReport> play(FrameSink frames) throws Exception;

    /** Playback delay between recorded frames when a suite assembles them into an animation. */
    default int frameDelayMillis() {
        return 70;
    }
}
