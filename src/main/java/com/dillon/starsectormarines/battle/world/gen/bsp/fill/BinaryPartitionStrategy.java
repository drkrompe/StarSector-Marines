package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.Random;

/**
 * Single interior wall dividing a building into two chambers. Lifted
 * from the legacy {@code maybeAddInteriorWall} logic in
 * {@link BuildingShellCore}.
 */
final class BinaryPartitionStrategy implements PartitionStrategy {

    private static final int MIN_DIM = 7;

    static final BinaryPartitionStrategy DEFAULT = new BinaryPartitionStrategy(0.65f);

    private final float chance;

    BinaryPartitionStrategy(float chance) {
        this.chance = chance;
    }

    @Override
    public PartitionLayout partition(NavigationGrid grid, CellTopology topology,
                                     int bl, int bt, int br, int bb,
                                     Random rng, GroundKind interiorGround) {
        int w = br - bl + 1;
        int h = bb - bt + 1;
        boolean canVert  = w >= MIN_DIM;
        boolean canHoriz = h >= MIN_DIM;
        if (!canVert && !canHoriz) return PartitionLayout.NONE;
        if (rng.nextFloat() >= chance) return PartitionLayout.NONE;

        boolean vertical;
        if (canVert && canHoriz) {
            if (w > h)      vertical = true;
            else if (h > w) vertical = false;
            else            vertical = rng.nextBoolean();
        } else {
            vertical = canVert;
        }

        if (vertical) {
            int wx = bl + 3 + rng.nextInt(w - 6);
            for (int y = bt + 1; y <= bb - 1; y++) {
                grid.setWalkable(wx, y, false);
            }
            int dy = bt + 1 + rng.nextInt(h - 2);
            openInteriorDoorway(grid, topology, wx, dy, interiorGround);
            return new PartitionLayout(PartitionLayout.Orient.VERTICAL, new int[]{wx});
        } else {
            int wy = bt + 3 + rng.nextInt(h - 6);
            for (int x = bl + 1; x <= br - 1; x++) {
                grid.setWalkable(x, wy, false);
            }
            int dx = bl + 1 + rng.nextInt(w - 2);
            openInteriorDoorway(grid, topology, dx, wy, interiorGround);
            return new PartitionLayout(PartitionLayout.Orient.HORIZONTAL, new int[]{wy});
        }
    }

    static void openInteriorDoorway(NavigationGrid grid, CellTopology topology,
                                    int x, int y, GroundKind interiorGround) {
        grid.setWalkable(x, y, true);
        grid.setDoorway(x, y, true);
        grid.openAllEdges(x, y);
        topology.setGroundKind(x, y, interiorGround);
    }
}
