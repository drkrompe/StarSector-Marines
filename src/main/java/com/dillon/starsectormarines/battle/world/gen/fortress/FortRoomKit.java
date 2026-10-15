package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.fit.ConsoleBankLayout.Cell;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the authored furnishing of a fort room in the room's own frame, with the plan written once for the door on the
 * south side and mirrored when the door is on the north. A mirrored plan reverses which hand a console bank's operator
 * sits on, so the mirror toggles it and an operator always faces into the room.
 */
final class FortRoomKit {

    /** Floor ids of the catalogue pieces the fort rooms are fitted from; every one is a single cell. */
    static final String CRATES = "doodad.industrial-crate-stack";
    static final String PALLETS = "doodad.industrial-pallet-stack";
    static final String GENERATOR = "doodad.industrial-generator";
    static final String TANK = "doodad.industrial-fluid-tank";
    /** The operator seat at a console station: round, so no pose of it reads wrong whichever side the bank's operator is on. */
    static final String STOOL = "fort-fittings.seat";

    private final int width;
    private final int height;
    private final boolean mirrored;
    private final boolean mirroredAcross;
    private final List<LayoutOp> ops = new ArrayList<>();

    /** The plan written for a south door, mirrored top to bottom when the door is on the north. */
    FortRoomKit(int height, boolean mirrored) {
        this(0, height, mirrored, false);
    }

    /**
     * @param width          the floor.s width, for the side-to-side mirror
     * @param height         the floor.s height, for the mirror
     * @param mirrored       whether the plan, written for a south door, is turned to a north door
     * @param mirroredAcross whether the plan is also reversed side to side; with {@code mirrored} that is a half turn of
     *                       the plan, which is how a room is drawn by art authored for the other door
     */
    FortRoomKit(int width, int height, boolean mirrored, boolean mirroredAcross) {
        this.width = width;
        this.height = height;
        this.mirrored = mirrored;
        this.mirroredAcross = mirroredAcross;
    }

    private int y(int y) {
        return mirrored ? height - 1 - y : y;
    }

    private int x(int x) {
        return mirroredAcross ? width - 1 - x : x;
    }

    /** Whether the plan is a reflection of the one written, which reverses which hand a bank.s operator sits on. */
    private boolean reflected() {
        return mirrored != mirroredAcross;
    }

    /** Circulation: a rectangle kept clear of furniture. */
    FortRoomKit lane(int x, int y, int spanX, int spanY) {
        ops.add(new LayoutOp.Lane(mirroredAcross ? width - x - spanX : x, mirrored ? height - y - spanY : y, spanX, spanY));
        return this;
    }

    /** A single-cell fixture. */
    FortRoomKit fixture(int x, int y, String doodadId) {
        ops.add(new LayoutOp.Fixture(x(x), y(y), doodadId, null));
        return this;
    }

    /** A straight run of single-cell fixtures from one cell to another along one axis. */
    FortRoomKit run(int x0, int y0, int x1, int y1, String doodadId) {
        int sx = Integer.signum(x1 - x0), sy = Integer.signum(y1 - y0);
        int x = x0, y = y0;
        while (true) {
            fixture(x, y, doodadId);
            if (x == x1 && y == y1) break;
            x += sx;
            y += sy;
        }
        return this;
    }

    /** A console bank along a straight stroke, its operators on the side that is {@code operatorRight} before mirroring. */
    FortRoomKit bank(boolean operatorRight, int x0, int y0, int x1, int y1) {
        List<Cell> cells = new ArrayList<>();
        int sx = Integer.signum(x1 - x0), sy = Integer.signum(y1 - y0);
        int x = x0, y = y0;
        while (true) {
            cells.add(new Cell(x(x), y(y)));
            if (x == x1 && y == y1) break;
            x += sx;
            y += sy;
        }
        ops.add(new LayoutOp.ConsoleBank(cells, false, reflected() != operatorRight));
        return this;
    }

    List<LayoutOp> ops() {
        return List.copyOf(ops);
    }

    /** A floor cell in this kit's frame, mirrored the way its fixtures are. */
    int[] cell(int x, int y) {
        return new int[]{x(x), y(y)};
    }
}
