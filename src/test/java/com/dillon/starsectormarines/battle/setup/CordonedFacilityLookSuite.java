package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.CordonedFacility;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The cordoned facility district ({@code set-piece-districts.md}) on Colony Breach colonies, one of each
 * character where the seeds have them: the facility at play zoom, the whole facility with its roofs on
 * and off, a gatehouse close and the courtyard. Look-preset stills. Opt-in.
 *
 * <p>The colony's map is generated once on its own to read its facility, which the battle does not keep, then
 * the battle is built from the same seed; a seed whose battle laid a different map is skipped.
 */
public final class CordonedFacilityLookSuite implements SnapshotSuite {

    static final int SEEDS_SCANNED = 24;
    static final int MAPS = 3;
    static final float GATE_CELLS = 16f;
    static final float COURT_CELLS = 22f;
    /** Cells of margin round the facility in its close frames. */
    static final int MARGIN = 6;

    @Override public String id() { return "cordoned-facility-look"; }

    @Override public String label() { return "Cordoned facility district on colony maps"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        MapScale scale = MapScale.COLONY;
        // Seeds with a facility, one of each character first, then the rest in seed order.
        List<Long> first = new ArrayList<>(), rest = new ArrayList<>();
        Map<Long, CordonedFacility> read = new LinkedHashMap<>();
        Set<CordonedFacility.Character> seen = EnumSet.noneOf(CordonedFacility.Character.class);
        for (long seed = 1; seed <= SEEDS_SCANNED; seed++) {
            MapResult map = new BspCityGenerator().generate(scale.width, scale.height, seed, null, null,
                    BattleSetup.colonyPlanFor(scale, seed));
            if (map.cordonedFacilities.isEmpty()) continue;
            CordonedFacility f = map.cordonedFacilities.get(0);
            read.put(seed, f);
            (seen.add(f.character()) ? first : rest).add(seed);
        }
        first.addAll(rest);
        List<SnapshotArtifact> out = new ArrayList<>();
        int maps = 0;
        for (long seed : first) {
            if (maps >= MAPS) break;
            CordonedFacility f = read.get(seed);
            try (BattleSimulation sim = BattleSetup.createCivilianRescue(seed, Collections.emptyList(), false,
                    RiskLevel.HIGH)) {
                if (!sameFacility(sim.getTopology(), f)) {
                    System.out.printf(Locale.ROOT, "[%s] seed %d: the battle laid another map%n", id(), seed);
                    continue;
                }
                maps++;
                System.out.printf(Locale.ROOT, "[%s] seed %d: %s%n", id(), seed, f);
                render(context, sim, seed, f, out);
            }
        }
        return out;
    }

    private static void render(SnapshotContext context, BattleSimulation sim, long seed, CordonedFacility f,
                               List<SnapshotArtifact> out) throws Exception {
        String stem = "seed" + seed;
        float cx = (f.left() + f.right() + 1) / 2f, cy = (f.top() + f.bottom() + 1) / 2f;
        float close = Math.max(f.right() - f.left(), (f.bottom() - f.top()) * 16f / 9f) + 2 * MARGIN;
        out.add(new SnapshotArtifact(stem + "-facility.png", BattleReviewFrameRenderer.look(context.modRoot(),
                1920, 1080, cx, cy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, stem)));
        out.add(new SnapshotArtifact(stem + "-whole.png", BattleReviewFrameRenderer.look(context.modRoot(),
                1920, 1080, cx, cy, close).render(sim, stem)));
        out.add(new SnapshotArtifact(stem + "-whole-open.png", BattleReviewFrameRenderer.look(context.modRoot(),
                1920, 1080, cx, cy, close).withoutRoofs().render(sim, stem)));
        if (!f.gatehouses().isEmpty()) {
            CordonedFacility.Gatehouse g = f.gatehouses().get(0);
            out.add(new SnapshotArtifact(stem + "-gatehouse.png", BattleReviewFrameRenderer.look(context.modRoot(),
                    1920, 1080, (g.outX() + g.inX() + 1) / 2f, (g.outY() + g.inY() + 1) / 2f, GATE_CELLS)
                    .render(sim, stem)));
        }
        if (f.courtyardLeft() >= 0) {
            out.add(new SnapshotArtifact(stem + "-courtyard.png", BattleReviewFrameRenderer.look(context.modRoot(),
                    1920, 1080, (f.courtyardLeft() + f.courtyardRight() + 1) / 2f,
                    (f.courtyardTop() + f.courtyardBottom() + 1) / 2f, COURT_CELLS).render(sim, stem)));
        }
    }

    /** Whether the battle's map is the one read: the courtyard is brick beside its middle, and its wall corner a wall. */
    private static boolean sameFacility(CellTopology battle, CordonedFacility f) {
        if (f.courtyardLeft() < 0) return false;
        int cx = (f.courtyardLeft() + f.courtyardRight()) / 2, cy = (f.courtyardTop() + f.courtyardBottom()) / 2;
        return battle.getGroundKind(cx + 1, cy + 1) == GroundKind.BRICK && battle.isWall(f.left(), f.top());
    }
}
