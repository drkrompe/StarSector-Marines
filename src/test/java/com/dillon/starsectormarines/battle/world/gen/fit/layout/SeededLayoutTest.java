package com.dillon.starsectormarines.battle.world.gen.fit.layout;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.FurnishableRoom;
import com.dillon.starsectormarines.battle.world.gen.fit.Hookup;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFittings;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A seeded document reproduces the room it was copied from.
 *
 * <p>This is the property the whole editor rests on. An author opens a room,
 * sees what already ships, and changes one thing — so a seed that furnished the
 * room even slightly differently would present every unedited room as already
 * modified, and there would be no way to tell an edit from a transcription
 * error.
 */
class SeededLayoutTest {

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors)
            implements FurnishableRoom { }

    /**
     * Every furnished purpose, at a footprint that suits it, seeds to a document
     * that lays the room out the same way.
     *
     * <p>Swept across purposes rather than asked of one, because what is being
     * checked is that the recorder covers the whole of {@code RoomFloor}'s
     * vocabulary. One room proves the mechanism for the calls that room happens
     * to make; a fitting that paves, closes off deck or berths a machine would
     * go unrecorded until somebody opened it in the editor.
     */
    @Test
    void aSeededDocumentFurnishesTheRoomItWasCopiedFrom() {
        int checked = 0;
        for (RoomPurpose purpose : RoomPurpose.values()) {
            RoomFitting fitting = RoomFittings.forPurpose(purpose);
            if (fitting == null) continue;

            RoomShape shape = RoomShape.rectangle(16, 12);
            RoomLayout seeded = RoomLayoutSeed.from(purpose, shape, RoomFit.STANDARD);
            assertNotNull(seeded, purpose + " seeded to nothing");

            Furnished program = furnish(fitting, purpose, shape);
            Furnished document = furnish(new AuthoredFitting(seeded), purpose, shape);

            assertEquals(program.doodads(), document.doodads(),
                    purpose + ": the seeded document furnished the room differently");
            assertEquals(program.tasks(), document.tasks(),
                    purpose + ": the seeded document published different work");
            assertEquals(program.berths(), document.berths(),
                    purpose + ": the seeded document berthed differently");
            checked++;
        }
        assertTrue(checked >= 10, "only " + checked + " purposes were actually exercised");
    }

    /**
     * A seed is taken from the room standing on its own, so it is the same
     * document every time.
     *
     * <p>An editor whose first screen differed from its second would make every
     * comparison meaningless.
     */
    @Test
    void seedingTwiceGivesTheSameDocument() {
        RoomShape shape = RoomShape.rectangle(14, 10);
        RoomLayout once = RoomLayoutSeed.from(RoomPurpose.ARMORY, shape, RoomFit.STANDARD);
        RoomLayout twice = RoomLayoutSeed.from(RoomPurpose.ARMORY, shape, RoomFit.STANDARD);

        assertEquals(once.ops(), twice.ops(), "seeding the same room twice differed");
    }

    /**
     * A seeded document survives being written out and read back, which is the
     * path every saved room takes.
     */
    @Test
    void aSeededDocumentSurvivesTheRoundTripToDisk() throws Exception {
        RoomShape shape = RoomShape.rectangle(14, 10);
        RoomLayout seeded = RoomLayoutSeed.from(RoomPurpose.ARMORY, shape, RoomFit.STANDARD);

        RoomLayout reloaded = RoomLayoutJson.parse(
                new JSONObject(RoomLayoutJson.write(seeded).toString()));

        assertEquals(seeded.ops(), reloaded.ops());
        assertEquals(seeded.shape(), reloaded.shape());
        assertEquals(seeded.provides(), reloaded.provides());
    }

    /**
     * The seed carries the room's authored doors, because the arrangement
     * depends on them.
     *
     * <p>A berth puts its stowage by the hatches, so a document that forgot
     * where they were would record an arrangement no real room ever takes — and
     * the packer, reading the layout instead of the fitting, would then place
     * the room with no hookup constraint at all.
     */
    @Test
    void theSeedKeepsTheDoorsTheArrangementWasBuiltAround() {
        RoomShape shape = RoomShape.rectangle(8, 6);
        List<Hookup> authored = RoomFittings.forPurpose(RoomPurpose.BARRACKS).hookups(shape);
        RoomLayout seeded = RoomLayoutSeed.from(RoomPurpose.BARRACKS, shape, RoomFit.STANDARD);

        assertFalse(authored.isEmpty(), "a berth is supposed to author its hatches");
        assertEquals(authored.size(), seeded.hookups().size(),
                "the seeded document dropped the room's authored doors");
    }

    private record Furnished(List<String> doodads, List<String> tasks, List<String> berths) { }

    private static Furnished furnish(RoomFitting fitting, RoomPurpose purpose, RoomShape shape) {
        int margin = 4;
        int width = shape.width() + margin * 2;
        int height = shape.height() + margin * 2;
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        GenContext ctx = new GenContext(grid, topology, new Random(0L), width, height, 0L);

        List<Doorway> doors = new ArrayList<>();
        for (Hookup hookup : fitting.hookups(shape)) {
            for (Hookup.DoorSlot slot : hookup.slots()) {
                int[] cell = slot.cells().get(0);
                doors.add(new Doorway(margin + cell[0], margin + cell[1]));
            }
            break;
        }
        if (doors.isEmpty()) doors.add(new Doorway(margin, margin + shape.height() / 2));

        fitting.fit(new RoomFloor(ctx, new Room(shape, margin, margin,
                RoomPose.CANONICAL, purpose, doors), RoomFit.STANDARD));

        List<String> doodads = new ArrayList<>();
        for (Doodad doodad : ctx.doodads) {
            doodads.add(doodad.cellX + "," + doodad.cellY + " " + doodad.sheetPath
                    + "#" + doodad.tile.col + ":" + doodad.tile.row);
        }
        List<String> tasks = new ArrayList<>();
        for (FixtureTask task : ctx.fixtureTasks) {
            tasks.add(task.cellX() + "," + task.cellY() + " " + task.affordance());
        }
        List<String> berths = new ArrayList<>();
        for (Gantry gantry : ctx.gantries) {
            berths.add(gantry.centerX + "," + gantry.centerY + " " + gantry.facing);
        }
        return new Furnished(doodads, tasks, berths);
    }
}
