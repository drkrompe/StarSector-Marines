package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class MedicalServiceCourtTest {
    @Test void rotationPreservesFiveCellDriveAndWholeVehicleFootprints() {
        for (int width : new int[]{10,16}) for (int turn=0;turn<4;turn++) {
            GenContext c=ServiceYardTemplateTest.open();
            RoomPose pose=new RoomPose(turn,false);
            MedicalServiceCourt.stamp(c,new BlockLeaf(4,4,3+pose.posedWidth(width,14),3+pose.posedHeight(width,14),false),turn);
            assertTrue(c.doodads.stream().anyMatch(d -> "doodad.parked-cargo-truck".equals(d.definitionId())));
            var clearance=VehicleClearance.erode(c.grid,1);
            int half=width>=12 ? 2 : 1;
            for (int y=0;y<14;y++) for (int x=width/2-half;x<=width/2+half;x++) {
                int[] at=pose.map(x,y,width,14);
                if (x==width/2) assertTrue(clearance.isPassable(4+at[0],4+at[1]), "vehicle footprint must fit");
                assertTrue(c.grid.isWalkable(4+at[0],4+at[1]), "vehicle spine");
                assertFalse(c.topology.isFixture(4+at[0],4+at[1]));
            }
            for (var d:c.doodads) if (d.definitionId().contains("parked-")) {
                assertEquals((1+turn)%4,d.quarterTurns);
                for (int y=d.cellY;y<d.cellY+d.footprintCellsY;y++)
                    for (int x=d.cellX;x<d.cellX+d.footprintCellsX;x++) assertFalse(c.grid.isWalkable(x,y));
            }
            assertEquals(new Random(42).nextLong(),c.rng.nextLong());
        }
    }

    @Test void reservedRoadAndBerthRemainUntouched() {
        GenContext c=ServiceYardTemplateTest.open();
        boolean[][] reserved=new boolean[c.width][c.height];
        c.put(BspKeys.ROAD_RESERVATION,reserved);
        for (int y=0;y<c.height;y++) {
            reserved[5][y]=true;
            c.topology.setGroundKind(5,y,GroundKind.STREET);
        }
        var pad=new LandingPad(16,10,3,3,LandingPad.Approach.SOUTH,LandingPad.Purpose.FALLBACK);
        c.landingPads.add(pad);
        var ground=c.topology.getGroundKind(16,10);
        MedicalServiceCourt.stamp(c,new BlockLeaf(4,4,19,17,false),0);
        for (int y=0;y<c.height;y++) {
            assertEquals(GroundKind.STREET,c.topology.getGroundKind(5,y));
            assertTrue(c.grid.isWalkable(5,y));
        }
        assertEquals(ground,c.topology.getGroundKind(16,10));
        assertTrue(pad.isClear(c.grid,c.topology));
    }
}
