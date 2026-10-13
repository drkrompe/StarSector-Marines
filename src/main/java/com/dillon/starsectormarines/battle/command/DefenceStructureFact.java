package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * One ground-defence structure a command may care about: a fortress battery's fire control or a shield generator.
 * Frozen state, free of the live simulation. What a given side is <em>allowed to know</em> of it is decided where the
 * facts are disclosed ({@link ConquestCommandFacts}), not here.
 *
 * @param kind            which kind of structure it is
 * @param cellX           the structure's anchor cell
 * @param cellY           the structure's anchor cell
 * @param holder          the side that holds it now
 * @param originalHolder  the side it was built for, which keeps knowing where it is after it is taken
 * @param threatened      whether its holder is being pressed: a contested fire control, or a capture under way
 * @param reachCells      how far from the anchor a side must stand to take or hold it, in cells
 */
public record DefenceStructureFact(Kind kind, int cellX, int cellY, Faction holder, Faction originalHolder,
                                   boolean threatened, int reachCells) {

    /** The two structure kinds a command can be sent to take, wreck or hold. */
    public enum Kind { FIRE_CONTROL, SHIELD_GENERATOR }
}
