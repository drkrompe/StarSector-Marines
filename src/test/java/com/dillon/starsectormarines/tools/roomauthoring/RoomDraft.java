package com.dillon.starsectormarines.tools.roomauthoring;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.fit.Hookup;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayout;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;

/**
 * One room being edited: its footprint, and the steps that furnish it.
 *
 * <p>The mutable counterpart of {@link RoomLayout}, which is immutable because
 * generation reads it. Everything here is a whole-document edit — toggle a cell,
 * drop a fixture, repaint a run of deck — and {@link #layout()} hands back the
 * settled form for replay, comparison, or saving.
 *
 * <p>Edits are kept as an ordered list because <b>order is meaning</b> in a
 * layout: a lane declared after the fixture standing on it is not a lane, and
 * paving laid after a fixture covers it. Nothing here re-sorts the list, and the
 * one place order is imposed is {@link #addFixture}, which appends — a fixture
 * added later stands on top of paving added earlier, which is what an author
 * doing it in that order means.
 */
public final class RoomDraft {

    private final RoomPurpose purpose;
    private final RoomFit fit;
    private final List<Hookup> hookups;
    private final boolean handed;
    private final List<LayoutOp> ops = new ArrayList<>();
    private boolean[][] floor;

    /** Whatever this room generates as today, as the starting point. */
    public RoomDraft(RoomLayout seed) {
        this.purpose = seed.purpose();
        this.fit = seed.fit();
        this.hookups = List.copyOf(seed.hookups());
        this.handed = seed.handed();
        this.ops.addAll(seed.ops());
        this.floor = mask(seed.shape());
    }

    private static boolean[][] mask(RoomShape shape) {
        boolean[][] cells = new boolean[shape.width()][shape.height()];
        for (int x = 0; x < shape.width(); x++) {
            for (int y = 0; y < shape.height(); y++) cells[x][y] = shape.contains(x, y);
        }
        return cells;
    }

    public RoomPurpose purpose() {
        return purpose;
    }

    public RoomFit fit() {
        return fit;
    }

    public int width() {
        return floor.length;
    }

    public int height() {
        return floor[0].length;
    }

    public boolean isFloor(int x, int y) {
        return x >= 0 && y >= 0 && x < width() && y < height() && floor[x][y];
    }

    public List<LayoutOp> ops() {
        return List.copyOf(ops);
    }

    /** How many fixtures stand here, which is what the room provides. */
    public int provides() {
        int count = 0;
        for (LayoutOp op : ops) {
            if (op instanceof LayoutOp.Fixture) count++;
        }
        return count;
    }

    // ---- the footprint -------------------------------------------------------

    /**
     * Add or remove one cell of deck.
     *
     * <p>Removing a cell drops whatever stood on it, because the alternative is
     * a document carrying furniture outside its own room — which replays as a
     * silently refused placement and reads afterwards as a fill that came out
     * sparse for no reason.
     */
    public void toggleCell(int x, int y) {
        if (x < 0 || y < 0 || x >= width() || y >= height()) return;
        floor[x][y] = !floor[x][y];
        if (!floor[x][y]) removeAt(x, y);
    }

    /**
     * Grow the footprint's bounding box. The added band is deck, since a room
     * enlarged to nothing would need every new cell clicked in one at a time.
     */
    public void resize(int width, int height) {
        if (width < 1 || height < 1) return;
        boolean[][] grown = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                grown[x][y] = x < width() && y < height() ? floor[x][y] : true;
            }
        }
        floor = grown;
        // Anything now outside the room goes with it, for the reason above.
        ops.removeIf(op -> !within(op, width, height));
    }

    private boolean within(LayoutOp op, int width, int height) {
        int[] cell = cellOf(op);
        return cell == null || (cell[0] < width && cell[1] < height);
    }

    // ---- what stands on it ---------------------------------------------------

    /** Repaint a run of deck. Real topology, so consumers see it and not only the eye. */
    public void paintGround(int x, int y, int spanX, int spanY, GroundKind kind) {
        ops.add(new LayoutOp.Ground(x, y, Math.max(1, spanX), Math.max(1, spanY), kind));
    }

    /** Reserve a run as circulation, which nothing may then be placed on. */
    public void reserveLane(int x, int y, int spanX, int spanY) {
        ops.add(new LayoutOp.Lane(x, y, Math.max(1, spanX), Math.max(1, spanY)));
    }

    /**
     * Give one cell back to open deck.
     *
     * <p>Rubbing out part of a walkway means splitting the run it belongs to,
     * since a lane is stored as a rectangle and a rectangle with a hole is not
     * one. The run is replaced by the pieces of itself that survive, which is
     * why this is more than a remove.
     */
    public void clearLaneAt(int x, int y) {
        List<LayoutOp> replaced = new ArrayList<>();
        for (LayoutOp op : ops) {
            if (!(op instanceof LayoutOp.Lane lane)
                    || x < lane.x() || x >= lane.x() + lane.spanX()
                    || y < lane.y() || y >= lane.y() + lane.spanY()) {
                replaced.add(op);
                continue;
            }
            // The four bands around the removed cell, any of which may be empty.
            addLane(replaced, lane.x(), lane.y(), lane.spanX(), y - lane.y());
            addLane(replaced, lane.x(), y + 1, lane.spanX(),
                    lane.y() + lane.spanY() - (y + 1));
            addLane(replaced, lane.x(), y, x - lane.x(), 1);
            addLane(replaced, x + 1, y, lane.x() + lane.spanX() - (x + 1), 1);
        }
        ops.clear();
        ops.addAll(replaced);
    }

    private static void addLane(List<LayoutOp> into, int x, int y, int spanX, int spanY) {
        if (spanX > 0 && spanY > 0) into.add(new LayoutOp.Lane(x, y, spanX, spanY));
    }

    /**
     * Give back every cell the room had reserved as circulation.
     *
     * <p>Deliberately a command of its own rather than something a click can do.
     * A seeded room is mostly lane — an armoury comes back with six of its eight
     * rows reserved — so an author who cannot un-reserve can barely place
     * anything; but a stray click that deleted a room's circulation would be a
     * very expensive misclick, since a fill that seals its room is discarded
     * entire. Explicit both ways.
     */
    public void clearLanes() {
        ops.removeIf(op -> op instanceof LayoutOp.Lane);
    }

    /**
     * Draw this room's bulkhead from a named block, or from the deck's own when
     * handed null.
     *
     * <p>Replaces rather than appends. A room has one wall, so a second choice
     * is a correction and not a second wall — and leaving both in the script
     * would make the document's meaning depend on which one was last, for no
     * reason an author would ever intend.
     */
    public void bulkhead(String blockId) {
        ops.removeIf(op -> op instanceof LayoutOp.Bulkhead);
        if (blockId != null && !blockId.isEmpty()) ops.add(new LayoutOp.Bulkhead(blockId));
    }

    /** The block this room's bulkhead draws from, or null for the deck's own. */
    public String bulkhead() {
        String named = null;
        for (LayoutOp op : ops) {
            if (op instanceof LayoutOp.Bulkhead bulkhead) named = bulkhead.blockId();
        }
        return named;
    }

    /**
     * Draw a run of this room's deck from a named block — vent plate, hazard
     * striping, whatever the room wants underfoot.
     *
     * <p>Appends rather than replacing, unlike the bulkhead: a room has one wall
     * all the way round but several kinds of floor is the entire point. A later
     * run over the same cells wins, which is what painting means.
     */
    public void floor(int x, int y, int spanX, int spanY, String blockId) {
        if (blockId == null || blockId.isEmpty()) return;
        ops.add(new LayoutOp.Flooring(x, y, Math.max(1, spanX), Math.max(1, spanY), blockId));
    }

    /** The block painted over a cell, or null where the deck's own kind decides. */
    public String floorAt(int x, int y) {
        String painted = null;
        for (LayoutOp op : ops) {
            if (!(op instanceof LayoutOp.Flooring flooring)) continue;
            if (x >= flooring.x() && x < flooring.x() + flooring.spanX()
                    && y >= flooring.y() && y < flooring.y() + flooring.spanY()) {
                painted = flooring.blockId();
            }
        }
        return painted;
    }

    /** Stand one fixture here, optionally with the work somebody does at it. */
    public void addFixture(int x, int y, String doodadId, Affordance affordance) {
        if (!isFloor(x, y)) return;
        ops.add(new LayoutOp.Fixture(x, y, doodadId, affordance));
    }

    /** Lay floor covering here, which does not claim the cell. */
    public void pave(int x, int y, String doodadId) {
        if (!isFloor(x, y)) return;
        ops.add(new LayoutOp.Paving(x, y, doodadId));
    }

    /**
     * Take away whatever the author last put on this cell.
     *
     * <p>Last rather than all, because a cell legitimately carries several
     * things — paving under a fixture — and because undoing one click at a time
     * is what a person clicking expects.
     */
    public boolean removeAt(int x, int y) {
        for (int i = ops.size() - 1; i >= 0; i--) {
            int[] cell = cellOf(ops.get(i));
            if (cell != null && cell[0] == x && cell[1] == y) {
                ops.remove(i);
                return true;
            }
        }
        return false;
    }

    /** What stands on a cell, outermost last, for the view and for a tooltip. */
    public List<LayoutOp> at(int x, int y) {
        List<LayoutOp> here = new ArrayList<>();
        for (LayoutOp op : ops) {
            int[] cell = cellOf(op);
            if (cell != null && cell[0] == x && cell[1] == y) here.add(op);
        }
        return here;
    }

    /**
     * The cell a step is anchored on, or null for one that covers a run.
     *
     * <p>Rectangles deliberately answer null. A lane is not "on" a cell in the
     * sense a click means, and letting a click delete the room's circulation
     * because it happened to cross the cell would be a very expensive misclick.
     */
    private static int[] cellOf(LayoutOp op) {
        if (op instanceof LayoutOp.Fixture fixture) return new int[]{ fixture.x(), fixture.y() };
        if (op instanceof LayoutOp.Paving paving) return new int[]{ paving.x(), paving.y() };
        if (op instanceof LayoutOp.Task task) return new int[]{ task.x(), task.y() };
        return null;
    }

    /** The ground kind painted over a cell, or null where the deck is untouched. */
    public GroundKind groundAt(int x, int y) {
        GroundKind kind = null;
        for (LayoutOp op : ops) {
            if (!(op instanceof LayoutOp.Ground ground)) continue;
            if (x >= ground.x() && x < ground.x() + ground.spanX()
                    && y >= ground.y() && y < ground.y() + ground.spanY()) {
                kind = ground.kind();
            }
        }
        return kind;
    }

    /** Whether a cell is reserved as circulation. */
    public boolean isLane(int x, int y) {
        for (LayoutOp op : ops) {
            if (!(op instanceof LayoutOp.Lane lane)) continue;
            if (x >= lane.x() && x < lane.x() + lane.spanX()
                    && y >= lane.y() && y < lane.y() + lane.spanY()) {
                return true;
            }
        }
        return false;
    }

    // ---- the settled form ----------------------------------------------------

    /** This draft as the immutable document generation reads. */
    public RoomLayout layout() {
        String[] rows = new String[height()];
        for (int y = 0; y < height(); y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < width(); x++) row.append(floor[x][y] ? '#' : '.');
            rows[y] = row.toString();
        }
        return new RoomLayout(purpose, fit, RoomShape.of(rows), ops, hookups, handed);
    }
}
