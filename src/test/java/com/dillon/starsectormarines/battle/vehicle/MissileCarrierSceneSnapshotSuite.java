package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * The missile-carrier scene's frames, from the same run that produced its verdicts: all three loops at 1920x1080 and
 * 100% scale, whole map plus a full-size close view (the LRM carrier's on the carrier itself). Scene walls have no art, so the wall the LRM carrier fires over
 * and the building the SRM technical hides behind are drawn as marks, and each hull is labelled where it starts.
 *
 * <p>Those loops are verdict evidence. The owner is shown {@code look/lrm-salvo-*}: the carrier mid-salvo on real
 * generated ground through the look preset ({@link MissileCarrierScene#lrmSalvoLook()}).
 */
public final class MissileCarrierSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public MissileCarrierSceneSnapshotSuite() {
        super(new MissileCarrierScene(15), WIDTH, HEIGHT);
    }

    @Override protected float[] closeView(String loopId) {
        if (loopId.startsWith(MissileCarrierScene.SRM_AMBUSH)) {
            // 64 cells across holds the technical, the building and the stretch of road it covers.
            return new float[] {40f, 27f, 64f};
        }
        // The whole map already holds the infantry, the scout, the wall and the tank; the close view is the carrier
        // itself, 24 cells across, so its launcher box and the salvo leaving its tubes read.
        return new float[] {MissileCarrierScene.CARRIER_X + 4f, MissileCarrierScene.CARRIER_Y, 24f};
    }

    @Override protected float closeViewScale() { return 1f; }

    @Override protected List<LookReview.Shot> lookShots() {
        return List.of(MissileCarrierScene.lrmSalvoLook());
    }

    @Override protected ReviewAnnotations annotations(BattleSimulation simulation) {
        NavigationGrid grid = simulation.getGrid();
        boolean srm = false;
        boolean scout = false;
        for (long id : simulation.getConvoyVehicleIds()) {
            VehicleType type = simulation.convoy().vehicleType(id);
            if (type == VehicleType.SRM_TECHNICAL) srm = true;
            if (type == VehicleType.SCOUT_CAR) scout = true;
        }
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        if (srm) {
            marks.box(MissileCarrierScene.BLD_X0, MissileCarrierScene.BLD_Y0, MissileCarrierScene.BLD_X1,
                            MissileCarrierScene.BLD_Y1, "BUILDING (COVER)", ReviewStyle.NOTE)
                    .box(4, MissileCarrierScene.ROAD_Y - 1, 102, MissileCarrierScene.ROAD_Y + 1, "ROAD",
                            ReviewStyle.KEEP)
                    .label(MissileCarrierScene.TECH_X, MissileCarrierScene.TECH_Y - 3f, "SRM TECHNICAL",
                            ReviewStyle.LANDING)
                    .label(MissileCarrierScene.SRM_TANK_START_X - 4f, MissileCarrierScene.ROAD_Y + 4f,
                            "TANK (DRIVING WEST)", ReviewStyle.OBJECTIVE)
                    .label(MissileCarrierScene.SRM_MEN_X + 2f, MissileCarrierScene.SRM_MEN_Y - 3f,
                            "INFANTRY", ReviewStyle.NOTE);
        } else {
            List<ReviewAnnotation.Cell> wall = new ArrayList<>();
            for (int y = MissileCarrierScene.LRM_WALL_Y0; y <= MissileCarrierScene.LRM_WALL_Y1; y++) {
                if (grid.isWalkable(MissileCarrierScene.LRM_WALL_X, y)) continue;
                wall.add(new ReviewAnnotation.Cell(MissileCarrierScene.LRM_WALL_X, y));
            }
            marks.cells(wall, "WALL", ReviewStyle.NOTE)
                    .label(MissileCarrierScene.CARRIER_X, MissileCarrierScene.CARRIER_Y - 3f, "LRM CARRIER",
                            ReviewStyle.LANDING)
                    .label(MissileCarrierScene.LRM_TANK_X, MissileCarrierScene.LRM_TANK_Y - 3f, "TANK",
                            ReviewStyle.OBJECTIVE)
                    .label(MissileCarrierScene.LRM_MEN_X + 2f, MissileCarrierScene.LRM_MEN_Y + 3f, "INFANTRY",
                            ReviewStyle.NOTE);
            if (scout) {
                marks.label(MissileCarrierScene.SCOUT_X, MissileCarrierScene.SCOUT_Y - 3f, "SCOUT CAR",
                        ReviewStyle.LANDING);
            }
        }
        return marks.build();
    }

    @Override protected int[] stillFrames(String loopId) {
        if (!loopId.endsWith("-close")) return new int[0];
        int[] all = new int[10];
        for (int i = 0; i < all.length; i++) all[i] = i * 10;
        return all;
    }
}
