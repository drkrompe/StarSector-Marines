package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.List;

/** Shared grid-authored shipboard quarters used by live and headless Barracks views. */
final class BarracksSceneLayout {

    static final int WIDTH = 30;
    static final int HEIGHT = 14;

    static final List<MarinePlacement> MARINES = List.of(
            new MarinePlacement(4, 5, 15f), new MarinePlacement(7, 5, 345f),
            new MarinePlacement(4, 8, 165f), new MarinePlacement(7, 8, 195f),
            new MarinePlacement(13, 5, 15f), new MarinePlacement(16, 5, 345f),
            new MarinePlacement(13, 8, 165f), new MarinePlacement(16, 8, 195f),
            new MarinePlacement(22, 5, 15f), new MarinePlacement(25, 5, 345f),
            new MarinePlacement(22, 8, 165f), new MarinePlacement(25, 8, 195f));

    static final List<PropPlacement> PROPS = List.of(
            new PropPlacement(3, 11, 5, 3), new PropPlacement(6, 11, 8, 2),
            new PropPlacement(9, 11, 6, 3), new PropPlacement(12, 11, 9, 2),
            new PropPlacement(15, 11, 7, 3), new PropPlacement(18, 11, 8, 2),
            new PropPlacement(21, 11, 5, 3), new PropPlacement(24, 11, 9, 2),
            new PropPlacement(27, 11, 6, 3),
            new PropPlacement(2, 2, 3, 3), new PropPlacement(27, 2, 9, 1));

    private BarracksSceneLayout() { }

    static boolean wall(int x, int y) {
        return x == 0 || x == WIDTH - 1 || y == 0 || y == HEIGHT - 1;
    }

    static CellTopology.GroundKind groundKind(int x, int y) {
        return CellTopology.GroundKind.INDOOR;
    }

    static List<FloorOverlayPlacement> floorOverlays() {
        return List.of();
    }

    record MarinePlacement(int cellX, int cellY, float facingDegrees) { }
    record FloorOverlayPlacement(int cellX, int cellY, int tileColumn, int tileRow) { }
    record PropPlacement(int cellX, int cellY, int tileColumn, int tileRow) { }
}
