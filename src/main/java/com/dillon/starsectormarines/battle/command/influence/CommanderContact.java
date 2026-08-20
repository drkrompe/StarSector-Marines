package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.squad.BeliefSource;

/** One deterministically merged squad report in a faction commander's threat set. */
public record CommanderContact(long unitId,
                               int cellX,
                               int cellY,
                               int observedTick,
                               float confidence,
                               BeliefSource source,
                               int reporterSquadId) {
}
