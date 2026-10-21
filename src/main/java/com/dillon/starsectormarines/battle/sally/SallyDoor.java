package com.dillon.starsectormarines.battle.sally;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * A door in a walled place's own wall that stands closed, as wall, until the place's holder opens it from inside for a
 * sortie (see {@code garrisonables.md}, sally doors). A fort's sally port is one, authored into its plan; a walled
 * compound or a building with few exits gets one where {@link SallyDoorSurvey} finds its walls send the holder round.
 *
 * @param cells the wall cells the door opens, in world cells
 * @param insideX the open cell just inside the door
 * @param mouthX the open cell just outside it
 * @param place the place whose wall it is
 * @param placeDoor which of the place's doors it is, for the place's own record
 */
public record SallyDoor(int[][] cells, int insideX, int insideY, int mouthX, int mouthY, Holding place, int placeDoor) {

    /** The outward step through the door along x: -1, 0 or 1. */
    public int outX() {
        return Integer.signum(mouthX - insideX);
    }

    /** The outward step through the door along y: -1, 0 or 1. */
    public int outY() {
        return Integer.signum(mouthY - insideY);
    }

    /**
     * The place a door belongs to, as the door's rules need it: who holds it whole right now, which cells are inside it,
     * and where its middle and extent are, for which face a door is on.
     */
    public interface Holding {

        /** The side that holds the place whole now (in it, and nobody hostile contesting it), or null. */
        Faction holder(BattleSimulation sim);

        /** Whether the cell is inside the place. */
        boolean inside(int x, int y);

        /** The place's extent in world cells: {minX, minY, maxX, maxY}, inclusive. */
        int[] bounds();

        /** Told when one of its doors opens, for a place that draws its doors (a fort's port carries leaves). */
        default void opened(int placeDoor) { }

        /** A short name for evidence. */
        String label();
    }
}
