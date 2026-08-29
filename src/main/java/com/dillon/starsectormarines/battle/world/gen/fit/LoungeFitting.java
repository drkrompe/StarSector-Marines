package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;

/**
 * Seating gathered into conversation groups on a grid of islands, with
 * circulation between them.
 *
 * <p>The lounge is one of the two compartments aboard whose <em>content</em> is
 * that there is nothing to do. That is not the same thing as a room with no
 * fitting: idleness a crew chooses has to have somewhere to be spent, or the
 * only shape it can take is standing in a passage — which is precisely the
 * defect the whole ambient model exists to remove. So this room is furnished
 * with as much care as a machinery space, and what it publishes is
 * {@link Affordance#UNWIND} rather than work.
 *
 * <p>Not {@linkplain AisleFitting ranked}, and the difference is the point. Two
 * rows of identical sofas down opposite bulkheads is a waiting room: it seats
 * the same number of people and tells them all to face the same way. A lounge is
 * <b>islands</b> — a settle round a low table here, four seats at a card table
 * there, a sofa in a corner with something green beside it — separated by floor
 * people walk through rather than sit in. So the room is divided into blocks
 * with a lane between them, and each block takes one of a handful of authored
 * arrangements in rotation, so that no two neighbouring groups are the same
 * shape.
 *
 * <p>Capacity follows from the footprint rather than from a number kept here: a
 * larger compartment fits more blocks, and each block seats what its arrangement
 * seats. A room too small for a single block is left bare, which is honest — a
 * four-by-four compartment is a cabin, not a lounge.
 *
 * <p>Every piece whose art is drawn facing one particular way is chosen from
 * where the room <em>ended up</em>, not from where it was authored. A sofa is
 * laid down here as "backed onto the block's forward edge"; which of the four
 * sofa variants that turns out to be depends on the {@link RoomPose}, exactly as
 * a firing range picks its barrier by which way the rounds go. Authoring the id
 * directly would give half the lounges on a ship sofas with their backs to the
 * middle of the room.
 */
public final class LoungeFitting implements RoomFitting {

    /** Cells on a side of one conversation group's island. */
    private static final int BLOCK = 4;
    /** Cells of walking floor between one island and the next, and round them all. */
    private static final int LANE = 1;

    /** Sofa art, by which way the seat faces in the world. */
    private static final String SOFA_FACING_SOUTH = "doodad.residential-sofa-h";
    private static final String SOFA_FACING_NORTH = "doodad.residential-sofa-back-s";
    private static final String SOFA_FACING_EAST = "doodad.residential-sofa-v";
    private static final String SOFA_FACING_WEST = "doodad.residential-sofa-back-e";

    private static final String LOW_TABLE = "doodad.office-conference-table";
    private static final String SHELF = "doodad.shelf-3";
    private static final String COUNTER = "doodad.office-reception-counter";
    private static final String PLANTER_EAST_WEST = "doodad.residential-planter-h";
    private static final String PLANTER_NORTH_SOUTH = "doodad.residential-planter-v";

    /**
     * The seats. Two colours rather than one, because a lounge that furnished
     * itself out of a single chair is back to being a waiting room.
     */
    private static final String[] STOOLS = {
            "doodad.chair-south-green",
            "doodad.chair-south-yellow" };

    /** What a piece of a group is, before the pose says which art that means. */
    private enum Prop { SOFA, TABLE, PLANTER, STOOL, SHELVING, COUNTER }

    /**
     * One item of a group, offset from the island's corner in the canonical
     * frame.
     *
     * <p>{@code facingAlong}/{@code facingAcross} is the direction a sofa's
     * seat looks, and is ignored by everything else. It doubles as the sofa's
     * extent: a sofa faces across the two cells it occupies, so a seat looking
     * down the canonical long axis is one cell wide and two deep, and one
     * looking across it is the other way about.
     */
    private record Piece(Prop prop, int along, int across,
                         int facingAlong, int facingAcross) {

        static Piece of(Prop prop, int along, int across) {
            return new Piece(prop, along, across, 0, 0);
        }

        static Piece sofa(int along, int across, int facingAlong, int facingAcross) {
            return new Piece(Prop.SOFA, along, across, facingAlong, facingAcross);
        }
    }

    /**
     * The authored arrangements, in the order they are dealt out.
     *
     * <p>Within a group the furniture that is only scenery goes down first and
     * the seating last. Placing a seat reserves a cell beside it for whoever
     * sits there, so a sofa laid before its own table can quietly take the cell
     * the table wanted and leave the group a chair short of what was drawn.
     */
    private static final List<List<Piece>> GROUPS = List.of(
            // A settle: two sofas facing each other over a low table, with
            // something green at the open end.
            List.of(Piece.of(Prop.TABLE, 0, 1),
                    Piece.of(Prop.PLANTER, 3, 1),
                    Piece.sofa(0, 0, 0, 1),
                    Piece.sofa(0, 2, 0, -1)),

            // A corner: an L of two sofas with the table in the angle. The same
            // furniture as the settle and a different room to sit in.
            List.of(Piece.of(Prop.TABLE, 1, 1),
                    Piece.of(Prop.PLANTER, 3, 0),
                    Piece.sofa(0, 0, 0, 1),
                    Piece.sofa(0, 1, 1, 0)),

            // A card table: four seats round one table, everybody facing in.
            // The one group where people are doing something together.
            List.of(Piece.of(Prop.TABLE, 1, 1),
                    Piece.of(Prop.STOOL, 1, 0),
                    Piece.of(Prop.STOOL, 0, 1),
                    Piece.of(Prop.STOOL, 2, 1),
                    Piece.of(Prop.STOOL, 1, 2)),

            // A nook: one sofa, a shelf of whatever the ship has to read, and
            // greenery either end. Somewhere to be on your own, which a room
            // full of conversation groups otherwise has nowhere for.
            List.of(Piece.of(Prop.SHELVING, 2, 2),
                    Piece.of(Prop.PLANTER, 0, 0),
                    Piece.of(Prop.PLANTER, 0, 3),
                    Piece.sofa(0, 1, 1, 0)));

    /**
     * The counter, and the stools along it.
     *
     * <p>Kept out of the rotation because a lounge has one bar and not four.
     * It takes the first island, and where it cannot be laid the island takes an
     * ordinary group instead.
     */
    private static final List<Piece> BAR = List.of(
            Piece.of(Prop.COUNTER, 0, 0),
            Piece.of(Prop.PLANTER, 3, 0),
            Piece.of(Prop.STOOL, 0, 2),
            Piece.of(Prop.STOOL, 1, 2),
            Piece.of(Prop.STOOL, 2, 2));

    @Override
    public RoomPurpose purpose() {
        return RoomPurpose.CREW_LOUNGE;
    }

    @Override
    public void fit(RoomFloor floor) {
        int along = floor.canonicalWidth();
        int across = floor.canonicalHeight();
        int pitch = BLOCK + LANE;

        // Islands are laid inside a walking margin, so every bulkhead — and
        // therefore every hatch, wherever the deck had to cut one — opens onto
        // floor rather than onto the back of a sofa.
        int usableAlong = along - 2 * LANE;
        int usableAcross = across - 2 * LANE;
        int columns = (usableAlong + LANE) / pitch;
        int rows = (usableAcross + LANE) / pitch;
        if (columns <= 0 || rows <= 0) return;

        int firstAlong = LANE + (usableAlong - (columns * pitch - LANE)) / 2;
        int firstAcross = LANE + (usableAcross - (rows * pitch - LANE)) / 2;

        reserveCirculation(floor, along, across, pitch,
                columns, rows, firstAlong, firstAcross);
        for (Doorway door : floor.localDoors()) {
            floor.reserveLane(door.x() - 1, door.y() - 1, 3, 3);
        }

        boolean bar = floor.pose().upright();
        int dealt = 0;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int islandAlong = firstAlong + column * pitch;
                int islandAcross = firstAcross + row * pitch;
                if (bar && row == 0 && column == 0) {
                    lay(floor, BAR, islandAlong, islandAcross);
                    continue;
                }
                lay(floor, GROUPS.get(dealt++ % GROUPS.size()), islandAlong, islandAcross);
            }
        }
    }

    /**
     * Everything that is not an island, reserved before anything is placed.
     *
     * <p>Taken as the whole of the room minus the blocks rather than as strips
     * of the exact width between them, so a footprint that does not divide
     * evenly leaves its slack as more walking floor instead of as a band of deck
     * nothing has argued for.
     */
    private void reserveCirculation(RoomFloor floor, int along, int across, int pitch,
                                    int columns, int rows,
                                    int firstAlong, int firstAcross) {
        int pastAlong = firstAlong + columns * pitch - LANE;
        int pastAcross = firstAcross + rows * pitch - LANE;
        reserve(floor, 0, 0, firstAlong, across);
        reserve(floor, pastAlong, 0, along - pastAlong, across);
        reserve(floor, 0, 0, along, firstAcross);
        reserve(floor, 0, pastAcross, along, across - pastAcross);
        for (int column = 1; column < columns; column++) {
            reserve(floor, firstAlong + column * pitch - LANE, 0, LANE, across);
        }
        for (int row = 1; row < rows; row++) {
            reserve(floor, 0, firstAcross + row * pitch - LANE, along, LANE);
        }
    }

    /** One arrangement, laid at an island's canonical corner. */
    private void lay(RoomFloor floor, List<Piece> group, int along, int across) {
        int stool = 0;
        for (Piece piece : group) {
            int pieceAlong = along + piece.along();
            int pieceAcross = across + piece.across();
            switch (piece.prop()) {
                case SOFA -> place(floor, sofa(floor, piece), pieceAlong, pieceAcross,
                        Math.abs(piece.facingAcross()) + 1, Math.abs(piece.facingAlong()) + 1,
                        Affordance.UNWIND);
                case STOOL -> place(floor, STOOLS[stool++ % STOOLS.length],
                        pieceAlong, pieceAcross, 1, 1, Affordance.UNWIND);
                case TABLE -> place(floor, LOW_TABLE, pieceAlong, pieceAcross, 1, 1, null);
                case SHELVING -> place(floor, SHELF, pieceAlong, pieceAcross, 1, 1, null);
                case PLANTER -> place(floor, planter(floor), pieceAlong, pieceAcross, 1, 1, null);
                case COUNTER -> place(floor, COUNTER, pieceAlong, pieceAcross, 3, 2, null);
            }
        }
    }

    /**
     * One piece, at the corner its canonical footprint maps onto.
     *
     * <p>A quarter turn moves which corner of a multi-cell prop is its origin,
     * so the whole rectangle is carried across and the near corner taken.
     * Mapping only the authored corner puts every sofa in a turned lounge one
     * cell off the group it belongs to.
     */
    private static boolean place(RoomFloor floor, String id, int along, int across,
                                 int spanAlong, int spanAcross, Affordance affordance) {
        int[] rect = floor.toLocalRect(along, across, spanAlong, spanAcross);
        return affordance == null
                ? floor.place(id, rect[0], rect[1])
                : floor.place(id, rect[0], rect[1], affordance);
    }

    /** The sofa whose seat looks the way this one was authored to look. */
    private static String sofa(RoomFloor floor, Piece piece) {
        int[] facing = floor.pose().mapDirection(piece.facingAlong(), piece.facingAcross());
        if (facing[1] > 0) return SOFA_FACING_SOUTH;
        if (facing[1] < 0) return SOFA_FACING_NORTH;
        return facing[0] > 0 ? SOFA_FACING_EAST : SOFA_FACING_WEST;
    }

    /**
     * The planter drawn along the room's long axis, wherever that axis ended up.
     *
     * <p>A planter is one cell either way, so this changes nothing about the
     * fill and everything about whether the shrubs line up with the run of the
     * compartment or sit across it.
     */
    private static String planter(RoomFloor floor) {
        return floor.pose().mapDirection(1, 0)[0] != 0
                ? PLANTER_EAST_WEST : PLANTER_NORTH_SOUTH;
    }

    private void reserve(RoomFloor floor,
                         int along, int across, int alongSpan, int acrossSpan) {
        if (alongSpan <= 0 || acrossSpan <= 0) return;
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        floor.reserveLane(rect[0], rect[1], rect[2], rect[3]);
    }
}
