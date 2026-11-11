package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A works' solid props ({@link WorksProps#place}) on a bare yard; no map is generated. */
class WorksPropsTest {

    /** Yard stock a squad walks round is still cover: a belt's hedgehog is never laid on it. */
    @Test
    void coverIsNeverLaidOverCover() {
        GenContext c = yard();
        // Laid as a district yard lays its stock: walkable round, not a fixture.
        c.doodads.add(new Doodad(2, 2, TileRegistry.installed().doodad("doodad.industrial-crate-stack")));
        assertFalse(WorksProps.place(c, 2, 2, "doodad.fort-hedgehog", 0), "not over the crates");
        assertEquals(1, c.doodads.size());
        assertTrue(WorksProps.place(c, 3, 2, "doodad.fort-hedgehog", 0), "beside them");
    }

    private static GenContext yard() {
        NavigationGrid grid = new NavigationGrid(6, 6);
        CellTopology topology = new CellTopology(6, 6);
        for (int y = 0; y < 6; y++) for (int x = 0; x < 6; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, GroundKind.DIRT);
        }
        return new GenContext(grid, topology, new Random(1), 6, 6, 1);
    }
}
