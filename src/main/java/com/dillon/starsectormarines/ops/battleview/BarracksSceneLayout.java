package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ambient.AmbientActivity;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskRoute;
import com.dillon.starsectormarines.battle.ambient.AmbientThreatPolicy;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.List;

/** Shared authored habitation deck used by live and headless Barracks views. */
final class BarracksSceneLayout {

    static final int WIDTH = 32;
    static final int HEIGHT = 16;
    private static final int RANGE_WALL_X = 23;
    private static final int RANGE_DOOR_MIN_Y = 7;
    private static final int RANGE_DOOR_MAX_Y = 8;

    /** Real registry doodads also used by generated bedrooms and military rooms. */
    static final List<PropPlacement> PROPS = List.of(
            // Twelve two-cell berths: six against each long hull wall.
            prop(2, 13, "doodad.residential-bed-h"),
            prop(5, 13, "doodad.residential-bed-h"),
            prop(8, 13, "doodad.residential-bed-h"),
            prop(11, 13, "doodad.residential-bed-h"),
            prop(14, 13, "doodad.residential-bed-h"),
            prop(17, 13, "doodad.residential-bed-h"),
            prop(2, 1, "doodad.residential-bed-head-e"),
            prop(5, 1, "doodad.residential-bed-head-e"),
            prop(8, 1, "doodad.residential-bed-head-e"),
            prop(11, 1, "doodad.residential-bed-head-e"),
            prop(14, 1, "doodad.residential-bed-head-e"),
            prop(17, 1, "doodad.residential-bed-head-e"),

            // Shared lounge, lockers, planning surface, and shipboard terminals.
            prop(14, 9, "doodad.residential-sofa-h"),
            prop(18, 9, "doodad.residential-sofa-h"),
            prop(16, 6, "doodad.military-tactical-table"),
            prop(20, 12, "doodad.office-workstation-bank"),
            prop(20, 2, "doodad.office-server-rack"),
            prop(21, 5, "doodad.shelf-1"),
            prop(21, 10, "doodad.shelf-2"),
            prop(12, 6, "doodad.chest-1"),
            prop(12, 8, "doodad.chest-2"),
            prop(3, 7, "doodad.military-command-console"),
            prop(6, 7, "doodad.office-conference-table"),
            prop(10, 7, "doodad.residential-planter-h"),

            // Three-lane arms practice cell behind an internal blast wall.
            prop(25, 2, "doodad.industrial-control-console"),
            prop(28, 2, "doodad.industrial-cable-reel"),
            prop(25, 6, "doodad.sandbag-straight-s"),
            prop(27, 6, "doodad.sandbag-straight-s"),
            prop(29, 6, "doodad.sandbag-straight-s"),
            prop(25, 13, "doodad.crate"),
            prop(27, 13, "doodad.crate"),
            prop(29, 13, "doodad.crate"),
            prop(30, 9, "doodad.industrial-generator"));

    static final List<AmbientTaskRoute> MARINE_TASKS = marineTasks();

    private BarracksSceneLayout() { }

    static boolean wall(int x, int y) {
        if (x == 0 || x == WIDTH - 1 || y == 0 || y == HEIGHT - 1) return true;
        return x == RANGE_WALL_X && (y < RANGE_DOOR_MIN_Y || y > RANGE_DOOR_MAX_Y);
    }

    static CellTopology.GroundKind groundKind(int x, int y) {
        return CellTopology.GroundKind.INDOOR;
    }

    private static List<AmbientTaskRoute> marineTasks() {
        ArrayList<AmbientTaskRoute> result = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            boolean northBerth = index < 6;
            int berthIndex = index % 6;
            float berthX = 3.5f + berthIndex * 3f;
            float berthY = northBerth ? 12.4f : 3.6f;
            float berthFocusY = northBerth ? 14f : 1.5f;
            int lane = index % 3;
            float laneX = 25.5f + lane * 2f;
            float loungeX = 14.5f + (index % 4) * 1.7f;
            float loungeY = index % 2 == 0 ? 8.4f : 5.4f;
            float lockerY = index % 2 == 0 ? 10.5f : 5.5f;
            // Spread all twelve billets around their differently-sized loops;
            // this keeps the three-lane range reading as a rotation instead of
            // a crowd while the rest of the squad inhabits the commons.
            float phase = index * 8.1f;
            result.add(new AmbientTaskRoute(
                    "marine-leisure-" + (index + 1), phase, 0.78f, 0f,
                    AmbientThreatPolicy.NONE, List.of(
                    stop(berthX, berthY, 6.4f, AmbientActivity.RESTING,
                            berthX, berthFocusY),
                    stop(loungeX, loungeY, 5.2f, AmbientActivity.SOCIALIZING,
                            16.5f, 7.5f),
                    stop(20.4f, lockerY, 3.1f, AmbientActivity.INSPECTING,
                            21.5f, lockerY),
                    stop(22.3f, 7.5f, 0.35f, AmbientActivity.IDLE,
                            24.5f, 7.5f),
                    stop(laneX, 5.2f, 7.2f, AmbientActivity.FIRING_PRIMARY,
                            laneX, 13.5f),
                    stop(24.5f, 7.5f, 0.35f, AmbientActivity.IDLE,
                            22.3f, 7.5f))));
        }
        return List.copyOf(result);
    }

    private static AmbientTaskRoute.Stop stop(
            float x, float y, float dwell, AmbientActivity activity,
            float focusX, float focusY) {
        return new AmbientTaskRoute.Stop(x, y, dwell, activity, focusX, focusY);
    }

    private static PropPlacement prop(int x, int y, String doodadId) {
        return new PropPlacement(x, y, doodadId);
    }

    record PropPlacement(int cellX, int cellY, String doodadId) { }
}
