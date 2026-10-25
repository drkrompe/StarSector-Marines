package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.OpenSquare;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The market square set piece ({@code set-piece-blocks.md}) on Colony Breach colonies: the square, its
 * shopfronts, its stall rows and its mouths. One colony for each number of mouths found, look-preset stills
 * close, roofed and not, and at play zoom, then the colony twenty seconds on. Opt-in.
 */
public final class MarketSquareLookSuite implements SnapshotSuite {

    static final int SEEDS_SCANNED = 24;
    static final int MAPS = 4;
    static final float CLOSE_CELLS = 36f;
    static final float LIVE_SECONDS = 20f;

    @Override public String id() { return "market-square-look"; }

    @Override public String label() { return "Market square set piece on colony maps"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        Set<Integer> shown = new HashSet<>();
        for (long seed = 1; seed <= SEEDS_SCANNED && shown.size() < MAPS; seed++) {
            // The colony's first attempt generates exactly this map; a battle that had to retry is skipped below.
            MapResult map = new BspCityGenerator().generate(MapScale.COLONY.width, MapScale.COLONY.height, seed, null,
                    TargetProfile.NEUTRAL, BattleSetup.colonyPlanFor(MapScale.COLONY, seed));
            OpenSquare square = null;
            for (OpenSquare candidate : map.openSquares) {
                if (!shown.contains(candidate.mouths().size())) { square = candidate; break; }
            }
            if (square == null) continue;
            try (BattleSimulation sim = BattleSetup.createCivilianRescue(seed, Collections.emptyList(), false,
                    RiskLevel.HIGH)) {
                if (sim.getTopology().getGroundKind(square.left(), square.bottom())
                        != map.topology.getGroundKind(square.left(), square.bottom())
                        || sim.getGrid().getWidth() != map.grid.getWidth()) {
                    System.out.printf(Locale.ROOT, "[%s] seed %d: battle retried its map, skipped%n", id(), seed);
                    continue;
                }
                shown.add(square.mouths().size());
                float cx = square.centreX() + .5f, cy = square.centreY() + .5f;
                String stem = "seed" + seed + "-mouths" + square.mouths().size();
                System.out.printf(Locale.ROOT, "[%s] seed %d: square at %d,%d with %d mouths %s%n", id(), seed,
                        square.centreX(), square.centreY(), square.mouths().size(), square.mouths());
                out.add(new SnapshotArtifact(stem + "-square.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        1920, 1080, cx, cy, CLOSE_CELLS).withoutRoofs().render(sim, stem)));
                out.add(new SnapshotArtifact(stem + "-roofed.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        1920, 1080, cx, cy, CLOSE_CELLS).render(sim, stem)));
                out.add(new SnapshotArtifact(stem + "-play.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        1920, 1080, cx, cy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).withoutRoofs().render(sim, stem)));
                int ticks = Math.round(LIVE_SECONDS / BattleSimulation.TICK_DT);
                for (int t = 0; t < ticks; t++) sim.advance(BattleSimulation.TICK_DT);
                out.add(new SnapshotArtifact(stem + "-live.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        1920, 1080, cx, cy, CLOSE_CELLS).withoutRoofs().render(sim, stem)));
            }
        }
        return out;
    }
}
