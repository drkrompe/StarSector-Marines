package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomRecipe;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ShipBridgeFittingTest {
    private record Room(RoomShape shape,int originX,int originY,RoomPose pose,
                        RoomPurpose purpose,List<Doorway> doors) implements FurnishableRoom {}
    private record Fixture(GenContext ctx,RoomFloor floor,List<LayoutOp> ops) {}

    private static Fixture fit(RoomPose pose,List<int[]> doors) {
        RoomShape shape=RoomRecipe.COMMAND.shape().posed(pose);
        int w=shape.width()+8,h=shape.height()+8;
        NavigationGrid grid=new NavigationGrid(w,h);
        // Restrict navigation to this CIC and its actual doors; no escape through corners.
        for(int y=0;y<shape.height();y++) for(int x=0;x<shape.width();x++)
            if(shape.contains(x,y)) grid.setWalkableFloor(x+4,y+4);
        List<Doorway> localDoors=new ArrayList<>();
        for(int[] d:doors) {
            int[] cell=pose.map(d[0],d[1],17,13);
            localDoors.add(new Doorway(cell[0]+4,cell[1]+4));
            grid.setWalkableFloor(cell[0]+4,cell[1]+4);
        }
        GenContext ctx=new GenContext(grid,new CellTopology(w,h),new Random(7),w,h,7);
        RoomFloor floor=new RoomFloor(ctx,new Room(shape,4,4,pose,RoomPurpose.BRIDGE,localDoors),RoomFit.STANDARD);
        List<LayoutOp> ops=new ArrayList<>();floor.record(ops::add);
        RoomFittings.forShipRoom(RoomPurpose.BRIDGE,RoomRecipe.COMMAND.shape(),RoomFit.STANDARD).fit(floor);
        return new Fixture(ctx,floor,ops);
    }

    @Test void everyPoseKeepsThePlotFourBanksAndNineWatchPositionsAndOneSentry() {
        for(RoomPose pose:RoomPose.all()) {
            Fixture f=fit(pose,List.of(new int[]{8,-1}));
            assertBanks(f);assertEquals(10,f.ctx.fixtureTasks.size());verify(f);
        }
    }

    @Test void everyCicHatchAndOppositeHatchesPreserveCirculationAndWatch() {
        for(RoomPose pose:RoomPose.all()) {
            for(int[] door:RoomRecipe.COMMAND.shape().doorways()) {
                Fixture f=fit(pose,List.of(door));
                assertBanks(f);assertEquals(10,f.ctx.fixtureTasks.size());verify(f);
            }
            Fixture f=fit(pose,List.of(new int[]{8,-1},new int[]{8,13},new int[]{-1,6},new int[]{17,6}));
            assertBanks(f);assertEquals(10,f.ctx.fixtureTasks.size());verify(f);
        }
    }

    @Test void genericBridgesKeepTheirExistingFitting() {
        assertInstanceOf(BridgeFitting.class,RoomFittings.forPurpose(RoomPurpose.BRIDGE));
        assertInstanceOf(ShipBridgeFitting.class,RoomFittings.forShipRoom(RoomPurpose.BRIDGE,
                RoomRecipe.COMMAND.shape(),RoomFit.STANDARD));
    }

    private static void assertBanks(Fixture f) {
        var firstWatch = f.ctx.fixtureTasks.stream().filter(task -> task.affordance() == Affordance.WATCH)
                .limit(5).toList();
        assertEquals(1, f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly a
                && a.baseId().equals("ship-bridge.plot")).count());
        var banks = f.ops.stream().filter(LayoutOp.ConsoleBank.class::isInstance)
                .map(LayoutOp.ConsoleBank.class::cast).toList();
        assertEquals(4, banks.size());
        assertEquals(26, f.ctx.doodads.stream().filter(d -> d.definitionId().startsWith("console-bank.")).count());
        assertEquals(List.of(1L, 1L, 2L, 2L), banks.stream().map(bank ->
                ConsoleBankLayout.resolveWorld(bank.cells(), bank.closed(), bank.operatorRight()).stream()
                        .filter(piece -> piece.id().endsWith("corner")).count()).toList(), "Two L banks and two U banks");
        for (int[] stand : new int[][]{{8,4}, {7,11}, {9,1}, {3,5}, {13,5}, {3,7}, {13,7}, {9,11}, {7,1}}) {
            int[] local = f.floor.toLocal(stand[0], stand[1]);
            assertTrue(f.ctx.fixtureTasks.stream().anyMatch(task -> task.affordance() == Affordance.WATCH
                    && task.cellX() == local[0] + 4 && task.cellY() == local[1] + 4), "Keep every existing watch stand");
        }
        for (LayoutOp.ConsoleBank bank : banks) {
            assertTrue(firstWatch.stream().anyMatch(task -> bank.cells().stream().anyMatch(cell -> {
                int[] posed = f.floor.toLocal(cell.x(), cell.y());
                return task.fixtureX() == posed[0] + 4 && task.fixtureY() == posed[1] + 4;
            })), "First five watch positions must cover every bank");
        }
    }

    private static void verify(Fixture f) {
        assertTrue(f.floor.circulationSurvives());assertEquals(0,f.floor.dropUnreachableWork());
        f.floor.seal();HashSet<String> stands=new HashSet<>();
        // A square two-cell band around the plot remains open through every pose and hatch choice.
        for (int y = 3; y <= 9; y++) for (int x = 5; x <= 11; x++)
            if (Math.abs(x - 8) >= 2 || Math.abs(y - 6) >= 2) walkable(f, x, y);
        assertEquals(1, f.ctx.fixtureTasks.stream().filter(task -> task.affordance() == Affordance.GUARD).count());
        for(var task:f.ctx.fixtureTasks) {
            assertTrue(stands.add(task.cellX()+","+task.cellY()));
            assertTrue(f.ctx.grid.isWalkable(task.cellX(),task.cellY()));
            if (task.affordance() == Affordance.GUARD) {
                assertTrue(f.floor.room().doors().stream().anyMatch(door ->
                        door.x() == task.fixtureX() && door.y() == task.fixtureY()));
                assertEquals(1, Math.abs(task.cellX() - task.fixtureX()));
                assertEquals(1, Math.abs(task.cellY() - task.fixtureY()));
                for (Doorway door : f.floor.room().doors())
                    assertTrue(Math.abs(task.cellX() - door.x()) + Math.abs(task.cellY() - door.y()) > 1);
                continue;
            }
            assertEquals(Affordance.WATCH, task.affordance());
            assertTrue(f.ctx.topology.isFixture(task.fixtureX(),task.fixtureY()));
            assertEquals(1,Math.abs(task.cellX()-task.fixtureX())+Math.abs(task.cellY()-task.fixtureY()));
            var console = f.ctx.doodads.stream().filter(d -> d.cellX == task.fixtureX()
                    && d.cellY == task.fixtureY() && d.definitionId().startsWith("console-bank.")).findFirst();
            if (console.isPresent()) {
                var d = console.get();
                int dx = d.definitionId().endsWith("end-left") ? 1 : d.definitionId().endsWith("end-right") ? -1 : 0;
                int dy = dx == 0 ? -1 : 0;
                if (d.mirrored) dx = -dx;
                for (int turn = 0; turn < d.quarterTurns; turn++) { int old = dx; dx = -dy; dy = old; }
                assertEquals(dx, task.cellX() - task.fixtureX(), "Operator must face the control edge");
                assertEquals(dy, task.cellY() - task.fixtureY(), "Operator must face the control edge");
            }
        }
    }
    private static void walkable(Fixture f,int x,int y) {
        int[] cell=f.floor.toLocal(x,y);assertTrue(f.ctx.grid.isWalkable(cell[0]+4,cell[1]+4),x+","+y);
    }
}
