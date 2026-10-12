package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.ship.stage.ShipMaterialStage;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipDeckTrimTest {
    private static final String KIT = "ship-floor-trim.";

    @Test void floorsDistinguishPassageAndRoomsWithoutRepaintingAuthoredSurfaces() {
        GenContext ctx = context(6, 1);
        RoomPurpose[] purposes = {RoomPurpose.CORRIDOR, RoomPurpose.CREW_LOUNGE,
                RoomPurpose.PATIENT_WARD, RoomPurpose.ENGINE_ROOM, RoomPurpose.PRODUCTION_FLOOR, RoomPurpose.CORRIDOR};
        for (int x = 0; x < 6; x++) ctx.topology.setRoomPurpose(x, 0, purposes[x]);
        ctx.topology.setSurface(5, 0, ctx.topology.surfaceIndex("authored.floor"));
        new ShipMaterialStage().run(ctx);
        String[] expected = {"passage", "lounge", "clinical", "engineering", "engineering"};
        for (int x = 0; x < 5; x++) assertEquals(KIT + expected[x], ctx.topology.getSurfaceId(x, 0));
        assertEquals("authored.floor", ctx.topology.getSurfaceId(5, 0));
        for (int x = 0; x < 6; x++) assertTrue(ctx.grid.isWalkable(x, 0));
    }

    @Test void edgesFollowAllFourWallsAndOpenHatchesWhileTrimStaysBelowFurniture() {
        GenContext ctx = context(7, 7);
        for (int y = 0; y < 7; y++) for (int x = 0; x < 7; x++) {
            if (x != 0 && x != 6 && y != 0 && y != 6) continue;
            ctx.topology.setWall(x, y, true); ctx.grid.setWalkable(x, y, false);
            ctx.grid.setWallHp(x, y, 83);
        }
        ctx.topology.setWall(3, 0, false); ctx.grid.setWalkableFloor(3, 0); ctx.grid.setDoorway(3, 0, true);
        ctx.topology.setSurface(4, 4, ctx.topology.surfaceIndex("authored.floor"));
        ctx.topology.setGroundKind(5, 5, GroundKind.VOID);
        Doodad fixture = new Doodad(2, 2, TileRegistry.installed().doodad("ship-utility.server"));
        ctx.doodads.add(fixture);
        new ShipMaterialStage().run(ctx);
        assertEdge(ctx, 3, 5, 0); assertEdge(ctx, 1, 3, 1);
        assertEdge(ctx, 2, 1, 2); assertEdge(ctx, 5, 3, 3);
        assertFalse(ctx.doodads.stream().anyMatch(d -> (KIT + "edge").equals(d.definitionId())
                && d.cellX == 3 && d.cellY == 1 && d.quarterTurns == 2));
        assertFalse(ctx.doodads.stream().anyMatch(d -> d.cellX == 4 && d.cellY == 4));
        assertFalse(ctx.doodads.stream().anyMatch(d -> d.cellX == 5 && d.cellY == 5));
        assertSame(fixture, ctx.doodads.get(ctx.doodads.size()-1));
        List<String> once = placements(ctx);
        new ShipMaterialStage().run(ctx);
        assertEquals(once, placements(ctx));
        assertTrue(ctx.grid.isWalkable(3, 0)); assertTrue(ctx.grid.isDoorway(3, 0));
        assertFalse(ctx.grid.isWalkable(0, 3)); assertEquals(83, ctx.grid.getWallHp(0, 3));
        assertTrue(ctx.topology.isWall(0, 3));
        assertEquals(GroundKind.VOID, ctx.topology.getGroundKind(5, 5));
        for (Doodad d : ctx.doodads) if (d != fixture) {
            assertEquals(0, d.cover); assertEquals(0, d.ballisticHalfHeight);
        }
    }

    @Test void edgeCanvasesReachBothEndsAndLeaveTheFloorVisible() throws Exception {
        for (String id : List.of("edge", "binding")) {
            DoodadDef def = TileRegistry.installed().doodad(KIT + id);
            var image = ImageIO.read(Path.of("mod", def.sheetPath).toFile());
            int ox = def.col * def.sourceCellPx, oy = def.row * def.sourceCellPx;
            for (int x : new int[]{1, def.sourceCellPx/2, def.sourceCellPx-2}) {
                int alpha = 0;
                for (int y = 0; y < 8; y++) alpha = Math.max(alpha, image.getRGB(ox+x, oy+y) >>> 24);
                assertTrue(alpha > 40, id + " must reach both ends");
                assertEquals(0, image.getRGB(ox+x, oy+32) >>> 24, id + " floor must remain visible");
            }
        }
    }

    private static void assertEdge(GenContext ctx, int x, int y, int turn) {
        assertTrue(ctx.doodads.stream().anyMatch(d -> (KIT + "edge").equals(d.definitionId())
                && d.cellX == x && d.cellY == y && d.quarterTurns == turn));
    }
    private static List<String> placements(GenContext ctx) {
        return ctx.doodads.stream().map(d -> d.definitionId()+":"+d.cellX+":"+d.cellY+":"+d.quarterTurns).toList();
    }
    private static GenContext context(int w, int h) {
        GenContext ctx = new GenContext(new NavigationGrid(w,h), new CellTopology(w,h), new Random(1), w,h,1);
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) {
            ctx.grid.setWalkableFloor(x,y); ctx.topology.setGroundKind(x,y,GroundKind.INDOOR);
        }
        return ctx;
    }
}
