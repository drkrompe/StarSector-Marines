package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceFieldBuilderTest {

    @Test
    void wallInsideOneTacticalBlockDoesNotLeakToTheNextBlock() {
        NavigationGrid grid = openGrid(16, 8);
        for (int y = 0; y < grid.getHeight(); y++) grid.setWalkable(4, y, false);
        InfluenceTopology topology = new InfluenceTopology(grid, 8);

        float[] field = InfluenceFieldBuilder.propagate(topology,
                List.of(new InfluenceSource(2, 3, 1f)));

        assertEquals(1f, field[0], 0.0001f);
        assertEquals(0f, field[1], 0.0001f,
                "the coarse block must retain its disconnected fine components");
    }

    @Test
    void realOpeningAllowsAttenuatedCrossBlockPropagation() {
        NavigationGrid grid = openGrid(16, 8);
        for (int y = 0; y < grid.getHeight(); y++) grid.setWalkable(4, y, false);
        grid.setWalkableFloor(4, 3);
        InfluenceTopology topology = new InfluenceTopology(grid, 8);

        float[] field = InfluenceFieldBuilder.propagate(topology,
                List.of(new InfluenceSource(2, 3, 1f)));

        assertEquals(1f, field[0], 0.0001f);
        assertTrue(field[1] > 0f);
        assertEquals(InfluenceFieldBuilder.ATTENUATION, field[1], 0.0001f);
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
