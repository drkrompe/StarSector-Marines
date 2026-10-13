package com.dillon.starsectormarines.marine;

import java.io.Serializable;

/** Physical shoulder hardpoint in cells, independent of sprite or animation data. */
public record MissilePodMountSpec(float rightCells, float forwardCells,
                                 float muzzleForwardCells) implements Serializable {
    public MissilePodMountSpec {
        if (!Float.isFinite(rightCells) || !Float.isFinite(forwardCells)
                || !Float.isFinite(muzzleForwardCells) || muzzleForwardCells < 0f) {
            throw new IllegalArgumentException("Pod mount coordinates must be finite; muzzle reach cannot be negative");
        }
    }
}
