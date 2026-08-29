package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.List;

/**
 * A galley at one end, a serving counter across the middle, and the dining
 * floor beyond it.
 *
 * <p>The mess is the one compartment aboard that is a workplace and an amenity
 * at once, for two different populations, at the same moment. Everything else
 * is one or the other: a machinery space is worked and visited by nobody, a
 * lounge is visited and worked by nobody. Here the ship's cooks are on watch in
 * the room the whole complement passes through to eat, and the arrangement
 * exists to keep those two facts apart on the deck — which is what a servery
 * <em>is</em>.
 *
 * <p>So the room is divided along its length rather than {@linkplain
 * AisleFitting ranked}. Ranked, it was tables to the horizon: a hall with
 * nowhere the food came from, publishing {@link Affordance#MESS} and nothing
 * else, which gave a ship whose entire complement ate three meals a day that
 * nobody made.
 *
 * <p>{@linkplain #handed() Handed}, because those two ends are not
 * interchangeable — people come in at the dining end and the galley is at the
 * back — and a mirror image is a different room.
 *
 * <p>A compartment too short to hold a galley, its counter and a rank of tables
 * is fitted as dining floor alone. A ship's second mess is somewhere to eat
 * rather than a second galley, and inventing a range in a small room would post
 * a cook to it.
 */
public final class MessHallFitting implements RoomFitting {

    /** Gangway between the galley's working lines, and in front of the counter. */
    private static final int GANGWAY = 2;

    /** The strip behind the counter that the servery is served from. */
    private static final int SERVING_SIDE = 1;

    /** Floor left round the dining grid so a hatch never opens onto a table. */
    private static final int LANE = 1;

    /**
     * The shortest compartment that gets a galley at all.
     *
     * <p>Stated rather than derived, and deliberately larger than the sum of
     * what has to fit. Derived from the art alone it moves with whatever the
     * registry happens to hold — a ship whose stand-in props are small would
     * put a range and a scullery into a nine-cell cabin, and every {@link
     * Affordance#COOK} point is a posting. Where the line falls is a judgement
     * about which rooms are the ship's galley and which are somewhere else to
     * eat, and that is not a measurement.
     */
    private static final int SHORTEST_GALLEY = 16;

    /** A galley needs beam enough for a counter with a way through it. */
    private static final int NARROWEST_GALLEY = 8;

    private static final String RANGE = "doodad.galley-range";
    private static final String PREP_BENCH = "doodad.galley-prep-bench";
    private static final String SERVERY = "doodad.galley-servery-counter";
    private static final String SCULLERY = "doodad.galley-scullery-sink";
    private static final String COLD_STORE = "doodad.galley-cold-store";
    private static final String MESS_TABLE = "doodad.mess-table-long";

    /**
     * What each authored piece falls back to when its art is not in the
     * registry.
     *
     * <p>A fitting may only ever name an id the registry has: a missing one
     * makes {@code place} return false without saying so, and a room whose kit
     * half-exists comes out bare with nothing to explain it. So every piece
     * names something that has been in the urban set since the beginning, and a
     * galley whose art has not been packed is a worse-looking galley rather than
     * an empty hall.
     */
    private static final List<String[]> STAND_INS = List.of(
            new String[] { RANGE, "doodad.industrial-machine-tool" },
            new String[] { PREP_BENCH, "doodad.office-conference-table" },
            new String[] { SERVERY, "doodad.office-reception-counter" },
            new String[] { SCULLERY, "doodad.industrial-fluid-tank" },
            new String[] { COLD_STORE, "doodad.industrial-crate-stack" },
            new String[] { MESS_TABLE, "doodad.office-conference-table" });

    @Override
    public RoomPurpose purpose() {
        return RoomPurpose.MESS_HALL;
    }

    @Override
    public boolean handed() {
        return true;
    }

    @Override
    public void fit(RoomFloor floor) {
        int along = floor.canonicalWidth();
        int across = floor.canonicalHeight();
        int[] table = span(floor, MESS_TABLE);
        if (along < table[0] + 2 * LANE || across < table[1] + 2 * LANE) return;

        // Before anything is placed, not after. A lane is only reserved over
        // deck that is still free, so a surround claimed at the end of the fill
        // is claimed over whatever already stands there — which is to say not at
        // all. The room then furnishes a scullery against the very bulkhead the
        // deck cut its hatch in, the hatch has no free cell to open onto, and
        // the connectivity check reports the whole compartment unreachable and
        // throws the fill away. Nothing about the arrangement is wrong; it is
        // purely that the door was consulted last.
        for (Doorway door : floor.localDoors()) {
            floor.reserveLane(door.x() - 1, door.y() - 1, 3, 3);
        }

        int galley = galleyDepth(floor);
        int diningFrom = 0;
        if (along >= Math.max(SHORTEST_GALLEY, galley + GANGWAY + table[0] + 2 * LANE)
                && across >= NARROWEST_GALLEY) {
            int gap = across / 2 - 1;
            layGalley(floor, across, gap);
            layServery(floor, galley, across, gap);
            reserve(floor, galley + 1, 0, GANGWAY, across);
            diningFrom = galley + 1 + GANGWAY;
        }
        layDining(floor, diningFrom, along, across, table);
    }

    /**
     * The working end: a heat line against the end bulkhead, a preparation line
     * facing it, and the gangway between them.
     *
     * <p>Two lines across the room rather than runs down its sides, because the
     * galley's own long axis is the ship's beam here — six cells deep and the
     * full width — and a run laid down the short way is three appliances and a
     * corner. It is the same reasoning that gives a machinery space flats and an
     * alley, arrived at from a room a quarter the size.
     *
     * <p>Ranges against the bulkhead specifically, so a cook at the stove has
     * their back to a wall rather than to somebody carrying a tray.
     */
    private void layGalley(RoomFloor floor, int across, int gap) {
        int heat = Math.max(span(floor, RANGE)[0], span(floor, SCULLERY)[0]);
        int prep = Math.max(span(floor, PREP_BENCH)[0], span(floor, COLD_STORE)[0]);

        // The heat line stands against the end bulkhead and needs no way
        // through it: nothing is behind it, and a cook at the stove has their
        // back to a wall rather than to somebody carrying a tray.
        layLine(floor, 0, across, -1, RANGE, SCULLERY, Affordance.COOK, Affordance.COOK);
        reserve(floor, heat, 0, GANGWAY, across);

        // The preparation line does, and forgetting it sealed the galley: the
        // way through the counter opened onto an unbroken row of benches, so
        // the whole working end was walled off from the ship and every job in
        // it was dropped as unreachable. A line across a room is a wall unless
        // it is told not to be.
        layLine(floor, heat + GANGWAY, across, gap,
                PREP_BENCH, COLD_STORE, Affordance.COOK, Affordance.STOW);
        reserve(floor, heat + GANGWAY, gap, prep + SERVING_SIDE, GANGWAY);
        reserve(floor, heat + GANGWAY + prep, 0, SERVING_SIDE, across);
    }

    /**
     * One line of the galley: the working piece repeated across, the corner
     * piece at each end of it, and a gap left where somebody has to get past.
     *
     * <p>The ends are a different piece on purpose. A line of one appliance
     * repeated is a wall of appliances; a line that runs sink, stove, stove,
     * stove, sink is a galley, and the difference costs one extra id.
     */
    private void layLine(RoomFloor floor, int alongAt, int across, int gap,
                         String middle, String end,
                         Affordance middleWork, Affordance endWork) {
        int[] endSpan = span(floor, end);
        int[] middleSpan = span(floor, middle);
        place(floor, end, alongAt, 0, endWork);
        place(floor, end, alongAt, across - endSpan[1], endWork);
        for (int cell = endSpan[1]; cell + middleSpan[1] <= across - endSpan[1];
             cell += middleSpan[1]) {
            if (gap >= 0 && cell + middleSpan[1] > gap && cell < gap + GANGWAY) continue;
            place(floor, middle, alongAt, cell, middleWork);
        }
    }

    /**
     * The counter, spanning the room but for the way through.
     *
     * <p>Served from the galley side, which is why a serving position is
     * {@link Affordance#COOK} and not {@link Affordance#MESS}: dishing up is the
     * last of the cook's work rather than the first of the diner's. The gap is
     * left rather than sealed because the galley has to be reachable, and an
     * unreachable working end now costs the whole room its fill rather than
     * going quietly.
     */
    private void layServery(RoomFloor floor, int galley, int across, int gap) {
        int[] counter = span(floor, SERVERY);
        layCounter(floor, galley, 0, gap, counter[1]);
        layCounter(floor, galley, gap + GANGWAY, across, counter[1]);
        reserve(floor, galley, gap, counter[0], GANGWAY);
    }

    /**
     * One run of counter, served from behind at each end of it.
     *
     * <p>Served at the ends — where a queue reaches a counter — rather than at
     * every section, because a servery is one place people are handed things
     * from however long it is drawn, and a job per section would post a cook
     * per foot of it.
     *
     * <p>The serving position is placed by hand on the galley side rather than
     * left to the floor to find, which would put it in the queue. Which side of
     * a counter somebody stands on is the entire difference between serving and
     * being served.
     */
    private void layCounter(RoomFloor floor, int galley, int from, int to, int width) {
        int last = from;
        for (int cell = from; cell + width <= to; cell += width) last = cell;
        for (int cell = from; cell + width <= to; cell += width) {
            if (!place(floor, SERVERY, galley, cell, null)) continue;
            if (cell != from && cell != last) continue;
            int[] stand = floor.toLocalRect(galley - SERVING_SIDE, cell, 1, 1);
            int[] fixture = floor.toLocalRect(galley, cell, 1, 1);
            floor.fixtureTask(stand[0], stand[1], Affordance.COOK, fixture[0], fixture[1]);
        }
    }

    /**
     * Tables ranked in a grid, each with a rank of seats down either side.
     *
     * <p>Ranks are right here and wrong in a lounge, and the difference is what
     * the room is for. People in a lounge are talking to each other, so they sit
     * in groups facing inward; people in a mess are eating, in sittings, and a
     * mess deck that seats them in scattered conversation groups seats half as
     * many and reads as a canteen.
     *
     * <p>The seats are the point of the fill. A table placed with an affordance
     * affords <em>one</em> sitting — the model finds a single cell beside the
     * fixture — so a twelve-foot table would seat one person. Here the table is
     * furniture and every cell down both of its long sides is published
     * separately against it, which is the one place in the room where the
     * fixture and the job are deliberately not one to one.
     */
    private void layDining(RoomFloor floor, int from, int along, int across, int[] table) {
        // A block is the table plus the rank of seats down each side, so the
        // pitch carries no lane of its own: two blocks side by side put their
        // outer seats back to back, which is what a mess deck looks like.
        int pitchAcross = table[1] + 2;
        int pitchAlong = table[0] + LANE;
        int usableAcross = across - 2 * LANE;
        int columns = usableAcross / pitchAcross;
        if (columns <= 0) return;
        int firstAcross = LANE + (usableAcross - columns * pitchAcross) / 2;

        reserve(floor, from, 0, along - from, firstAcross);
        int pastAcross = firstAcross + columns * pitchAcross;
        reserve(floor, from, pastAcross, along - from, across - pastAcross);

        for (int rank = from + LANE; rank + table[0] <= along; rank += pitchAlong) {
            for (int column = 0; column < columns; column++) {
                int seat = firstAcross + column * pitchAcross;
                layTable(floor, rank, seat, table);
            }
            reserve(floor, rank + table[0], 0, LANE, across);
        }
    }

    /** One table, and a seat at every cell down both of its long sides. */
    private void layTable(RoomFloor floor, int rank, int seat, int[] table) {
        if (!place(floor, MESS_TABLE, rank, seat + 1, null)) return;
        for (int cell = 0; cell < table[0]; cell++) {
            seatAt(floor, rank + cell, seat, rank, seat + 1);
            seatAt(floor, rank + cell, seat + 1 + table[1], rank, seat + 1);
        }
    }

    /** One place at table, standing on the deck beside it. */
    private void seatAt(RoomFloor floor, int along, int across,
                        int tableAlong, int tableAcross) {
        int[] cell = floor.toLocalRect(along, across, 1, 1);
        int[] fixture = floor.toLocalRect(tableAlong, tableAcross, 1, 1);
        floor.fixtureTask(cell[0], cell[1], Affordance.MESS, fixture[0], fixture[1]);
    }

    /**
     * How deep the galley is: its two working lines and the gangway between
     * them, measured from the art rather than fixed.
     *
     * <p>A fixed depth would hold a range in half the compartments on a ship and
     * quietly refuse it in the other half, because a quarter turn swaps which
     * way a piece of art reaches. That is the worst kind of defect here: the
     * plan still looks right.
     */
    private int galleyDepth(RoomFloor floor) {
        int heat = Math.max(span(floor, RANGE)[0], span(floor, SCULLERY)[0]);
        int prep = Math.max(span(floor, PREP_BENCH)[0], span(floor, COLD_STORE)[0]);
        return heat + GANGWAY + prep + SERVING_SIDE;
    }

    /**
     * How much of the canonical frame one piece of art covers, as
     * {@code {along, across}}, swapped for a quarter-turned compartment.
     *
     * <p>Read off the registry rather than restated here, because a footprint is
     * a property of the art and a number copied into a fitting goes stale in
     * silence. Doodads are placed unrotated, so a piece drawn three cells long
     * lies along the <em>deck's</em> x axis whatever the room did.
     */
    private static int[] span(RoomFloor floor, String id) {
        DoodadDef def = definition(available(id));
        int x = def == null ? 1 : def.footprintCellsX;
        int y = def == null ? 1 : def.footprintCellsY;
        return floor.pose().upright() ? new int[]{ x, y } : new int[]{ y, x };
    }

    private static boolean place(RoomFloor floor, String id, int along, int across,
                                 Affordance affordance) {
        String art = available(id);
        int[] extent = span(floor, id);
        int[] rect = floor.toLocalRect(along, across, extent[0], extent[1]);
        return affordance == null
                ? floor.place(art, rect[0], rect[1])
                : floor.place(art, rect[0], rect[1], affordance);
    }

    /** The authored id if the registry has it, else this piece's stand-in. */
    private static String available(String id) {
        if (definition(id) != null) return id;
        for (String[] piece : STAND_INS) {
            if (piece[0].equals(id)) return piece[1];
        }
        return id;
    }

    private static DoodadDef definition(String id) {
        TileRegistry registry = TileRegistry.installed();
        return registry == null ? null : registry.doodad(id);
    }

    private void reserve(RoomFloor floor,
                         int along, int across, int alongSpan, int acrossSpan) {
        if (alongSpan <= 0 || acrossSpan <= 0) return;
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        floor.reserveLane(rect[0], rect[1], rect[2], rect[3]);
    }
}
