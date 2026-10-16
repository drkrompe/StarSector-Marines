package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class InstallationInteriorsTest {
    @Test void dispatchKeepsItsDesksMeetingNookAndWindowApproachesInEveryPose() {
        for (RoomPose pose : RoomPose.all()) {
            var room = room(RoomPurpose.CIVIC_OFFICE, 12, 10, pose);
            var ctx = open();
            FortressInterior.author(ctx, List.of(room));
            for (String id : List.of("colony-furnishings.desk", "colony-furnishings.shelf",
                    "colony-furnishings.locker", "crew-lounge.coffee", "mess-galley.stool"))
                assertTrue(ctx.doodads.stream().anyMatch(d -> d.definitionId().equals(id)), id + " / " + pose);
            assertTrue(ctx.fixtureTasks.isEmpty(), "Office scenery must not invent permanent crew posts");
            assertTrue(ctx.doodads.stream().noneMatch(d -> d.definitionId().startsWith("doodad.")));
            Set<String> windows = new HashSet<>();
            for (var b : ctx.grid.getEdgeBarriers()) if (b.kind() == SharedEdgeBarrier.Kind.WINDOW) {
                int x = b.structureCellX(), y = b.structureCellY();
                boolean canonical = x == b.cellX() && y == b.cellY();
                int dx = b.direction().dx * (canonical ? 1 : -1);
                int dy = b.direction().dy * (canonical ? 1 : -1);
                assertTrue(ctx.grid.isWalkable(x - dx, y - dy), "Blocked window approach");
                assertEquals("installation.command-floor", ctx.topology.getSurfaceId(x, y), "Window recess floor");
                assertFalse(ctx.grid.canTraverseCellStep(x, y, x + dx, y + dy));
                windows.add(x + "," + y);
            }
            assertTrue(windows.stream().anyMatch(key -> {
                String[] xy = key.split(","); int x = Integer.parseInt(xy[0]), y = Integer.parseInt(xy[1]);
                return windows.contains((x + 1) + "," + y) && windows.contains((x + 2) + "," + y)
                        || windows.contains(x + "," + (y + 1)) && windows.contains(x + "," + (y + 2));
            }), "No three-pane window bay");
            checkInterior(ctx, room);
        }
    }

    @Test void staffRoomsUseCompleteModernKitsAndKeepDoorsReachable() {
        for (RoomPurpose purpose : List.of(RoomPurpose.BARRACKS, RoomPurpose.MESS_HALL, RoomPurpose.PATIENT_WARD)) {
            int w = purpose == RoomPurpose.BARRACKS ? 16 : purpose == RoomPurpose.MESS_HALL ? 11 : 14;
            int h = purpose == RoomPurpose.BARRACKS ? 6 : purpose == RoomPurpose.MESS_HALL ? 8 : 10;
            String kit = purpose == RoomPurpose.BARRACKS ? "barracks-bunks." :
                    purpose == RoomPurpose.MESS_HALL ? "mess-galley." : "sickbay-clinic.";
            for (RoomPose pose : RoomPose.all()) {
                var room = room(purpose, w, h, pose); var ctx = open();
                FortressInterior.author(ctx, List.of(room));
                assertTrue(ctx.doodads.stream().anyMatch(d -> d.definitionId().startsWith(kit)), purpose + " / " + pose);
                assertTrue(ctx.doodads.stream().noneMatch(d -> d.definitionId().equals("doodad.box")));
                checkInterior(ctx, room);
            }
        }
    }

    @Test void compactDispatchStillHasPairedDeskAndSeat() {
        var ctx = open(); var room = room(RoomPurpose.CIVIC_OFFICE, 7, 6, new RoomPose(0, false));
        FortressInterior.author(ctx, List.of(room));
        assertTrue(ctx.doodads.stream().anyMatch(d -> d.definitionId().equals("colony-furnishings.desk")));
        assertTrue(ctx.doodads.stream().anyMatch(d -> d.definitionId().equals("mess-galley.stool")));
        checkInterior(ctx, room);
    }

    private static GenContext open() {
        var grid = new NavigationGrid(40, 40);
        for (int x = 0; x < 40; x++) for (int y = 0; y < 40; y++) grid.setWalkableFloor(x, y);
        return new GenContext(grid, new CellTopology(40, 40), new Random(42), 40, 40, 42);
    }

    private static RoomPacker.Placed room(RoomPurpose purpose, int w, int h, RoomPose pose) {
        int[] door = pose.map(w / 2, -1, w, h);
        return new RoomPacker.Placed(RoomShape.rectangle(w, h).posed(pose), pose, 8, 8, purpose,
                List.of(new Doorway(8 + door[0], 8 + door[1])));
    }

    private static void checkInterior(GenContext ctx, RoomPacker.Placed room) {
        Doorway door = room.doors().get(0);
        Set<String> reached = flood(ctx.grid, door.x(), door.y());
        for (int[] c : room.shape().filled()) {
            int x = room.originX() + c[0], y = room.originY() + c[1];
            if (ctx.grid.isWalkable(x, y)) assertTrue(reached.contains(x + "," + y), "Stranded interior floor");
        }
        // With the actual doorway shut, apertures must not leak into the yard at corners.
        ctx.grid.setWalkable(door.x(), door.y(), false);
        int[] start = Arrays.stream(room.shape().filled()).filter(c -> ctx.grid.isWalkable(
                room.originX() + c[0], room.originY() + c[1])).findFirst().orElseThrow();
        assertFalse(flood(ctx.grid, room.originX() + start[0], room.originY() + start[1]).contains("0,0"));
    }

    private static Set<String> flood(NavigationGrid grid, int x, int y) {
        Set<String> reached = new HashSet<>(); var queue = new ArrayDeque<int[]>(); queue.add(new int[]{x, y});
        while (!queue.isEmpty()) {
            int[] c = queue.remove();
            if (!reached.add(c[0] + "," + c[1])) continue;
            for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (grid.inBounds(nx, ny) && grid.canTraverseCellStep(c[0], c[1], nx, ny)) queue.add(new int[]{nx, ny});
            }
        }
        return reached;
    }
}
