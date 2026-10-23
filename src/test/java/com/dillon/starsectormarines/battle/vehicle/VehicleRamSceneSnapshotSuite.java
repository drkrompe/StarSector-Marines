package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * The vehicle ram and hull-contact loops, from the same run that produced their verdicts. A scene's walls have no
 * art, so the cells they stand on are outlined; the close view is on the stretch of road where the hulls meet what
 * is in it, at the full 1920x1080.
 */
public final class VehicleRamSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public VehicleRamSceneSnapshotSuite() {
        super(new VehicleRamScene(), 1920, 1080);
    }

    @Override
    protected ReviewAnnotations annotations(BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        List<ReviewAnnotation.Cell> walls = new ArrayList<>();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!grid.isWalkable(x, y)) walls.add(new ReviewAnnotation.Cell(x, y));
            }
        }
        return walls.isEmpty() ? ReviewAnnotations.NONE
                : ReviewAnnotations.builder().cells(walls, "wall", ReviewStyle.NOTE).build();
    }

    @Override
    protected float[] closeView(String loopId) {
        return new float[]{33f, 13f, 30f};
    }

    @Override
    protected float closeViewScale() {
        return 1f;
    }
}
