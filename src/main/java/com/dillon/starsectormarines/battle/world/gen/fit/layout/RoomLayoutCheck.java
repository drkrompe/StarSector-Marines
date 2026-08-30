package com.dillon.starsectormarines.battle.world.gen.fit.layout;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.FurnishableRoom;
import com.dillon.starsectormarines.battle.world.gen.fit.Hookup;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.GridLayout;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What a layout would actually do if the ship were generated with it.
 *
 * <p>Exists because <b>the two ways an authored room fails are both silent</b>.
 * A fixture whose cell is taken is refused and the room simply comes out
 * sparser; and an arrangement that severs its own circulation has its entire
 * fill thrown away, so the room ships as bare deck. Neither says anything, and
 * both look from the outside exactly like a fill that was never written.
 *
 * <p>That is not hypothetical. A checkerboard of crates across an armoury —
 * fifteen fixtures, every one of them legal on its own — produced a compartment
 * with nothing in it at all, because a line of fixtures across a room is a wall
 * unless it is told not to be. The picture showed an empty room and the count
 * said fifteen, and only one of those was true.
 *
 * <p>So the editor replays the document before it is trusted and reports the
 * difference between what was asked for and what happened.
 */
public final class RoomLayoutCheck {

    private RoomLayoutCheck() {}

    /**
     * @param declared fixtures the document asks for
     * @param placed fixtures that actually went down
     * @param work task points published
     * @param workDropped task points withdrawn as unreachable
     * @param circulationSurvives whether the room can still be walked through —
     *     when false the ship discards this fill entire and the room is bare
     * @param unknownIds ids the catalog does not have, which place nothing
     */
    public record Report(int declared, int placed, int work, int workDropped,
                         boolean circulationSurvives, List<String> unknownIds,
                         String badBulkhead) {

        /** Whether this layout does what it says. */
        public boolean clean() {
            return circulationSurvives && placed == declared
                    && unknownIds.isEmpty() && badBulkhead == null;
        }

        /** What is wrong, in the order it matters, or an empty list. */
        public List<String> complaints() {
            List<String> said = new ArrayList<>();
            if (!circulationSurvives) {
                said.add("This arrangement severs the room's own circulation, so the ship "
                        + "would throw the whole fill away and the room would generate as "
                        + "bare deck. A line of fixtures across a room is a wall.");
            }
            if (badBulkhead != null) {
                said.add(badBulkhead);
            }
            if (!unknownIds.isEmpty()) {
                said.add("The catalog has no " + String.join(", ", unknownIds)
                        + " — those place nothing and say nothing.");
            }
            if (placed < declared) {
                said.add((declared - placed) + " of " + declared + " fixtures had nowhere to "
                        + "go and were refused; the room provides " + placed + ".");
            }
            if (workDropped > 0) {
                said.add(workDropped + " work points were walled in by their own neighbours "
                        + "and withdrawn.");
            }
            return said;
        }
    }

    /**
     * Replay this layout on an empty deck and report what happened.
     *
     * <p>On its own rather than in a hull, because this answers what the
     * document does — not whether one particular ship had room for it. Where the
     * room ends up is the comparison's question.
     */
    public static Report replay(RoomLayout layout) {
        int margin = 4;
        RoomShape shape = layout.shape();
        int width = shape.width() + margin * 2;
        int height = shape.height() + margin * 2;
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        GenContext ctx = new GenContext(grid, topology, new Random(0L), width, height, 0L);

        RoomFloor floor = new RoomFloor(ctx,
                new CheckedRoom(shape, margin, margin, RoomPose.CANONICAL,
                        layout.purpose(), doors(layout, margin)), layout.fit());
        new AuthoredFitting(layout).fit(floor);
        int dropped = floor.dropUnreachableWork();

        List<String> unknown = new ArrayList<>();
        int declared = 0;
        for (LayoutOp op : layout.ops()) {
            if (!(op instanceof LayoutOp.Fixture fixture)) continue;
            declared++;
            if (TileRegistry.installed().doodad(fixture.doodadId()) == null
                    && !unknown.contains(fixture.doodadId())) {
                unknown.add(fixture.doodadId());
            }
        }
        return new Report(declared, floor.placedFixtures(), ctx.fixtureTasks.size(),
                dropped, floor.circulationSurvives(), unknown, bulkheadComplaint(layout));
    }

    /**
     * Why this room's bulkhead will not draw, or null.
     *
     * <p>Two ways it can be wrong and both end in an ordinary-looking wall: an
     * id the catalog does not have, and an id that names something which is not
     * a wall. A variant pool cannot resolve a corner, so a room pointed at one
     * would fall back to the deck's own wall on every cell and look exactly like
     * a room that never asked.
     */
    private static String bulkheadComplaint(RoomLayout layout) {
        String id = layout.bulkhead();
        if (id == null) return null;
        GridBlockDef block = TileRegistry.installed().block(id);
        if (block == null) {
            return "The catalog has no block '" + id + "', so this room would draw the "
                    + "deck's own bulkhead instead.";
        }
        if (block.layout != GridLayout.WALL_3X3) {
            return "'" + id + "' is not a wall — it cannot resolve a corner, so this room "
                    + "would draw the deck's own bulkhead instead.";
        }
        return null;
    }

    private static List<Doorway> doors(RoomLayout layout, int margin) {
        List<Doorway> doors = new ArrayList<>();
        for (Hookup hookup : layout.hookups()) {
            for (Hookup.DoorSlot slot : hookup.slots()) {
                int[] cell = slot.cells().get(0);
                doors.add(new Doorway(margin + cell[0], margin + cell[1]));
            }
            break;
        }
        if (doors.isEmpty()) {
            doors.add(new Doorway(margin, margin + layout.shape().height() / 2));
        }
        return doors;
    }

    private record CheckedRoom(RoomShape shape, int originX, int originY, RoomPose pose,
                               RoomPurpose purpose, List<Doorway> doors)
            implements FurnishableRoom { }
}
