package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.objective.optional.ObjectiveBoard;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.staffed.Loading;
import com.dillon.starsectormarines.battle.staffed.LoadingObjective;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
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
 * The freight yard set piece ({@code set-piece-blocks.md}) on Colony Breach colonies: the walled yard,
 * its gate onto the street, the hauler standing in the bay and the colony's crew loading it. Look-preset
 * stills at close range, roofed and not, and at play zoom, then the loading twenty seconds on. Opt-in.
 */
public final class FreightYardLookSuite implements SnapshotSuite {

    static final int SEEDS_SCANNED = 24;
    static final int MAPS = 3;
    static final float CLOSE_CELLS = 30f;
    static final float LOADING_SECONDS = 20f;

    @Override public String id() { return "freight-yard-look"; }

    @Override public String label() { return "Freight yard set piece on colony maps"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        int maps = 0;
        for (long seed = 1; seed <= SEEDS_SCANNED && maps < MAPS; seed++) {
            try (BattleSimulation sim = BattleSetup.createCivilianRescue(seed, Collections.emptyList(), false,
                    RiskLevel.HIGH)) {
                ObjectiveBoard board = sim.getOptionalObjectives();
                Loading loading = board != null && board.runtime(LoadingObjective.KEY) instanceof LoadingObjective o
                        ? o.loading() : null;
                System.out.printf(Locale.ROOT, "[%s] seed %d: %s%n", id(), seed,
                        loading == null ? "no loading" : loading.atBay() ? "truck in a yard's bay" : "truck parked by search");
                if (loading == null || !loading.atBay()) continue;
                GroundBody truck = sim.convoy().body(loading.truck());
                PointOfInterest hall = loading.building().building();
                maps++;
                float cx = (truck.x + (hall.left + hall.right) / 2f + .5f) / 2f;
                float cy = (truck.y + (hall.top + hall.bottom) / 2f + .5f) / 2f;
                String stem = "seed" + seed;
                out.add(new SnapshotArtifact(stem + "-yard.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920,
                        1080, cx, cy, CLOSE_CELLS).withoutRoofs().render(sim, stem)));
                out.add(new SnapshotArtifact(stem + "-yard-roofed.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        1920, 1080, cx, cy, CLOSE_CELLS).render(sim, stem)));
                out.add(new SnapshotArtifact(stem + "-play.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920,
                        1080, cx, cy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).withoutRoofs().render(sim, stem)));
                int ticks = Math.round(LOADING_SECONDS / BattleSimulation.TICK_DT);
                for (int t = 0; t < ticks; t++) sim.advance(BattleSimulation.TICK_DT);
                out.add(new SnapshotArtifact(stem + "-loading.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        1920, 1080, cx, cy, CLOSE_CELLS).withoutRoofs().render(sim, stem)));
                System.out.printf(Locale.ROOT, "[%s] seed %d: %d/%d aboard after %.0f s%n", id(), seed,
                        loading.delivered(), loading.target(), LOADING_SECONDS);
            }
        }
        return out;
    }
}
