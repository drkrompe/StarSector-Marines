package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * One ground-defence structure a command may care about: a fortress battery's fire control, a shield generator, or an
 * aspect of a fort. Frozen state, free of the live simulation. What a given side is <em>allowed to know</em> of it is
 * decided where the facts are disclosed ({@link ConquestCommandFacts}), not here.
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
 *                        commander is sent to stands inside walls nobody outside can see. It is also the key under
 *                        which a sighting is remembered
 * @param seenCellY       as {@code seenCellX}
 * @param fortId          the fort this aspect belongs to, or {@code -1} for a bare structure. Facts sharing a fort id are
 *                        one defence whose aspects a commander chooses between
 * @param family          what sort of fort the aspect belongs to, which says what losing each aspect costs
 * @param seenCells       further cells, as packed {@code x, y} pairs, any one of which in view also discloses the
 *                        structure: for a fort, its works (wall, gates, bastion nests), which a company walking up to
 *                        the fort sees long before it sees a platform standing inside them. Empty for a bare structure
 */
public record DefenceStructureFact(Kind kind, int cellX, int cellY, Faction holder, Faction originalHolder,
                                   boolean threatened, int reachCells, int seenCellX, int seenCellY,
                                   int fortId, Family family, int[] seenCells) {

    private static final int[] NO_CELLS = new int[0];

    public DefenceStructureFact {
        if (seenCells == null) seenCells = NO_CELLS;
    }

    /** An aspect of a fort disclosed only by sighting {@code seenCellX, seenCellY}. */
    public DefenceStructureFact(Kind kind, int cellX, int cellY, Faction holder, Faction originalHolder,
                                boolean threatened, int reachCells, int seenCellX, int seenCellY,
                                int fortId, Family family) {
        this(kind, cellX, cellY, holder, originalHolder, threatened, reachCells, seenCellX, seenCellY,
                fortId, family, NO_CELLS);
    }

    /** An aspect of no fort, disclosed at a cell other than its own. */
    public DefenceStructureFact(Kind kind, int cellX, int cellY, Faction holder, Faction originalHolder,
                                boolean threatened, int reachCells, int seenCellX, int seenCellY) {
        this(kind, cellX, cellY, holder, originalHolder, threatened, reachCells, seenCellX, seenCellY, -1, Family.BARE);
    }

    /** A structure disclosed by sighting its own anchor, which is every bare structure. */
    public DefenceStructureFact(Kind kind, int cellX, int cellY, Faction holder, Faction originalHolder,
                                boolean threatened, int reachCells) {
        this(kind, cellX, cellY, holder, originalHolder, threatened, reachCells, cellX, cellY);
    }

    /** Whether this is one aspect of a fort rather than a structure standing alone. */
    public boolean ofFort() {
        return fortId >= 0 && family != Family.BARE;
    }

    /**
     * The structure kinds a command can be sent to take, wreck or hold. {@code FIRE_CONTROL} is where a battery's
     * guns are held (the bunker, for a fort); {@code SHIELD_GENERATOR} is where the dome is held (the generator's
     * capture ring, or a shield fort's control room); {@code FORT_POWER} is a fort's power plant, which feeds a dome
     * or a battery's traverse; {@code FORT_MAGAZINE} is a battery fort's store, which feeds sustained fire.
     */
    public enum Kind { FIRE_CONTROL, SHIELD_GENERATOR, FORT_POWER, FORT_MAGAZINE }

    /** What a fort is for, which decides how much each of its aspects matters. */
    public enum Family { BARE, BATTERY_FORT, SHIELD_FORT }
}
