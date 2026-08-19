package com.dillon.starsectormarines.battle.perception;

import com.dillon.starsectormarines.battle.unit.Faction;

/** One launch, impact, or future loud world event awaiting squad detection. */
public record NoiseEvent(float x,
                         float y,
                         float magnitude,
                         long sourceUnitId,
                         Faction sourceFaction,
                         NoiseKind kind,
                         int emittedTick) {

    public boolean hasIdentifiedSource() {
        return sourceUnitId != 0L;
    }
}
