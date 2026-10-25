package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The works lay no obstacle on a cell a cover prop already stands on, though the cell is open floor. */
class CordonWorksObstacleTest {

    @Test
    void anObstacleIsRefusedWhereACoverPropStands() {
        NavigationGrid grid = new NavigationGrid(8, 8);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) grid.setWalkableFloor(x, y);
        // A yard's crate stack dressed on open ground, its cell left walkable.
        List<Doodad> props = new ArrayList<>(List.of(new Doodad(3, 3, new DoodadDef("doodad.industrial-crate-stack",
                "sheet", 0, 0, DoodadCover.HEAVY, 0.6f, 1, 1, null, 0, null, FightingPositionRole.NONE))));
        CordonWorks works = new CordonWorks(grid, new CellTopology(8, 8), props, 1L, 0, 0);
        assertFalse(works.obstacle(3, 3, CordonBuilders.HEDGEHOG));
        assertEquals(1, props.size());
    }
}
