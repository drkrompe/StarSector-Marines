package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ServiceYardTemplateTest {
    static GenContext open() {
        NavigationGrid grid = new NavigationGrid(40,40);
        CellTopology topology = new CellTopology(40,40);
        for (int y=0;y<40;y++) for (int x=0;x<40;x++) grid.setWalkableFloor(x,y);
        return new GenContext(grid,topology,new Random(42),40,40,42);
    }

    @Test void blockedAndReservedYardsRefuseBeforeChangingGroundPropsOrRandomness() {
        for (boolean road : new boolean[]{false,true}) {
            GenContext c = open();
            boolean[][] reserved = new boolean[40][40];
            c.put(BspKeys.ROAD_RESERVATION,reserved);
            if (road) reserved[5][5]=true; else c.grid.setWalkable(5,5,false);
            var before = c.topology.getGroundKind(4,4);
            assertFalse(ServiceYardTemplate.MAINTENANCE.canPlace(c,3,3,0));
            assertThrows(IllegalArgumentException.class, () -> ServiceYardTemplate.MAINTENANCE.place(c,3,3,0));
            assertEquals(before,c.topology.getGroundKind(4,4));
            assertTrue(c.doodads.isEmpty());
            assertEquals(new Random(42).nextLong(),c.rng.nextLong());
        }
    }

    @Test void footprintAndHeadingTurnTogetherWithoutMirroringVehicles() {
        for (ServiceYardTemplate template : ServiceYardTemplate.values()) {
            GenContext canonical = open(); template.place(canonical,3,3,0);
            for (int turn=1;turn<4;turn++) {
                GenContext rotated = open(); template.place(rotated,3,3,turn);
                RoomPose pose = new RoomPose(turn,false);
                assertEquals(canonical.doodads.size(),rotated.doodads.size());
                for (int i=0;i<canonical.doodads.size();i++) {
                    var a=canonical.doodads.get(i); var b=rotated.doodads.get(i);
                    int[] near=pose.map(a.cellX-3,a.cellY-3,template.width,template.height);
                    int[] far=pose.map(a.cellX-3+a.footprintCellsX-1,a.cellY-3+a.footprintCellsY-1,template.width,template.height);
                    assertEquals(3+Math.min(near[0],far[0]),b.cellX);
                    assertEquals(3+Math.min(near[1],far[1]),b.cellY);
                    assertEquals((a.quarterTurns+turn)%4,b.quarterTurns);
                }
            }
        }
    }
}
