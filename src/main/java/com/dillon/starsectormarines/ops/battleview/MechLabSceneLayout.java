package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ambient.AmbientActivity;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskRoute;
import com.dillon.starsectormarines.battle.ambient.AmbientThreatPolicy;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.List;

/** One grid-authored four-gantry garage consumed by live and snapshot hosts. */
final class MechLabSceneLayout {

    static final int WIDTH = 49;
    static final int HEIGHT = 20;
    static final int GANTRY_Y = 7;
    private static final int SERVICE_BULKHEAD_Y = 16;
    static final List<Gantry> GANTRIES = List.of(
            new Gantry(7, GANTRY_Y),
            new Gantry(18, GANTRY_Y),
            new Gantry(29, GANTRY_Y),
            new Gantry(40, GANTRY_Y));

    /** Registered industrial fixtures shared with generated workshops and yards. */
    static final List<PropPlacement> PROPS = List.of(
            prop(2, 12, "doodad.industrial-crate-stack"),
            prop(3, 14, "doodad.industrial-fluid-tank"),
            prop(5, 13, "doodad.industrial-machine-tool"),
            prop(10, 13, "doodad.industrial-control-console"),
            prop(12, 11, "doodad.industrial-cable-reel"),
            prop(13, 14, "doodad.industrial-pallet-stack"),
            prop(16, 13, "doodad.industrial-machine-tool"),
            prop(21, 13, "doodad.industrial-control-console"),
            prop(23, 11, "doodad.industrial-crate-stack"),
            prop(24, 14, "doodad.industrial-pipe-bundle"),
            prop(27, 13, "doodad.industrial-machine-tool"),
            prop(32, 13, "doodad.industrial-control-console"),
            prop(34, 11, "doodad.industrial-cable-reel"),
            prop(35, 14, "doodad.industrial-drum-cluster"),
            prop(38, 13, "doodad.industrial-machine-tool"),
            prop(43, 13, "doodad.industrial-control-console"),
            prop(45, 11, "doodad.industrial-pallet-stack"),
            prop(45, 14, "doodad.industrial-fluid-tank"),
            prop(46, 12, "doodad.industrial-generator"),

            // The north cross-corridor belongs to the larger ship facility.
            prop(3, 17, "doodad.office-server-rack"),
            prop(20, 17, "doodad.office-workstation-bank"),
            prop(24, 17, "doodad.military-command-console"),
            prop(28, 17, "doodad.office-workstation-bank"),
            prop(45, 17, "doodad.office-server-rack"));

    static final List<AmbientTaskRoute> TECHNICIAN_JOBS = List.of(
            technicianJob("fabricator one", 0f, 7, -1f),
            technicianJob("fabricator two", 5.5f, 18, 1f),
            technicianJob("fabricator three", 10.5f, 29, -1f),
            technicianJob("fabricator four", 15.5f, 40, 1f));
    static final List<AmbientTaskRoute> SUPPORT_JOBS = List.of(
            logisticsJob("parts runner west", 3.5f, 7, 18, 1f),
            logisticsJob("parts runner east", 12f, 40, 29, -1f),
            inspectionJob("systems inspector", 7f, 18, 29),
            corridorJob("bay coordinator", 18f));
    static final List<AmbientTaskRoute> FACILITY_JOBS = facilityJobs();

    private MechLabSceneLayout() { }

    static boolean wall(int x, int y) {
        // The south edge deliberately remains open as the vehicle entrance.
        if (x == 0 || x == WIDTH - 1 || y == HEIGHT - 1) return true;
        return y == SERVICE_BULKHEAD_Y && !serviceDoor(x);
    }

    static CellTopology.GroundKind groundKind(int x, int y) {
        return CellTopology.GroundKind.INDOOR;
    }

    static List<FloorOverlayPlacement> floorOverlays() {
        java.util.ArrayList<FloorOverlayPlacement> result = new java.util.ArrayList<>();
        for (Gantry gantry : GANTRIES) {
            for (int y = 3; y <= 10; y++) {
                for (int x = gantry.cellX() - 2; x <= gantry.cellX() + 2; x++) {
                    boolean perimeter = x == gantry.cellX() - 2
                            || x == gantry.cellX() + 2 || y == 3 || y == 10;
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
        float storageX = gantryX + 0.5f - side * 2.2f;
        float machineryX = gantryX + 0.5f + side * 2.2f;
        return new AmbientTaskRoute(name, phaseOffsetSeconds, 0.72f, 14f,
                AmbientThreatPolicy.ANY_COMBATANT, List.of(
                stop(weldX, 7.5f, 4.4f,
                        AmbientActivity.WORKING,
                        weldFocusX, 7.5f),
                stop(storageX, 11.5f, 3.2f,
                        AmbientActivity.INSPECTING,
                        storageX, 12.5f),
                stop(machineryX, 13.5f, 2.6f,
                        AmbientActivity.INSPECTING,
                        machineryX, 14.5f)));
    }

    private static AmbientTaskRoute logisticsJob(String name, float phase,
                                                  int firstGantry, int secondGantry,
                                                  float side) {
        float corridorX = firstGantry + 0.5f;
        return route(name, phase, 0.82f, List.of(
                stop(corridorX, 17.5f, 2.2f, AmbientActivity.IDLE,
                        corridorX + side, 17.5f),
                stop(corridorX, 15.2f, 0.35f, AmbientActivity.IDLE,
                        corridorX, 13.5f),
                stop(firstGantry + 0.5f - side * 2.4f, 12.2f, 3.1f,
                        AmbientActivity.INSPECTING, firstGantry + 0.5f, 13.5f),
                stop(firstGantry + 0.5f + side * 2.8f, 10.8f, 2.6f,
                        AmbientActivity.INSPECTING, firstGantry + 0.5f, 7.5f),
                stop(secondGantry + 0.5f - side * 2.8f, 10.8f, 2.6f,
                        AmbientActivity.INSPECTING, secondGantry + 0.5f, 7.5f),
                stop(corridorX, 15.2f, 0.35f, AmbientActivity.IDLE,
                        corridorX, 17.5f)));
    }

    private static AmbientTaskRoute inspectionJob(String name, float phase,
                                                   int firstGantry, int secondGantry) {
        return route(name, phase, 0.76f, List.of(
                stop(24.5f, 17.5f, 3.5f, AmbientActivity.INSPECTING, 24.5f, 18.5f),
                stop(24.5f, 15.2f, 0.35f, AmbientActivity.IDLE, 24.5f, 13.5f),
                stop(firstGantry + 3.2f, 13.4f, 3.2f,
                        AmbientActivity.INSPECTING, firstGantry + 0.5f, 13.5f),
                stop(firstGantry + 2.8f, 9.8f, 2.5f,
                        AmbientActivity.INSPECTING, firstGantry + 0.5f, 7.5f),
                stop(secondGantry - 2.8f, 9.8f, 2.5f,
                        AmbientActivity.INSPECTING, secondGantry + 0.5f, 7.5f),
                stop(secondGantry - 3.2f, 13.4f, 3.2f,
                        AmbientActivity.INSPECTING, secondGantry + 0.5f, 13.5f),
                stop(24.5f, 15.2f, 0.35f, AmbientActivity.IDLE, 24.5f, 17.5f)));
    }

    private static AmbientTaskRoute corridorJob(String name, float phase) {
        return route(name, phase, 0.68f, List.of(
                stop(20.5f, 17.5f, 4f, AmbientActivity.INSPECTING, 20.5f, 18.5f),
                stop(24.5f, 17.5f, 0.35f, AmbientActivity.IDLE, 24.5f, 15.2f),
                stop(24.5f, 15.2f, 3f, AmbientActivity.SOCIALIZING, 29.5f, 15.2f),
                stop(24.5f, 13.2f, 3f, AmbientActivity.INSPECTING, 24.5f, 14.5f),
                stop(24.5f, 15.2f, 0.35f, AmbientActivity.IDLE, 24.5f, 17.5f),
                stop(24.5f, 17.5f, 0.35f, AmbientActivity.IDLE, 28.5f, 17.5f),
                stop(28.5f, 17.5f, 4f, AmbientActivity.INSPECTING, 28.5f, 18.5f)));
    }

    private static AmbientTaskRoute route(String name, float phase, float speed,
                                          List<AmbientTaskRoute.Stop> stops) {
        return new AmbientTaskRoute(name, phase, speed, 14f,
                AmbientThreatPolicy.ANY_COMBATANT, stops);
    }

    private static AmbientTaskRoute.Stop stop(float x, float y, float dwell,
                                              AmbientActivity activity,
                                              float focusX, float focusY) {
        return new AmbientTaskRoute.Stop(x, y, dwell, activity, focusX, focusY);
    }

    private static boolean serviceDoor(int x) {
        for (Gantry gantry : GANTRIES) {
            if (x == gantry.cellX() || x == gantry.cellX() + 1) return true;
        }
        return x == WIDTH / 2;
    }

    private static List<AmbientTaskRoute> facilityJobs() {
        ArrayList<AmbientTaskRoute> result = new ArrayList<>(TECHNICIAN_JOBS);
        result.addAll(SUPPORT_JOBS);
        return List.copyOf(result);
    }

    private static PropPlacement prop(int x, int y, String doodadId) {
        return new PropPlacement(x, y, doodadId);
    }

    record Gantry(int cellX, int cellY) { }

    record FloorOverlayPlacement(int cellX, int cellY, int tileColumn, int tileRow) { }

    record PropPlacement(int cellX, int cellY, String doodadId) { }

}
