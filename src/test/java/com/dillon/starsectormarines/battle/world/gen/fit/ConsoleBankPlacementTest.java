package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.ConsoleBankLayout.Cell;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ConsoleBankPlacementTest {
    private static final List<Cell> LOOP = List.of(new Cell(3, 3), new Cell(4, 3), new Cell(5, 3),
            new Cell(5, 4), new Cell(5, 5), new Cell(4, 5), new Cell(3, 5), new Cell(3, 4));
    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}
    private record Floor(RoomFloor floor, GenContext ctx) {}
    private static Floor floor(RoomPose pose) {
        NavigationGrid grid = new NavigationGrid(12, 12);
        for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        GenContext ctx = new GenContext(grid, new CellTopology(12, 12), new Random(0), 12, 12, 0);
        return new Floor(new RoomFloor(ctx, new Room(RoomShape.rectangle(10, 10), 1, 1, pose,
                RoomPurpose.BRIDGE, List.of(new Doorway(0, 5))), RoomFit.STANDARD), ctx);
    }
    @Test void islandCoreAndModulesBecomeFixturesOnlyAtSealInEveryPose() {
        for (RoomPose pose : RoomPose.all()) {
            Floor f = floor(pose);
            assertTrue(f.floor.placeConsoleBank(LOOP, true, true));
            assertEquals(8, f.ctx.doodads.size());
            assertEquals(1, f.floor.placedFixtures());
            int[] core = f.floor.toLocal(4, 4);
            assertTrue(f.ctx.grid.isWalkable(core[0] + 1, core[1] + 1));
            f.floor.seal();
            for (Cell source : ConsoleBankLayout.occupiedCells(LOOP, true)) {
                int[] cell = f.floor.toLocal(source.x(), source.y()); int x = cell[0] + 1, y = cell[1] + 1;
                assertFalse(f.ctx.grid.isWalkable(x, y));
                assertTrue(f.ctx.grid.isSeeThrough(x, y));
                assertTrue(f.ctx.topology.isFixture(x, y));
                assertFalse(f.ctx.topology.isWall(x, y));
            }
        }
    }
    @Test void reservedCoreRefusesWholeIslandWithoutLeavingModules() {
        Floor f = floor(RoomPose.CANONICAL);
        f.floor.reserveLane(4, 4, 1, 1);
        assertFalse(f.floor.placeConsoleBank(LOOP, true, true));
        assertEquals(0, f.floor.placedFixtures());
        assertTrue(f.ctx.doodads.isEmpty());
        assertTrue(f.floor.isFree(3, 3));
    }
}
