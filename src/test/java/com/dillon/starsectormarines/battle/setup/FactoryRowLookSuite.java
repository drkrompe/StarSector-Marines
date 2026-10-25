package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ServiceLane;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The factory row set piece ({@code set-piece-blocks.md}) on Colony Breach colonies: the workshops' blank
 * backs to the street, the service lane with a gate at each end, the loading doors onto it and the cover
 * in pockets across it. Look-preset stills at close range, roofed and not, and at play zoom. Opt-in.
 *
 * <p>The colony's map is generated once on its own to read its service lane, which the battle does not
 * keep, then the battle is built from the same seed; a seed whose battle laid a different map is skipped.
 */
public final class FactoryRowLookSuite implements SnapshotSuite {

    static final int SEEDS_SCANNED = 24;
    static final int MAPS = 3;
    static final float CLOSE_CELLS = 30f;

    @Override public String id() { return "factory-row-look"; }

    @Override public String label() { return "Factory row set piece on colony maps"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        int maps = 0;
        MapScale scale = MapScale.COLONY;
        for (long seed = 1; seed <= SEEDS_SCANNED && maps < MAPS; seed++) {
            MapResult map = new BspCityGenerator().generate(scale.width, scale.height, seed, null, null,
                    BattleSetup.colonyPlanFor(scale, seed));
            if (map.serviceLanes.isEmpty()) {
                System.out.printf(Locale.ROOT, "[%s] seed %d: no factory row%n", id(), seed);
                continue;
            }
            ServiceLane lane = map.serviceLanes.get(0);
            try (BattleSimulation sim = BattleSetup.createCivilianRescue(seed, Collections.emptyList(), false,
                    RiskLevel.HIGH)) {
                if (!sameRow(sim.getTopology(), lane)) {
                    System.out.printf(Locale.ROOT, "[%s] seed %d: the battle laid another map%n", id(), seed);
                    continue;
                }
                maps++;
                float cx = (lane.startX() + lane.endX()) / 2f, cy = (lane.startY() + lane.endY()) / 2f;
                System.out.printf(Locale.ROOT, "[%s] seed %d: %s%n", id(), seed, lane);
                String stem = "seed" + seed;
                out.add(new SnapshotArtifact(stem + "-row.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920,
                        1080, cx, cy, CLOSE_CELLS).withoutRoofs().render(sim, stem)));
                out.add(new SnapshotArtifact(stem + "-row-roofed.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        1920, 1080, cx, cy, CLOSE_CELLS).render(sim, stem)));
                out.add(new SnapshotArtifact(stem + "-play.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920,
                        1080, cx, cy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).withoutRoofs().render(sim, stem)));
            }
        }
        return out;
    }

    /** Whether the battle's map is the one read: its ground is striped in both of the lane's gates. */
    private static boolean sameRow(CellTopology battle, ServiceLane lane) {
        return battle.getGroundKind(lane.gateAX(), lane.gateAY()) == GroundKind.STRIPED
                && battle.getGroundKind(lane.gateBX(), lane.gateBY()) == GroundKind.STRIPED;
    }
}
