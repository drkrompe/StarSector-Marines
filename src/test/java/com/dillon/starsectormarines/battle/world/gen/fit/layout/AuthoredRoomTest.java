package com.dillon.starsectormarines.battle.world.gen.fit.layout;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.FurnishableRoom;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFittings;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An authored room is furnished from its document, and turns with the ship.
 *
 * <p>Asked of one room and one document, because that is where the mechanism
 * lives. Whether a whole deck comes out right is a question about the deck.
 */
class AuthoredRoomTest {

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors)
            implements FurnishableRoom { }

    private static final RoomShape SHAPE = RoomShape.rectangle(6, 4);

    @AfterEach
    void putTheRegistryBack() {
        RoomLayouts.reset();
    }

    /**
     * The point of the whole artifact: a cell named in the document is the cell
     * the fixture stands on.
     */
    @Test
    void aDocumentPutsItsFixtureWhereItSaysTo() {
        List<Doodad> placed = furnish(layout(
                new LayoutOp.Fixture(1, 1, "doodad.chest-1", null)), RoomPose.CANONICAL);

        assertEquals(1, placed.size(), "the authored fixture was not placed at all");
        assertEquals(ORIGIN + 1, placed.get(0).cellX);
        assertEquals(ORIGIN + 1, placed.get(0).cellY);
    }

    /**
     * The same document, laid down every way the packer may turn the room, puts
     * its fixture on the corresponding cell each time.
     *
     * <p>This is the property the design rests on. A shipboard room's footprint
     * is fixed and only its pose varies, so authoring once in a canonical frame
     * is enough — provided the mapping actually holds. A layout that mirrored
     * its footprint while leaving its contents where they were would look right
     * on one deck and be reversed on the next.
     */
    @Test
    void oneDocumentServesEveryPose() {
        RoomLayout document = layout(new LayoutOp.Fixture(1, 1, "doodad.chest-1", null));

        for (RoomPose pose : RoomPose.all()) {
            List<Doodad> placed = furnish(document, pose);
            assertEquals(1, placed.size(), "nothing was placed at pose " + pose);

            int[] expected = pose.map(1, 1, SHAPE.width(), SHAPE.height());
            assertEquals(ORIGIN + expected[0], placed.get(0).cellX, "x at pose " + pose);
            assertEquals(ORIGIN + expected[1], placed.get(0).cellY, "y at pose " + pose);
        }
    }

    /**
     * Capacity is the fixture count, read off the document.
     *
     * <p>Law 4, and the reason it is worth a test: paving and lanes are steps in
     * the same list, and counting the list would give a berth capacity that grew
     * every time somebody laid another square of deck plating.
     */
    @Test
    void capacityCountsFixturesAndNothingElse() {
        RoomLayout document = layout(
                new LayoutOp.Lane(0, 2, 6, 1),
                new LayoutOp.Paving(0, 0, "doodad.fl-grate-1"),
                new LayoutOp.Fixture(1, 1, "doodad.chest-1", Affordance.STOW),
                new LayoutOp.Fixture(3, 1, "doodad.chest-2", null));

        assertEquals(2, document.provides(),
                "capacity counted something that is not a fixture");
    }

    /**
     * An authored fixture that affords work publishes it, exactly as a
     * programmed one does — the document goes through the same floor, so it gets
     * the same standing-cell search rather than a second, laxer path.
     */
    @Test
    void authoredWorkIsPublishedLikeProgrammedWork() {
        Furnished furnished = furnishAll(layout(
                new LayoutOp.Lane(0, 2, 6, 1),
                new LayoutOp.Fixture(1, 1, "doodad.chest-1", Affordance.STOW)),
                RoomPose.CANONICAL);

        assertEquals(1, furnished.tasks(), "the authored fixture is nobody's work");
    }

    /**
     * A painted floor changes what the deck is drawn from and nothing else.
     *
     * <p>The whole reason flooring is separate from {@code Ground}: a vent run
     * and a striped run are the same floor to pathing, cover and sight, which is
     * exactly what makes them safe to use for flavour. If this ever starts
     * moving a ground kind, a room's decoration has become a tactical fact.
     */
    @Test
    void aPaintedFloorChangesThePictureAndNotTheDeck() {
        Furnished furnished = furnishAll(layout(
                new LayoutOp.Flooring(1, 1, 2, 2, "road.striped")), RoomPose.CANONICAL);

        CellTopology topology = furnished.topology();
        assertEquals("road.striped", topology.getSurfaceId(ORIGIN + 1, ORIGIN + 1),
                "the cell was not told what to draw from");
        assertEquals("road.striped", topology.getSurfaceId(ORIGIN + 2, ORIGIN + 2));
        assertNull(topology.getSurfaceId(ORIGIN + 4, ORIGIN + 1),
                "the paint ran past the cells it was given");

        assertEquals(topology.getGroundKind(ORIGIN + 4, ORIGIN + 1),
                topology.getGroundKind(ORIGIN + 1, ORIGIN + 1),
                "painting the floor moved the ground kind, which consumers read");
    }

    /**
     * A bulkhead is one wall, so a second choice replaces rather than adds.
     */
    @Test
    void aRoomsBulkheadIsStampedRoundItsWholeRing() {
        Furnished furnished = furnishAll(layout(
                new LayoutOp.Bulkhead("road.embankment")), RoomPose.CANONICAL);

        CellTopology topology = furnished.topology();
        assertEquals("road.embankment", topology.getSurfaceId(ORIGIN - 1, ORIGIN),
                "the ring's west side was not stamped");
        assertEquals("road.embankment",
                topology.getSurfaceId(ORIGIN + SHAPE.width(), ORIGIN),
                "the ring's east side was not stamped");
        assertNull(topology.getSurfaceId(ORIGIN + 1, ORIGIN + 1),
                "the bulkhead was stamped onto the room's own floor");
    }

    /**
     * All three parts of the key have to agree before a layout is used.
     *
     * <p>The footprint is the part that matters most: {@code RoomFittings} is
     * shared with fortress interiors, whose rooms carry the same purposes at
     * different sizes. Keying on purpose alone would put a ship's armoury into a
     * bunker.
     */
    @Test
    void aLayoutOnlyAnswersForTheRoomItWasAuthoredFor() {
        RoomLayout document = layout(new LayoutOp.Fixture(1, 1, "doodad.chest-1", null));
        RoomLayouts.install(new RoomLayouts(List.of(document)));

        assertInstanceOf(AuthoredFitting.class,
                RoomFittings.forRoom(RoomPurpose.STOCKROOM, SHAPE, RoomFit.STANDARD),
                "the layout did not answer for the room it was authored for");

        assertProcedural(RoomFittings.forRoom(
                        RoomPurpose.STOCKROOM, RoomShape.rectangle(4, 6), RoomFit.STANDARD),
                RoomPurpose.STOCKROOM, "a different footprint took the layout");
        assertProcedural(RoomFittings.forRoom(
                        RoomPurpose.STOCKROOM, SHAPE, RoomFit.OPTIMISED),
                RoomPurpose.STOCKROOM, "an unauthored refit level took the layout");
        assertProcedural(RoomFittings.forRoom(RoomPurpose.ARMORY, SHAPE, RoomFit.STANDARD),
                RoomPurpose.ARMORY, "another purpose took the layout");
    }

    /**
     * With nothing installed every room keeps the fitting it has always had.
     *
     * <p>The state the feature ships in, so it is worth pinning: this whole
     * package is inert until somebody authors something, and a deck generated
     * today must be the deck generated yesterday.
     */
    @Test
    void nothingAuthoredMeansNothingChanged() {
        assertTrue(RoomLayouts.installed().isEmpty());

        assertSame(RoomFittings.forPurpose(RoomPurpose.STOCKROOM),
                RoomFittings.forRoom(RoomPurpose.STOCKROOM, SHAPE, RoomFit.STANDARD),
                "an empty layout set still changed which fitting a room gets");
    }

    private static void assertProcedural(RoomFitting fitting, RoomPurpose purpose, String why) {
        assertNotNull(fitting, why);
        assertSame(RoomFittings.forPurpose(purpose), fitting, why);
    }

    private static RoomLayout layout(LayoutOp... ops) {
        return new RoomLayout(RoomPurpose.STOCKROOM, RoomFit.STANDARD, SHAPE,
                List.of(ops), List.of(), false);
    }

    private static final int ORIGIN = 4;

    private record Furnished(List<Doodad> doodads, int tasks, CellTopology topology) { }

    private static List<Doodad> furnish(RoomLayout document, RoomPose pose) {
        return furnishAll(document, pose).doodads();
    }

    private static Furnished furnishAll(RoomLayout document, RoomPose pose) {
        RoomShape posed = document.shape().posed(pose);
        int mapWidth = posed.width() + ORIGIN * 2;
        int mapHeight = posed.height() + ORIGIN * 2;
        NavigationGrid grid = new NavigationGrid(mapWidth, mapHeight);
        CellTopology topology = new CellTopology(mapWidth, mapHeight);
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) grid.setWalkableFloor(x, y);
        }
        GenContext ctx = new GenContext(grid, topology, new Random(3L),
                mapWidth, mapHeight, 3L);
        Room room = new Room(posed, ORIGIN, ORIGIN, pose, document.purpose(),
                List.of(new Doorway(ORIGIN, ORIGIN)));

        new AuthoredFitting(document).fit(new RoomFloor(ctx, room, document.fit()));
        return new Furnished(new ArrayList<>(ctx.doodads), ctx.fixtureTasks.size(), topology);
    }
}
