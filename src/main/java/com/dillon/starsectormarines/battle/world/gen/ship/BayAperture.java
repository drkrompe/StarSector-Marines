package com.dillon.starsectormarines.battle.world.gen.ship;

/**
 * The door in a bay's outboard bulkhead — where what is kept in the bay leaves
 * the ship, and where it comes back.
 *
 * <p><b>The one place on a deck that is not somewhere aboard.</b> Every other
 * opening on a ship joins two compartments; this one joins a compartment to
 * nothing, and is what makes a bay the deck's own way in and out rather than a
 * hold with boats in it. It is the ship's answer to the question a battle map
 * answers with a map edge: which way is off, and from where.
 *
 * <p>Placement aligns the pressure opening with the bay's central aisle on
 * its authored outboard side. The outward strip may cross unused hull plating,
 * but never another room or circulation. A ship-only generation stage opens
 * that strip after structural finalization.
 *
 * <p>The opening is sight-clear vacuum rather than infantry floor. Its
 * pressure-screen boundary allows boats to depart while ordinary deck
 * navigation remains confined to the room's inboard apron.
 *
 * <p>Coordinates are continuous world coordinates (cell centres lie at index
 * plus one half), the same space an
 * {@link com.dillon.starsectormarines.battle.air.AirBody} flies in, so a craft
 * can be steered at the door without converting. The direction is a unit
 * cardinal pointing <em>out</em> of the hull.
 *
 * @param compartmentId the bay this door belongs to
 * @param centerX       middle of the opening, in cell centres
 * @param centerY       middle of the opening, in cell centres
 * @param outDx         cardinal pointing out of the hull
 * @param outDy         cardinal pointing out of the hull
 * @param widthCells    how much of the bulkhead the door spans, across {@code out}
 */
public record BayAperture(int compartmentId, float centerX, float centerY,
                          int outDx, int outDy, int widthCells) {

    public BayAperture {
        if (widthCells <= 0) {
            throw new IllegalArgumentException("a door has width: " + widthCells);
        }
        if (Math.abs(outDx) + Math.abs(outDy) != 1) {
            throw new IllegalArgumentException(
                    "out of the hull is one cardinal: " + outDx + "," + outDy);
        }
    }

    /**
     * A point clear of the hull on the outboard side, {@code cells} out.
     *
     * <p>What a craft is steered at once it has left, and steered from on the
     * way in. Outside the deck's own coordinates on purpose — off the map is
     * exactly what is on the other side of this door.
     */
    public float[] offshipAt(float cells) {
        return new float[]{centerX + outDx * cells, centerY + outDy * cells};
    }

    /**
     * The cell just inboard of the door: the deck a boat is moved onto before it
     * goes out, and the first thing it stands on coming back.
     */
    public float[] inboardStep() {
        return new float[]{centerX - outDx, centerY - outDy};
    }
}
