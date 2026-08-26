package com.dillon.starsectormarines.battle.ambient;

/** Exact deterministic pose sampled from an {@link AmbientTaskRoute}. */
public record AmbientTaskPose(
        float worldX,
        float worldY,
        float facingDegrees,
        float locomotionPhase,
        float actionPhase,
        float focusX,
        float focusY,
        float headLookDegrees,
        boolean moving,
        AmbientActivity activity) { }
