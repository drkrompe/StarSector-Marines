package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.squad.BeliefSource;

/**
 * One deterministically merged squad report in a faction commander's threat set.
 *
 * @param structure whether the believed body is an emplacement (a nest, a battery) rather than a man or a machine, which
 *                  is what a plan reading the state of a fort's nests needs to tell from the garrison standing beside them
 */
public record CommanderContact(long unitId,
                               int cellX,
                               int cellY,
                               int observedTick,
                               float confidence,
                               float strength,
                               BeliefSource source,
                               int reporterSquadId,
                               boolean structure) {

    /** A report of something that is not an emplacement. */
    public CommanderContact(long unitId, int cellX, int cellY, int observedTick, float confidence, float strength,
                            BeliefSource source, int reporterSquadId) {
        this(unitId, cellX, cellY, observedTick, confidence, strength, source, reporterSquadId, false);
    }
}
