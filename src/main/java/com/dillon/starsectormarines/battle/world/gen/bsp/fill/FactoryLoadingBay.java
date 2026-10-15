package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/** A real cargo opening onto receiving, with a two-cell-deep clear inside approach. */
final class FactoryLoadingBay {
    private FactoryLoadingBay() {}

    static void carve(NavigationGrid grid, CellTopology topology, int bl, int bt, int br, int bb,
                      BuildingPlacement.Side frontage, GroundKind ground) {
        for (int width : new int[]{3, 2}) {
            int bestScore = Integer.MIN_VALUE, bestSide = -1, bestStart = -1;
            for (BuildingPlacement.Side side : BuildingPlacement.Side.values()) {
                boolean horizontal = side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM;
                int lo = horizontal ? bl + 1 : bt + 1, hi = horizontal ? br - 1 : bb - 1;
                int fixed = switch (side) { case TOP -> bt; case BOTTOM -> bb; case LEFT -> bl; case RIGHT -> br; };
                int inward = side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.LEFT ? 1 : -1;
                for (int start = lo; start + width - 1 <= hi; start++) {
                    boolean valid = true;
                    for (int i = 0; i < width && valid; i++) for (int depth = -1; depth <= 2; depth++) {
                        int x = horizontal ? start + i : fixed + inward * depth;
                        int y = horizontal ? fixed + inward * depth : start + i;
                        if (!grid.inBounds(x,y) || (depth != 0 && !grid.isWalkable(x,y))
                                || (depth > 0 && topology.getRoomPurpose(x,y) != RoomPurpose.LOADING_BAY)) valid = false;
                    }
                    if (!valid) continue;
                    int score = (side == frontage ? 1000 : 0) - Math.abs(2 * start + width - 1 - lo - hi);
                    if (score > bestScore) { bestScore = score; bestSide = side.ordinal(); bestStart = start; }
                }
            }
            if (bestSide < 0) continue;
            BuildingPlacement.Side side = BuildingPlacement.Side.values()[bestSide];
            for (int i = 0; i < width; i++) {
                int x = side == BuildingPlacement.Side.LEFT ? bl : side == BuildingPlacement.Side.RIGHT ? br : bestStart + i;
                int y = side == BuildingPlacement.Side.TOP ? bt : side == BuildingPlacement.Side.BOTTOM ? bb : bestStart + i;
                grid.setWalkableFloor(x,y); grid.setDoorway(x,y,true);
                topology.setWall(x,y,false); topology.setWallDirMask(x,y,0);
                topology.setGroundKind(x,y,ground);
            }
            return;
        }
    }
}
