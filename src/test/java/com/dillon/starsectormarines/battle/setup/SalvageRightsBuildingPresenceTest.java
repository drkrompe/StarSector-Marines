package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SalvageRightsBuildingPresenceTest {
    @Test void concaveExteriorAndAdjacentBuildingsDoNotContestTheWorkshop() {
        var topology=new CellTopology(12,12);
        var building=new PointOfInterest(PointOfInterest.Kind.DEPOT,2,2,9,9,1,4,3,4);
        topology.setBuildingId(3,4,1);
        topology.setBuildingId(8,4,1);
        topology.setBuildingId(4,8,2);
        assertTrue(SalvageRightsSequence.insideBuilding(topology,building,3.5f,4.5f));
        assertTrue(SalvageRightsSequence.insideBuilding(topology,building,8.5f,4.5f));
        assertFalse(SalvageRightsSequence.insideBuilding(topology,building,8.5f,8.5f));
        assertFalse(SalvageRightsSequence.insideBuilding(topology,building,4.5f,8.5f));
        assertFalse(SalvageRightsSequence.insideBuilding(topology,building,10.5f,4.5f));
    }
}
