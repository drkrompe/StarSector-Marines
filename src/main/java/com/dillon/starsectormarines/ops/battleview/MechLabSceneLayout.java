package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ambient.AmbientActivity;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskRoute;
import com.dillon.starsectormarines.battle.ambient.AmbientThreatPolicy;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.List;

/** One grid-authored four-gantry garage consumed by live and snapshot hosts. */
final class MechLabSceneLayout {

    static final int WIDTH = 39;
    static final int HEIGHT = 11;
    static final int GANTRY_Y = 5;
    static final List<Gantry> GANTRIES = List.of(
            new Gantry(7, GANTRY_Y),
            new Gantry(15, GANTRY_Y),
            new Gantry(23, GANTRY_Y),
            new Gantry(31, GANTRY_Y));

    static final List<PropPlacement> PROPS = List.of(
            new PropPlacement(5, 8, 5, 3),
            new PropPlacement(9, 8, 8, 2),
            new PropPlacement(13, 8, 6, 3),
            new PropPlacement(17, 8, 9, 2),
            new PropPlacement(21, 8, 7, 3),
            new PropPlacement(25, 8, 8, 2),
            new PropPlacement(29, 8, 3, 3),
            new PropPlacement(33, 8, 9, 1));

    static final List<AmbientTaskRoute> TECHNICIAN_JOBS = List.of(
            technicianJob("fabricator one", 0f, 7, -1f),
            technicianJob("fabricator two", 5.5f, 15, 1f),
            technicianJob("fabricator three", 10.5f, 23, -1f),
            technicianJob("fabricator four", 15.5f, 31, 1f));

    private MechLabSceneLayout() { }

    static boolean wall(int x, int y) {
        // The south edge deliberately remains open as the vehicle entrance.
        return x == 0 || x == WIDTH - 1 || y == HEIGHT - 1;
    }

    static CellTopology.GroundKind groundKind(int x, int y) {
        return CellTopology.GroundKind.INDOOR;
    }

    static List<FloorOverlayPlacement> floorOverlays() {
        java.util.ArrayList<FloorOverlayPlacement> result = new java.util.ArrayList<>();
        for (Gantry gantry : GANTRIES) {
            for (int y = 2; y <= 7; y++) {
                for (int x = gantry.cellX() - 2; x <= gantry.cellX() + 2; x++) {
                    boolean perimeter = x == gantry.cellX() - 2
                            || x == gantry.cellX() + 2 || y == 2 || y == 7;
                    int column = perimeter ? 1 : ((x + y) & 1) == 0 ? 0 : 2;
                    result.add(new FloorOverlayPlacement(x, y, column, 3));
                }
            }
        }
        return List.copyOf(result);
    }

    private static AmbientTaskRoute technicianJob(String name, float phaseOffsetSeconds,
                                                   int gantryX, float side) {
        float weldX = gantryX + 0.5f + side * 1.35f;
        float weldFocusX = gantryX + 0.5f + side * 0.72f;
        float storageX = gantryX + 0.5f - side * 2f;
        float machineryX = gantryX + 0.5f + side * 2f;
        return new AmbientTaskRoute(name, phaseOffsetSeconds, 0.72f, 14f,
                AmbientThreatPolicy.ANY_COMBATANT, List.of(
                new AmbientTaskRoute.Stop(weldX, 5.5f, 4.4f,
                        AmbientActivity.WORKING,
                        weldFocusX, 5.5f),
                new AmbientTaskRoute.Stop(storageX, 7.5f, 3.2f,
                        AmbientActivity.SOCIALIZING,
                        storageX, 8.5f),
                new AmbientTaskRoute.Stop(machineryX, 7.5f, 2.6f,
                        AmbientActivity.INSPECTING,
                        machineryX, 8.5f)));
    }

    record Gantry(int cellX, int cellY) { }

    record FloorOverlayPlacement(int cellX, int cellY, int tileColumn, int tileRow) { }

    record PropPlacement(int cellX, int cellY, int tileColumn, int tileRow) { }

}
