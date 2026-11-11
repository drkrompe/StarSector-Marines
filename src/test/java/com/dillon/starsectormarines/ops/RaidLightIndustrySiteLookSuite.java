package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.raid.LightIndustryWorks;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;

import java.util.List;

/**
 * The lightindustry Raid site, framed as {@link RaidSiteLookSuite} frames heavy industry's, its close-up on the
 * assembly hall and the yard round it, and a third shot of each case ({@code <case>-hall.png}): the assembly hall with
 * its roof off, to show the bays it builds the craft in.
 */
public final class RaidLightIndustrySiteLookSuite extends RaidSiteLookSuite {

    public RaidLightIndustrySiteLookSuite() {
        super("raid-light-industry-site-look", "lightindustry", EconomicFunction.HEAVY_INDUSTRY);
    }

    /** The assembly hall: the biggest building whose rooms are the assembly floor, or null. */
    private static Building hall(BattleSimulation sim) {
        Buildings buildings = sim.getBuildings();
        CellTopology topology = sim.getTopology();
        if (buildings == null) return null;
        Building best = null;
        for (Building b : buildings.all()) {
            int floor = 0;
            for (int i = 0; i < b.cellsX.length; i++) {
                if (topology.getRoomPurpose(b.cellsX[i], b.cellsY[i]) == LightIndustryWorks.ASSEMBLY) floor++;
            }
            if (floor * 2 > b.cellsX.length && (best == null || b.cellsX.length > best.cellsX.length)) best = b;
        }
        return best;
    }

    /**
     * The whole plant, at no less than play zoom: every building of the works within reach of the assembly hall
     * (receiving, the stores, the shops, the hall, finishing, dispatch), and the machines.
     */
    @Override protected float[] closeUp(BattleSimulation sim, float machinesX, float machinesY) {
        float[] base = super.closeUp(sim, machinesX, machinesY);
        Building hall = hall(sim);
        if (hall == null) return base;
        float x0 = Math.min(machinesX, hall.minX), x1 = Math.max(machinesX, hall.maxX + 1);
        float y0 = Math.min(machinesY, hall.minY), y1 = Math.max(machinesY, hall.maxY + 1);
        CellTopology topology = sim.getTopology();
        float reach = Math.max(hall.maxX - hall.minX, hall.maxY - hall.minY);
        for (Building b : sim.getBuildings().all()) {
            if (b.cellsX.length == 0) continue;
            RoomPurpose p = topology.getRoomPurpose(b.cellsX[0], b.cellsY[0]);
            if (p == null || !LightIndustryWorks.PROCESS.contains(p) && p != LightIndustryWorks.CANTEEN) continue;
            float cx = (b.minX + b.maxX) / 2f, cy = (b.minY + b.maxY) / 2f;
            float hx = (hall.minX + hall.maxX) / 2f, hy = (hall.minY + hall.maxY) / 2f;
            if (Math.abs(cx - hx) + Math.abs(cy - hy) > reach) continue;
            x0 = Math.min(x0, b.minX);
            y0 = Math.min(y0, b.minY);
            x1 = Math.max(x1, b.maxX + 1);
            y1 = Math.max(y1, b.maxY + 1);
        }
        float width = Math.max(base[2], Math.max(x1 - x0, (y1 - y0) * 16f / 9f) + 16f);
        return new float[]{(x0 + x1) / 2f, (y0 + y1) / 2f, width};
    }

    /** The assembly hall with its roof off, the hall filling the frame. */
    @Override protected void moreShots(SnapshotContext context, BattleSimulation sim, String caseId,
                                       List<SnapshotArtifact> out) throws Exception {
        Building hall = hall(sim);
        if (hall == null) return;
        float w = hall.maxX - hall.minX + 1, h = hall.maxY - hall.minY + 1;
        float width = Math.max(w, h * 16f / 9f) + 12f;
        out.add(new SnapshotArtifact(caseId + "-hall.png", BattleReviewFrameRenderer.look(
                context.modRoot(), 1920, 1080, hall.minX + w / 2f, hall.minY + h / 2f, width)
                .withoutRoofs().render(sim, "")));
    }
}
