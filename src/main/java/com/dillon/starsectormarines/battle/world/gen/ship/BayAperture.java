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
 * <p><b>Published where the hull is known, not where the room is fitted.</b> A
 * fitting sees a floor, a pose and a set of doors, and could not say which of
 * its four bulkheads has vacuum behind it — the placer is what pushed the room
 * against the ship's side, and is deliberately left free to use whichever side
 * it can reach. So the aperture is derived at placement from the run of the
 * room's own ring that lies outside the hull, and a bay that somehow reached the
 * outside on two sides takes the longer run rather than being refused: the
 * mistake to avoid is a bay with no way out, and a bay with an awkward one is
 * still a bay.
 *
 * <p><b>It is a door, not a hole.</b> Nothing here is cut: the cells stay
 * bulkhead, the deck stays sealed, and the ship does not vent because a bay
 * exists. What this records is where the door is and which way is out, so that
 * whatever opens it — a boat leaving, a boarding party arriving — has geometry
 * to work from rather than a guess about which side of the room faces space.
 *
 * <p>Coordinates are cell centres in continuous cell space, the same space an
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
