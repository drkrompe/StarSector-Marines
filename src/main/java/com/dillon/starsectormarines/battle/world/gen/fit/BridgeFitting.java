package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The bridge as a plot amidships with the watch ringed round it, facing in.
 *
 * <p>A bridge is not a room with consoles in it. It is <b>one thing everybody is
 * looking at</b> and a ring of people looking at it, and every other property of
 * the compartment follows from that: why the middle is a table nobody sits at,
 * why there is a walkway round the table, why the stations are spaced rather
 * than packed, and why a door may be cut anywhere. Ranked like a berth instead —
 * which is how this room was furnished before — it read as a warehouse of
 * identical consoles, because ranks are what a warehouse is.
 *
 * <p>Everything is laid out as <b>concentric rings</b> about the plot, and the
 * ring number is the whole of the arrangement:
 *
 * <ul>
 *   <li>ring 0 — the plot itself;
 *   <li>ring 1 — the walkway round it, so the plot can be leaned over from any
 *       side;
 *   <li>ring 2 — the watch stations;
 *   <li>ring 3 — where their watchkeepers stand;
 *   <li>ring 4 — the outer walkway, hugging the bulkhead;
 *   <li>beyond — the bulkhead pockets, where the ancillary gear goes.
 * </ul>
 *
 * <p>The <b>watchkeeper stands outboard of his console and looks inboard</b>,
 * over it and across the plot. That is the one relationship the arrangement
 * exists to produce, and it is why the standing cells are chosen here rather
 * than searched for: {@link RoomFloor#place(String, int, int, Affordance)} finds
 * a cell beside the fixture and prefers one already reserved as circulation, and
 * on a bridge the reserved circulation is the walkway <em>inboard</em> of the
 * console — so every watchkeeper would have been posted with his back to his own
 * screens and to the plot. The same reason a firing range places its shooters
 * rather than letting them be found: the deck reserved for walking is downrange.
 *
 * <p>The station count is a <b>quota read off the room</b> rather than a figure
 * authored here. A ring's circumference grows with the plot it encloses, and a
 * room deep enough for another walkway takes another tier of stations three
 * rings further out — so a larger bridge stands a larger watch without anything
 * being told how large it is. {@link RoomFit#gap()} sets the pitch along each
 * ring, which makes a refit mean the same thing here as everywhere else: the
 * same floor, more of it worked.
 *
 * <p>No {@linkplain RoomFitting#hookups hookups} are authored, and that is a
 * property of the ring rather than an omission. The outer walkway runs the whole
 * way round the compartment, so a hatch cut anywhere on the bulkhead opens onto
 * circulation; a range has to say where its doors go because a door in the wrong
 * bulkhead opens into the beaten zone, and a bridge has no such place. For the
 * same reason it is not {@linkplain RoomFitting#handed() handed} — a ring has no
 * front.
 */
public final class BridgeFitting implements RoomFitting {

    /**
     * Cells across the plot, at most, and the footprint the plot's own art is
     * drawn at.
     *
     * <p>Capped because a plot is a table people stand around, and a table wider
     * than three cells is one nobody at the far side can reach across. Room size
     * is spent on more stations instead, which is what a bigger bridge actually
     * has.
     */
    private static final int PLOT_MAX = 3;

    /** How much of the room the plot may take before the cap bites. */
    private static final int PLOT_DIVISOR = 3;

    /** The ring the first tier of stations sits on, leaving ring 1 as the walkway. */
    private static final int FIRST_TIER = 2;

    /**
     * Rings from one tier of stations to the next: consoles, their watchkeepers,
     * and a walkway before the next tier begins.
     */
    private static final int TIER_PITCH = 3;

    /**
     * Cells along a ring between one station and the next, before the fit is
     * applied. Two is shoulder to shoulder for consoles that are one cell wide,
     * and the gaps it leaves are how the watch gets in and out.
     */
    private static final int STATION_PITCH = 2;

    /** Cells of bulkhead pocket between one piece of ancillary gear and the next. */
    private static final int GEAR_PITCH = 2;

    /**
     * The plot: one three-cell holographic plot, drawn as a single object.
     *
     * <p>The thing the whole room is arranged around, so it is one fixture and
     * not a pattern of nine. A slab of repeated chart tables gets the footprint
     * right and the reading wrong — nine little tables pushed together is what a
     * hall being used for a briefing looks like, and what a bridge has is one
     * plot everybody is standing at.
     */
    private static final String PLOT = "doodad.military-nav-plot";

    /**
     * What stands in for the plot where the room cannot seat it: chart tables,
     * laid cell by cell over whatever of the middle the outline covers.
     *
     * <p>A frigate's bridge is a small compartment and must still come out as a
     * bridge. This is also the honest answer when the middle of an odd outline
     * is not a clean square: {@link RoomFloor#place} takes a fixture entire or
     * not at all, so a three-cell plot in a room with two cells of middle is a
     * bridge with a hole where its plot should be.
     */
    private static final String PLOT_TABLE = "doodad.military-tactical-table";

    /** One watch station. The bridge's whole published capacity is these. */
    private static final String STATION = "doodad.military-command-console";

    /**
     * What fills the bulkhead pockets outboard of the outer walkway: the plot's
     * computers, the ship-systems panel, and the chart desks.
     *
     * <p>Scenery, deliberately. The bridge's job is the watch at the ring, and a
     * server rack published as somewhere to work would station a second crew
     * against the bulkhead doing nothing anybody could name — the same mistake
     * as making every prop a work point, which is how a deck acquires a
     * population invented out of furniture.
     */
    private static final String[] GEAR = {
            "doodad.office-server-rack",
            "doodad.desk-1",
            "doodad.industrial-control-console",
            "doodad.desk-2" };

    @Override
    public RoomPurpose purpose() {
        return RoomPurpose.BRIDGE;
    }

    @Override
    public void fit(RoomFloor floor) {
        Plot plot = plot(floor.canonicalWidth(), floor.canonicalHeight());
        Watch watch = plan(floor, plot, STATION_PITCH + floor.fit().gap());

        // Circulation before furniture, as everywhere: the walkway rings the
        // stations do not occupy, and a run inboard from every hatch. The run is
        // what cuts the doorway gap in each station ring it crosses — reserved
        // deck refuses a fixture, so the gap happens rather than being coped
        // with afterwards.
        for (int ring = 1; ring <= watch.outer(); ring++) {
            if (watch.ringed().contains(ring)) continue;
            reserveRing(floor, plot, ring);
        }
        for (Doorway door : floor.localDoors()) {
            clearApproach(floor, plot, door);
        }

        layPlot(floor, plot);
        layConn(floor, plot);
        layStations(floor, watch);
        layGear(floor, plot, watch.outer());
    }

    /** The plot's footprint, centred, and as large as the room justifies. */
    private static Plot plot(int along, int across) {
        int spanX = span(along);
        int spanY = span(across);
        return new Plot((along - spanX) / 2, (across - spanY) / 2, spanX, spanY);
    }

    private static int span(int extent) {
        return Math.max(1, Math.min(PLOT_MAX, extent / PLOT_DIVISOR));
    }

    /**
     * Work out where the watch stands, tier by tier, before anything is placed.
     *
     * <p>Planned rather than placed as it goes because the walkway rings are the
     * complement of the station rings: which deck is circulation cannot be known
     * until it is known how far out the stations reach, and reserving optimistic
     * lanes first would refuse the very cells the stations want.
     *
     * <p>A room too shallow for a walkway round its plot gets its consoles hard
     * against it instead. That is a worse bridge and it is still a bridge; the
     * alternative is a compartment that declares itself the ship's command
     * centre and comes out as bare deck.
     */
    private static Watch plan(RoomFloor floor, Plot plot, int pitch) {
        List<Station> stations = new ArrayList<>();
        Set<Integer> ringed = new HashSet<>();
        int outermost = 0;
        for (int ring = FIRST_TIER; ring <= floor.canonicalWidth() + floor.canonicalHeight();
                ring += TIER_PITCH) {
            List<Station> tier = tier(floor, plot, ring, pitch);
            if (tier.isEmpty()) break;
            stations.addAll(tier);
            ringed.add(ring);
            ringed.add(ring + 1);
            outermost = ring;
        }
        if (stations.isEmpty()) {
            stations.addAll(tier(floor, plot, 1, pitch));
            if (!stations.isEmpty()) {
                ringed.add(1);
                ringed.add(2);
                outermost = 1;
            }
        }
        // Outboard of the last tier's watchkeepers there is one more walkway, and
        // whatever lies beyond that is bulkhead pocket rather than bridge.
        return new Watch(stations, ringed, outermost == 0 ? 1 : outermost + 2);
    }

    /**
     * One tier: stations spaced along a ring, each with the cell its watchkeeper
     * stands in.
     *
     * <p>The pitch counts every cell of the ring, including the ones the room's
     * outline does not cover, so the stations stay evenly spaced <em>around</em>
     * the plot instead of bunching up wherever a chamfer cut the ring short.
     */
    private static List<Station> tier(RoomFloor floor, Plot plot, int ring, int pitch) {
        List<int[]> border = border(plot, ring);
        List<Station> stations = new ArrayList<>();
        for (int index = 0; index < border.size(); index += pitch) {
            int[] console = border.get(index);
            if (!floor.contains(console[0], console[1])) continue;
            int[] stand = outboardOf(floor, plot, console[0], console[1]);
            if (stand == null) continue;
            stations.add(new Station(console[0], console[1], stand[0], stand[1]));
        }
        return stations;
    }

    /**
     * The cell just outboard of a console, which is where its watchkeeper goes.
     *
     * <p>Two candidates at a ring's corner, where outboard is diagonal and a
     * standing cell has to be orthogonal to the fixture it faces. The axis the
     * console is further out along is tried first, so a station on a ring's long
     * side is worked from directly behind rather than from beside it.
     *
     * @return the cell, or null where the room's outline has nothing outboard —
     *     a console against the hull is furniture and affords nobody a watch
     */
    private static int[] outboardOf(RoomFloor floor, Plot plot, int x, int y) {
        int outX = x < plot.x() ? plot.x() - x : Math.max(0, x - plot.right());
        int outY = y < plot.y() ? plot.y() - y : Math.max(0, y - plot.bottom());
        int stepX = x < plot.x() ? -1 : (x > plot.right() ? 1 : 0);
        int stepY = y < plot.y() ? -1 : (y > plot.bottom() ? 1 : 0);
        int[][] candidates = outX >= outY
                ? new int[][]{ { stepX, 0 }, { 0, stepY } }
                : new int[][]{ { 0, stepY }, { stepX, 0 } };
        for (int[] step : candidates) {
            if (step[0] == 0 && step[1] == 0) continue;
            if (floor.contains(x + step[0], y + step[1])) {
                return new int[]{ x + step[0], y + step[1] };
            }
        }
        return null;
    }

    /**
     * The plot, in the middle of the compartment.
     *
     * <p>Offered its own art first, and only laid as chart tables where that is
     * refused. The plot is drawn square at {@link #PLOT_MAX} a side, so it is
     * asked for only when the room justified a plot of exactly that size —
     * square because a fixture's footprint is read in the room's own axes while
     * everything here is authored in the canonical frame, and a square is the
     * one footprint a quarter turn leaves alone. An oblong plot would come out
     * across the compartment on every turned deck, which is the kind of defect
     * that looks like a packing bug rather than a fitting one.
     */
    private void layPlot(RoomFloor floor, Plot plot) {
        int[] rect = floor.toLocalRect(plot.x(), plot.y(), plot.spanX(), plot.spanY());
        if (plot.spanX() == PLOT_MAX && plot.spanY() == PLOT_MAX
                && floor.place(PLOT, rect[0], rect[1])) {
            return;
        }
        for (int dx = 0; dx < plot.spanX(); dx++) {
            for (int dy = 0; dy < plot.spanY(); dy++) {
                place(floor, plot.x() + dx, plot.y() + dy, PLOT_TABLE);
            }
        }
    }

    /**
     * The conn: one watch kept at the plot itself, from the walkway round it.
     *
     * <p>The ring is where the ship is worked and the plot is where she is
     * conned, so the officer of the watch is a station like any other — he
     * simply stands at the middle facing out of it rather than at the edge
     * facing in. Without him the plot is a table with a permanent audience and
     * nobody in charge of it.
     *
     * <p>Any side will do; the first one the outline offers is taken, because
     * which side of a plot somebody stands at is not a fact about the ship.
     */
    private void layConn(RoomFloor floor, Plot plot) {
        int[][] sides = {
                { plot.centreX(), plot.y() - 1, plot.centreX(), plot.y() },
                { plot.centreX(), plot.bottom() + 1, plot.centreX(), plot.bottom() },
                { plot.x() - 1, plot.centreY(), plot.x(), plot.centreY() },
                { plot.right() + 1, plot.centreY(), plot.right(), plot.centreY() } };
        for (int[] side : sides) {
            int[] stand = floor.toLocal(side[0], side[1]);
            int[] table = floor.toLocal(side[2], side[3]);
            if (floor.fixtureTask(stand[0], stand[1], Affordance.WATCH, table[0], table[1])) {
                return;
            }
        }
    }

    /**
     * The stations, each with its watchkeeper posted outboard of it.
     *
     * <p>A console whose standing cell is refused still goes down. It is a
     * console either way, and a bridge with one station fewer is a better room
     * than a bridge with a hole in its ring.
     */
    private void layStations(RoomFloor floor, Watch watch) {
        for (Station station : watch.stations()) {
            int[] console = floor.toLocal(station.consoleX(), station.consoleY());
            if (!floor.place(STATION, console[0], console[1])) continue;
            int[] stand = floor.toLocal(station.standX(), station.standY());
            floor.fixtureTask(stand[0], stand[1], Affordance.WATCH, console[0], console[1]);
        }
    }

    /**
     * The bulkhead pockets: whatever deck the rings never reached, filled at a
     * pitch so the pocket stays somewhere a person can walk into.
     *
     * <p>These are the corners a ring arrangement always leaves — the points of
     * a diamond, the ends of a long compartment — and they are the one part of
     * this room that would otherwise be unargued deck.
     */
    private void layGear(RoomFloor floor, Plot plot, int outer) {
        int slot = 0;
        for (int x = 0; x < floor.canonicalWidth(); x++) {
            for (int y = 0; y < floor.canonicalHeight(); y++) {
                if (!floor.contains(x, y) || within(plot, x, y, outer)) continue;
                int index = slot++;
                if (index % GEAR_PITCH != 0) continue;
                place(floor, x, y, GEAR[(index / GEAR_PITCH) % GEAR.length]);
            }
        }
    }

    /**
     * Join one hatch to the walkway round the plot, a single cell wide.
     *
     * <p>Run inboard rather than reacted to afterwards. A station ring is
     * unbroken except where circulation crosses it, so the run is what makes the
     * doorway gap — and reserving it before the consoles go down is the
     * difference between a ring with a way through it and a ring that had to
     * have one cut back out, losing a station on a different cell of every deck.
     */
    private void clearApproach(RoomFloor floor, Plot plot, Doorway door) {
        int[] canonical = floor.toCanonical(door.x(), door.y());
        int x = Math.max(0, Math.min(floor.canonicalWidth() - 1, canonical[0]));
        int y = Math.max(0, Math.min(floor.canonicalHeight() - 1, canonical[1]));
        for (int step = floor.canonicalWidth() + floor.canonicalHeight(); step > 0; step--) {
            reserve(floor, x, y);
            if (within(plot, x, y, 1)) return;
            int toX = plot.centreX() - x;
            int toY = plot.centreY() - y;
            if (Math.abs(toX) >= Math.abs(toY)) x += Integer.signum(toX);
            else y += Integer.signum(toY);
        }
    }

    /** Reserve a whole ring about the plot as circulation. */
    private void reserveRing(RoomFloor floor, Plot plot, int ring) {
        for (int[] cell : border(plot, ring)) {
            reserve(floor, cell[0], cell[1]);
        }
    }

    /** The cells of one ring about the plot, walked in order so a pitch is even. */
    private static List<int[]> border(Plot plot, int ring) {
        int left = plot.x() - ring;
        int right = plot.right() + ring;
        int top = plot.y() - ring;
        int bottom = plot.bottom() + ring;
        List<int[]> cells = new ArrayList<>();
        for (int x = left; x <= right; x++) cells.add(new int[]{ x, top });
        for (int y = top + 1; y <= bottom; y++) cells.add(new int[]{ right, y });
        for (int x = right - 1; x >= left; x--) cells.add(new int[]{ x, bottom });
        for (int y = bottom - 1; y > top; y--) cells.add(new int[]{ left, y });
        return cells;
    }

    /** Whether a cell lies on or inside the given ring about the plot. */
    private static boolean within(Plot plot, int x, int y, int ring) {
        return x >= plot.x() - ring && x <= plot.right() + ring
                && y >= plot.y() - ring && y <= plot.bottom() + ring;
    }

    private void place(RoomFloor floor, int along, int across, String id) {
        int[] cell = floor.toLocal(along, across);
        floor.place(id, cell[0], cell[1]);
    }

    private void reserve(RoomFloor floor, int along, int across) {
        int[] cell = floor.toLocal(along, across);
        floor.reserveLane(cell[0], cell[1], 1, 1);
    }

    /** The plot's footprint in the canonical frame. */
    private record Plot(int x, int y, int spanX, int spanY) {

        int right() {
            return x + spanX - 1;
        }

        int bottom() {
            return y + spanY - 1;
        }

        int centreX() {
            return x + spanX / 2;
        }

        int centreY() {
            return y + spanY / 2;
        }
    }

    /** One station: the console, and the cell its watchkeeper stands in. */
    private record Station(int consoleX, int consoleY, int standX, int standY) {}

    /**
     * The planned watch.
     *
     * @param stations every console and the cell it is kept from
     * @param ringed the rings the stations and their watchkeepers occupy, which
     *     is exactly the complement of the rings reserved as walkway
     * @param outer the outermost ring the arrangement claims; beyond it is
     *     bulkhead pocket
     */
    private record Watch(List<Station> stations, Set<Integer> ringed, int outer) {}
}
