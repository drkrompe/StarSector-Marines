package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A machinery space is a place people work, not a warehouse of one machine.
 *
 * <p>What the arrangement has to produce is a <b>hierarchy</b>: several kinds of
 * plant rather than one repeated one, a board somebody stands a watch at, and a
 * standing list of defects that scales with how much machinery the room holds.
 * The two purposes that used to be ranked with a single fixture down both
 * bulkheads satisfied none of that while passing every check that existed —
 * they were furnished, they published work, and they read as shelving.
 *
 * <p>Asked of one room on a synthetic floor rather than of a generated deck.
 * The subject is the fitting and the floor it fills; standing up a ship to look
 * at an engine room would answer a question about world generation instead, and
 * would fail for reasons belonging to the hull.
 */
class MachinerySpaceIsWorkedTest {

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors)
            implements FurnishableRoom { }

    private static final String TURBINE = "doodad.industrial-turbine-set";
    private static final String REACTOR = "doodad.industrial-reactor-housing";
    private static final String SWITCHBOARD = "doodad.industrial-switchboard";
    private static final String CONTROL_BOARD = "doodad.industrial-engine-control-board";

    /** The drive itself, at the footprint {@code RoomRecipe.ENGINE_ROOM} authors. */
    @Test
    void theDriveRoomIsPlantWithAWatchOnIt() {
        Fitted fitted = driveRoom(24, 22, 4, 4);

        assertTrue(fitted.kinds() >= 6,
                "the drive room came out as one machine repeated, not as machinery");
        assertTrue(fitted.count(REACTOR) > 0,
                "the heavy flat holds nothing heavier than what serves it");
        assertTrue(fitted.count(Affordance.TEND) > 0, "no plant is being kept running");
        assertTrue(fitted.count(Affordance.WATCH) > 0,
                "nobody stands a watch here, so the machinery runs itself");
        assertTrue(fitted.count(Affordance.REPAIR) > 0, "a drive room with no defect list");
        assertTrue(fitted.survives(),
                "the drive room's own fill severed its circulation, so it ships as bare deck");
    }

    /** The auxiliary plant forward of it, at {@code RoomRecipe.ENGINEERING}'s footprint. */
    @Test
    void theAuxiliaryPlantIsFittedTheSameWay() {
        Fitted fitted = auxiliaryPlant(20, 14, 4, 4);

        assertTrue(fitted.kinds() >= 6, "the auxiliary plant came out as one repeated machine");
        assertTrue(fitted.count(Affordance.TEND) > 0, "no plant is being kept running");
        assertTrue(fitted.count(Affordance.WATCH) > 0, "nobody stands a watch here");
        assertTrue(fitted.count(Affordance.REPAIR) > 0, "no defect list");
        assertTrue(fitted.survives(), "the auxiliary plant's fill severed its circulation");
    }

    /**
     * The list is a proportion of the plant, so a bigger compartment carries a
     * longer one — the quota that keeps a trade moving around a large hull
     * instead of finishing the whole ship's work in one compartment.
     */
    @Test
    void aBiggerMachinerySpaceCarriesMoreWork() {
        Fitted large = driveRoom(24, 22, 4, 4);
        Fitted small = driveRoom(12, 10, 4, 4);

        assertTrue(large.count(Affordance.TEND) > small.count(Affordance.TEND),
                "the larger drive room tends no more plant than the smaller one");
        assertTrue(large.count(Affordance.REPAIR) > small.count(Affordance.REPAIR),
                "the defect list does not grow with the machinery it is a list of");
        assertTrue(large.duty() > small.duty(),
                "job counts do not scale with the room");
    }

    /**
     * A list never covers the whole run.
     *
     * <p>Otherwise the quota would satisfy the test above by breaking every
     * machine in the room, which is not a ship on a list — it is a wreck, and it
     * would leave the compartment publishing no ordinary tending at all.
     */
    @Test
    void theListNeverCoversEveryMachine() {
        Fitted fitted = driveRoom(24, 22, 4, 4);

        assertTrue(fitted.count(Affordance.REPAIR) < fitted.count(Affordance.TEND),
                "every machine aboard is defective, which is a wreck rather than a list");
    }

    /**
     * Which machines are defective is drawn from where the room sits and where
     * the machine sits in it, never from a live random.
     *
     * <p>Generation has to replay identically from a seed. A fitting that reached
     * for an unseeded source would make the same hull come out differently twice
     * and there would be nothing in the output to say why.
     */
    @Test
    void theSameRoomIsFittedTheSameWayTwice() {
        assertEquals(driveRoom(24, 22, 4, 4).jobs(), driveRoom(24, 22, 4, 4).jobs(),
                "the same compartment fitted twice produced two different rooms");
    }

    /** Two engine rooms in one hull do not carry the same defects. */
    @Test
    void aRoomElsewhereOnTheDeckHasItsOwnDefects() {
        assertNotEquals(defects(driveRoom(24, 22, 4, 4)), defects(driveRoom(24, 22, 5, 9)),
                "every machinery space on the deck is broken in exactly the same places");
    }

    /**
     * A main engine is not one of several.
     *
     * <p>The turbine is authored as the drive room's centrepiece rather than as
     * a catalogue entry, and the difference is the whole point: ranked like the
     * rest it put three main engines side by side down one flat, which is a
     * different and slightly worse lie than the rank of tanks it replaced.
     */
    @Test
    void theDriveHasOneTurbineAndTheAuxiliaryPlantHasNone() {
        assertEquals(1, driveRoom(24, 22, 4, 4).count(TURBINE),
                "the drive room holds a rank of main engines, or none at all");
        assertEquals(0, auxiliaryPlant(20, 14, 4, 4).count(TURBINE),
                "the auxiliary plant has taken the drive's turbine");
    }

    /**
     * The big plant survives a quarter turn.
     *
     * <p>Doodads are placed unrotated, so a piece drawn five cells long lies
     * along the deck's x axis whatever the compartment did — and in a turned
     * room that axis is the fitting's <em>across</em>. A fitting that authored
     * spans in one frame and placed art in the other would reserve five cells of
     * a flat, draw the turbine lying athwart it, and lay the next machine
     * straight through it. Nothing about the resulting plan looks wrong, which
     * is why this is asked rather than assumed.
     */
    @Test
    void aQuarterTurnedMachinerySpaceKeepsItsBigPlant() {
        Fitted turned = fit(MachinerySpaceFitting.driveRoom(), 24, 22, 4, 4,
                new RoomPose(1, false));

        assertEquals(1, turned.count(TURBINE), "the turned drive room lost its centrepiece");
        assertTrue(turned.count(REACTOR) > 0, "the turned drive room lost its heavy plant");
        assertTrue(turned.count(SWITCHBOARD) > 0, "the turned drive room lost its switchgear");
        assertTrue(turned.count(CONTROL_BOARD) > 0, "the turned drive room lost its board");
        assertTrue(turned.count(Affordance.WATCH) > 0, "nobody stands a watch in the turned room");
        assertTrue(turned.survives(), "the turned drive room's fill severed its circulation");
    }

    private record Fitted(List<Doodad> doodads, List<FixtureTask> tasks, boolean survives) {

        int count(Affordance affordance) {
            return (int) tasks.stream().filter(task -> task.affordance() == affordance).count();
        }

        /**
         * How many of one registered prop are standing in the room, matched on
         * the frame the registry gives that id rather than on a name the fill
         * could have got wrong.
         */
        int count(String doodadId) {
            DoodadDef def = TileRegistry.installed().doodad(doodadId);
            assertNotNull(def, doodadId + " is not in the tile registry");
            return (int) doodads.stream()
                    .filter(d -> d.tile.col == def.col && d.tile.row == def.row)
                    .count();
        }

        /** How many jobs of any kind this room posts somebody to. */
        int duty() {
            return (int) tasks.stream().filter(task -> task.affordance().duty()).count();
        }

        /**
         * Distinct pieces of art standing in the room. Counted off the sprite
         * rather than off an id list, because the defect being tested for is
         * that a room reads as one machine repeated, and that is a fact about
         * what is drawn.
         */
        int kinds() {
            Set<String> frames = new HashSet<>();
            for (Doodad doodad : doodads) frames.add(doodad.tile.col + "," + doodad.tile.row);
            return frames.size();
        }

        /** Every job, as text, so two fills can be compared for being the same fill. */
        List<String> jobs() {
            return tasks.stream()
                    .map(task -> task.cellX() + ":" + task.cellY() + ":" + task.affordance())
                    .toList();
        }
    }

    private static List<String> defects(Fitted fitted) {
        return fitted.tasks().stream()
                .filter(task -> task.affordance() == Affordance.REPAIR)
                .map(task -> task.cellX() + ":" + task.cellY())
                .toList();
    }

    private static Fitted driveRoom(int width, int height, int originX, int originY) {
        return fit(MachinerySpaceFitting.driveRoom(), width, height, originX, originY,
                RoomPose.CANONICAL);
    }

    private static Fitted auxiliaryPlant(int width, int height, int originX, int originY) {
        return fit(MachinerySpaceFitting.auxiliaryPlant(), width, height, originX, originY,
                RoomPose.CANONICAL);
    }

    /**
     * @param width the room's extent along the fitting's canonical frame; the
     *     synthetic floor is laid out at the extents that frame ends up with
     *     once posed, which is what a placed room would have handed the fitting
     */
    private static Fitted fit(MachinerySpaceFitting fitting, int width, int height,
                              int originX, int originY, RoomPose pose) {
        int localWidth = pose.posedWidth(width, height);
        int localHeight = pose.posedHeight(width, height);
        int mapWidth = originX + localWidth + 4;
        int mapHeight = originY + localHeight + 4;
        NavigationGrid grid = new NavigationGrid(mapWidth, mapHeight);
        CellTopology topology = new CellTopology(mapWidth, mapHeight);
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) grid.setWalkableFloor(x, y);
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L),
                mapWidth, mapHeight, 7L);
        Room room = new Room(RoomShape.rectangle(localWidth, localHeight), originX, originY,
                pose, fitting.purpose(),
                List.of(new Doorway(originX, originY + localHeight / 2)));
        RoomFloor floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
        fitting.fit(floor);
        floor.dropUnreachableWork();
        return new Fitted(List.copyOf(ctx.doodads), List.copyOf(ctx.fixtureTasks),
                floor.circulationSurvives());
    }
}
