package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * The hover line scene's frames, from the same run that produced its verdicts: every loop at 1920x1080 and 100%
 * scale, whole map plus a full-size close view on the action. A scene's ground has no art, so open water is marked
 * cell by cell, and every hull is labelled. A handful of close frames per loop are also written as PNG stills.
 */
public final class HoverLineSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public HoverLineSceneSnapshotSuite() {
        super(new HoverLineScene(15), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected float[] closeView(String loopId) {
        return switch (loopId) {
            case HoverLineScene.RIVER -> new float[] {31f, (HoverLineScene.RIVER_Y0 + HoverLineScene.RIVER_Y1) / 2f, 44f};
            case HoverLineScene.SKIMMER, HoverLineScene.SKIMMER_LOSS, HoverLineScene.SENSORS, HoverLineScene.HALO ->
                    new float[] {25f, HoverLineScene.SKIMMER_Y, 36f};
            default -> new float[] {32f, HoverLineScene.ROAD_Y, 36f};
        };
    }

    @Override protected float closeViewScale() { return 1f; }

    @Override protected ReviewAnnotations annotations(BattleSimulation simulation) {
        List<ReviewAnnotation.Cell> water = new ArrayList<>();
        List<ReviewAnnotation.Cell> wall = new ArrayList<>();
        for (int y = 0; y < HoverLineScene.HEIGHT; y++) {
            for (int x = 0; x < HoverLineScene.WIDTH; x++) {
                if (simulation.getTopology().getGroundKind(x, y) == GroundKind.WATER) {
                    water.add(new ReviewAnnotation.Cell(x, y));
                } else if (x < HoverLineScene.HOLDOUT_X0 && !simulation.getGrid().isWalkable(x, y)) {
                    wall.add(new ReviewAnnotation.Cell(x, y));
                }
            }
        }
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        if (!water.isEmpty()) marks.cells(water, "RIVER", ReviewStyle.ROUTE);
        // A scene's wall has no art: mark the one the sensor pair looks past.
        if (!wall.isEmpty()) marks.cells(wall, "WALL", ReviewStyle.NOTE);
        for (long id : simulation.getConvoyVehicleIds()) {
            GroundBody body = simulation.convoy().body(id);
            VehicleType type = simulation.convoy().vehicleType(id);
            String name = switch (type) {
                case HOVER_SCOUT -> "HOVER SCOUT";
                case DRONE_SKIMMER -> "DRONE SKIMMER";
                case SCOUT_CAR -> "SPOTTER PICKUP";
                default -> "APC";
            };
            marks.label(body.x, body.y - 2.6f, name, ReviewStyle.NOTE);
        }
        return marks.build();
    }

    @Override protected int[] stillFrames(String loopId) {
        if (!loopId.endsWith("-close")) return new int[0];
        return new int[] {0, 6, 8, 10, 12, 14, 15, 20, 30, 45, 60, 79, 82, 90};
    }
}
