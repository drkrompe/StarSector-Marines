package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A compartment laid athwartships holds very nearly what the same compartment
 * laid fore and aft does.
 *
 * <p>Doodads are placed <b>unrotated</b>. A rack drawn three cells long lies
 * along the deck's own x axis whatever the room did, so in a quarter-turned
 * compartment it stands three cells <em>deep</em> instead of three cells wide —
 * and a band sized for the upright orientation refuses it. {@code place}
 * returns false and says nothing, so the room generates, reserves its lanes,
 * cuts its doors, passes its own connectivity check, and ships with a fraction
 * of its fixtures.
 *
 * <p>That is the worst shape a defect can take here, and it is why this is
 * asked of every fitting rather than of the ones that happened to be edited.
 * It cost a washroom every fixture it had and an armoury seven of its nine,
 * and neither looked wrong: a turned room half full reads as a room that was
 * authored sparsely. Nothing but comparing it against its own upright twin
 * distinguishes the two.
 *
 * <p>The bar is deliberately loose. A turned room legitimately holds somewhat
 * less — the art reaches the other way, and a piece that no longer fits its
 * band correctly falls back to a stand-in that covers one cell instead of
 * three. What is being caught is collapse, not variation.
 */
class ATurnedRoomIsStillFurnishedTest {

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors)
            implements FurnishableRoom { }

    /** How much of the upright fill a quarter-turned room has to keep. */
    private static final double FLOOR = 0.4;

    @Test
    void everyShipboardFittingSurvivesAQuarterTurn() {
        check(RoomPurpose.MESS_HALL, new MessHallFitting(), 22, 12);
        check(RoomPurpose.STOCKROOM, new StockroomFitting(), 18, 12);
        check(RoomPurpose.LOADING_BAY, new LoadingBayFitting(), 18, 12);
        check(RoomPurpose.PARTS_CAGE, new PartsCageFitting(), 14, 10);
        check(RoomPurpose.ARMORY, new ArmoryFitting(), 12, 8);
        check(RoomPurpose.PATIENT_WARD, new SickBayFitting(), 14, 10);
        check(RoomPurpose.WASHROOM, new WashroomFitting(), 6, 5);
        check(RoomPurpose.CREW_LOUNGE, new LoungeFitting(), 16, 12);
        check(RoomPurpose.GYMNASIUM, new GymFitting(), 14, 10);
        check(RoomPurpose.ENGINE_ROOM, MachinerySpaceFitting.driveRoom(), 24, 22);
        check(RoomPurpose.PRODUCTION_FLOOR, MachinerySpaceFitting.auxiliaryPlant(), 20, 14);
        check(RoomPurpose.BARRACKS,
                new BerthingFitting(RoomPurpose.BARRACKS, "doodad.chest-1"), 8, 6);
    }

    private void check(RoomPurpose purpose, RoomFitting fitting, int width, int height) {
        int upright = fill(purpose, fitting, width, height, RoomPose.CANONICAL);
        int turned = fill(purpose, fitting, width, height, new RoomPose(1, false));

        assertTrue(upright > 0, purpose + " furnishes nothing even laid fore and aft");
        assertTrue(turned >= upright * FLOOR,
                purpose + " holds " + upright + " fixtures fore and aft and " + turned
                        + " athwartships, so its art no longer fits the bands it"
                        + " was laid out for and most of it is being refused in silence");
    }

    /** Furnish one room of this purpose in this pose and count what went down. */
    private int fill(RoomPurpose purpose, RoomFitting fitting,
                     int width, int height, RoomPose pose) {
        int posedWidth = pose.posedWidth(width, height);
        int posedHeight = pose.posedHeight(width, height);
        int origin = 4;
        int mapWidth = posedWidth + 2 * origin;
        int mapHeight = posedHeight + 2 * origin;
        NavigationGrid grid = new NavigationGrid(mapWidth, mapHeight);
        CellTopology topology = new CellTopology(mapWidth, mapHeight);
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) grid.setWalkableFloor(x, y);
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L),
                mapWidth, mapHeight, 7L);
        // The hatch is put on the room's own long bulkhead in both poses, so
        // the comparison is about the fill rather than about where the door is.
        Room room = new Room(RoomShape.rectangle(width, height), origin, origin, pose,
                purpose, List.of(new Doorway(origin + posedWidth / 2, origin - 1)));
        RoomFloor floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
        fitting.fit(floor);
        floor.dropUnreachableWork();
        return ctx.doodads.size();
    }
}
