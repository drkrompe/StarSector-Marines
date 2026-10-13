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
 * @param seenCellX       the cell whose being in view discloses the structure to a side that did not build it. It is the
 *                        anchor for a bare structure; for a fort it is the battery platform, because the bunker the
 *                        commander is sent to stands inside walls nobody outside can see
 * @param seenCellY       as {@code seenCellX}
 */
public record DefenceStructureFact(Kind kind, int cellX, int cellY, Faction holder, Faction originalHolder,
                                   boolean threatened, int reachCells, int seenCellX, int seenCellY) {

    /** A structure disclosed by sighting its own anchor, which is every bare structure. */
    public DefenceStructureFact(Kind kind, int cellX, int cellY, Faction holder, Faction originalHolder,
                                boolean threatened, int reachCells) {
        this(kind, cellX, cellY, holder, originalHolder, threatened, reachCells, cellX, cellY);
    }

    /**
     * The structure kinds a command can be sent to take, wreck or hold. {@code SHIELD_GENERATOR} is where the dome is
     * held (the generator's capture ring, or a shield fort's control room); {@code FORT_POWER} is a fort's power plant,
     * which a side takes or wrecks to drop a dome that is not its own.
     */
    public enum Kind { FIRE_CONTROL, SHIELD_GENERATOR, FORT_POWER }
}
