package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSide;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckZone;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ArmoryRangeTrialTest {
    @Test void copiedRoomOwnsDamageAndClosesTheExitWithoutChangingTheShip() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        CellTopology topology = new CellTopology(12, 12);
        for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) {
            grid.setWalkable(x, y, true);
            grid.setSeeThrough(x, y, true);
        }
        grid.setDoorway(2, 4, true);
        grid.setWallHp(4, 4, 90);
        topology.setSurface(4, 4, topology.surfaceIndex("ship_test_surface"));
        MapResult source = new MapResult(grid, topology, 3, 3, 3, 3, List.of(), List.of());
        var room = new DeckGraph.Compartment(1, RoomShape.rectangle(5, 5), 3, 3,
                RoomPose.CANONICAL, DeckSide.PORT, DeckZone.MIDSHIPS,
                RoomPurpose.FIRING_RANGE, List.of());
        var copy = ArmoryRangeTrial.copyRoom(source, room, FixtureTask.at(3, 3, Affordance.PRACTICE, 6, 3));
        assertEquals(7, copy.grid.getWidth());
        assertFalse(copy.grid.isWalkable(0, 2));
        assertFalse(copy.grid.isDoorway(0, 2));
        assertTrue(source.grid.isDoorway(2, 4));
        assertEquals("ship_test_surface", copy.topology.getSurfaceId(2, 2));
        assertEquals(90, copy.grid.getWallHp(2, 2));
        copy.grid.setWallHp(2, 2, 0);
        copy.topology.setSurface(2, 2, 0);
        assertEquals(90, source.grid.getWallHp(4, 4));
        assertEquals("ship_test_surface", source.topology.getSurfaceId(4, 4));
    }
}
