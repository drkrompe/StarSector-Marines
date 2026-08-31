package com.dillon.starsectormarines.battle.world.gen.road;

import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;

/**
 * The one road across the map a vehicle is guaranteed to be able to drive:
 * a band of {@link #WIDTH} walkable cells running the full length of the
 * traversal axis, from the defender's rear map edge, through the fortress,
 * out into the city.
 *
 * <p><b>Why this is authored before anything is built.</b> The deck family's
 * law is that rooms are packed and circulation is cut from what the packing
 * leaves ({@code ship-interiors-nouns.md}), and that law was measured — ruling
 * corridors first and subdividing between them yields slabs of uniform depth.
 * A vehicle route inverts it, for two reasons that do not apply to a deck.
 * It has an <em>external contract</em>: a convoy enters off-map at a known
 * edge point and has to reach the city, so the route is owed to a consumer
 * outside the generator rather than discovered inside it. And its minimum
 * width is a large enough fraction of the fortress that it cannot be found in
 * leftovers — a one-cell walking lane can be, five cells cannot.
 *
 * <p>Reserving one spine is not partitioning. The fortress ward still packs
 * its buildings into what remains and still cuts its own foot traffic from
 * the yard; only this band is owed in advance.
 *
 * <p><b>The band is the reservation, not its centerline.</b> The distinction
 * is the whole defect this exists to fix: {@link RoadReservation} marks the
 * road graph's centerline, one cell wide, and the stampers that honored it
 * left the city's main street crossing the fortress wall as a footpath. A
 * consumer asking "may I close this cell" must ask the band.
 *
 * @see VehicleCorridorPlan for how a map's corridor is derived from its trunks
 */
public final class VehicleCorridor {

    /**
     * Walkable cells across the corridor.
     *
     * <p>Five rather than three. Three is the arithmetic minimum — a
     * {@code HEAVY_APC} erodes to a radius-1 footprint, so it needs a clear
     * 3x3 and a 3-wide band leaves exactly one drivable line down the middle.
     * One line is not a road: a single wreck closes it permanently (a wreck
     * writes nothing to the nav map, but the vehicle behind it still cannot
     * pass a vehicle), nothing can be overtaken, and any stamp that clips one
     * cell of the band severs it. Five leaves three drivable lines and
     * matches {@link com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan.TrunkKind#SECONDARY}'s
     * own width, so the
     * fortress road reads continuous with the city street it joins rather
     * than pinching at the gate.
     */
    public static final int WIDTH = 5;

    /** True where a cell belongs to the corridor band. Indexed {@code [x][y]}. */
    private final boolean[][] band;

    /** The corridor's long axis — the traversal axis it was cut along. */
    public final TraversalAxis axis;

    /**
     * Where the corridor meets the defender's rear map edge: the off-map
     * entry a ground reinforcement drives in from. On the map border itself,
     * at the band's center line.
     */
    public final int entryX;
    public final int entryY;

    public VehicleCorridor(boolean[][] band, TraversalAxis axis, int entryX, int entryY) {
        this.band = band;
        this.axis = axis;
        this.entryX = entryX;
        this.entryY = entryY;
    }

    /** True if {@code (x, y)} is a corridor cell no stamp may close. Out-of-bounds is false. */
    public boolean contains(int x, int y) {
        if (x < 0 || x >= band.length || y < 0 || y >= band[x].length) return false;
        return band[x][y];
    }

    /**
     * The band mask itself. Callers must not mutate it — the corridor is an
     * authored fact and a stage that could edit it could silently narrow the
     * thing every later stage is checking against.
     */
    public boolean[][] band() {
        return band;
    }

    /** Cells in the band. Diagnostic — evidence and validation report it, generation does not branch on it. */
    public int cellCount() {
        int count = 0;
        for (boolean[] column : band) {
            for (boolean cell : column) {
                if (cell) count++;
            }
        }
        return count;
    }
}
