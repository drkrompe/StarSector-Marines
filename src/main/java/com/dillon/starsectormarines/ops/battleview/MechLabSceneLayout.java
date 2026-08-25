package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.List;

/** One grid-authored fabrication room consumed by live and snapshot hosts. */
final class MechLabSceneLayout {

    static final int WIDTH = 15;
    static final int HEIGHT = 9;
    static final int MECH_X = 7;
    static final int MECH_Y = 4;

    static final List<PropPlacement> PROPS = List.of(
            new PropPlacement(2, 2, 5, 3),
            new PropPlacement(2, 4, 6, 3),
            new PropPlacement(2, 6, 7, 3),
            new PropPlacement(12, 2, 9, 2),
            new PropPlacement(12, 4, 9, 1),
            new PropPlacement(12, 6, 3, 3),
            new PropPlacement(5, 7, 8, 2),
            new PropPlacement(9, 7, 8, 2));

    static final List<TechnicianPlacement> TECHNICIANS = List.of(
            new TechnicianPlacement("fabricator one", 3, 2),
            new TechnicianPlacement("fabricator two", 11, 3),
            new TechnicianPlacement("fabricator three", 3, 6));

    private MechLabSceneLayout() { }

    static boolean wall(int x, int y) {
        return x == 0 || y == 0 || x == WIDTH - 1 || y == HEIGHT - 1;
    }

    static CellTopology.GroundKind groundKind(int x, int y) {
        boolean maintenancePad = x >= 5 && x <= 9 && y >= 2 && y <= 6;
        if (!maintenancePad) return CellTopology.GroundKind.INDOOR;
        boolean perimeter = x == 5 || x == 9 || y == 2 || y == 6;
        return perimeter ? CellTopology.GroundKind.STRIPED
                : CellTopology.GroundKind.TILE;
    }

    record PropPlacement(int cellX, int cellY, int tileColumn, int tileRow) { }

    record TechnicianPlacement(String name, int cellX, int cellY) { }
}
