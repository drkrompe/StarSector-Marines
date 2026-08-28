package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;

import java.util.ArrayList;
import java.util.List;

final class BuildingWindowTestSupport {

    private BuildingWindowTestSupport() {}

    static List<SharedEdgeBarrier> windowsOwnedBy(NavigationGrid grid, BlockLeaf footprint) {
        List<SharedEdgeBarrier> result = new ArrayList<>();
        for (SharedEdgeBarrier barrier : grid.getEdgeBarriers()) {
            if (barrier.kind() == SharedEdgeBarrier.Kind.WINDOW
                    && footprint.contains(
                    barrier.structureCellX(), barrier.structureCellY())) {
                result.add(barrier);
            }
        }
        return result;
    }

    static Direction outwardFromOwner(SharedEdgeBarrier barrier) {
        if (barrier.structureCellX() == barrier.cellX()
                && barrier.structureCellY() == barrier.cellY()) {
            return barrier.direction();
        }
        return barrier.direction().opposite();
    }
}
