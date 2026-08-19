package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.perception.NoiseKind;

/** Most recent localized noise retained for investigation and diagnostics. */
public record AudibleBearing(int cellX,
                             int cellY,
                             int heardTick,
                             float confidence,
                             long sourceUnitId,
                             NoiseKind kind) {
}
