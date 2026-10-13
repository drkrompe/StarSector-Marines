package com.dillon.starsectormarines.ops.battleview;

import java.util.ArrayList;
import java.util.List;

/**
 * The shader-drawn part of this frame's domes, queued as one custom command.
 * Holds the frozen per-dome numbers {@link ShieldDomeRenderSystem} collected, so
 * the GL drain and the offline (Java2D) drain read the same thing; running it
 * draws the domes with the shader.
 */
final class ShieldDomePass implements Runnable {

    private final ShieldDomeShader shader;
    private final List<ShieldDomeDraw> draws = new ArrayList<>();
    private float timeSeconds;

    ShieldDomePass(ShieldDomeShader shader) {
        this.shader = shader;
    }

    /** Starts a frame: forgets the previous domes and takes the presentation clock. */
    void reset(float timeSeconds) {
        draws.clear();
        this.timeSeconds = timeSeconds;
    }

    void add(ShieldDomeDraw draw) {
        draws.add(draw);
    }

    boolean isEmpty() {
        return draws.isEmpty();
    }

    List<ShieldDomeDraw> draws() {
        return draws;
    }

    float timeSeconds() {
        return timeSeconds;
    }

    @Override
    public void run() {
        shader.draw(this);
    }
}
