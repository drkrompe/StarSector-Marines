package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.fixture.CordonBattleFixture;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * The Road's drive at setup: the convoys' one road from the safe camp to the town's gate, drawn bold over the map,
 * whole and then in close stretches along it, with the camp, the gap and the gate named. Built at tick 0; no battle is
 * played. {@code -Dsnapshot.cordonRoute.seed} picks the town pool's ARID seed (Ain Tafrant's pinned 7 by default),
 * {@code -Dsnapshot.cordonRoute.stretches} how many close frames split the road (3).
 */
public final class CordonRouteSnapshotSuite implements SnapshotSuite {
    private static final int FRAME_W = 1920, FRAME_H = 1080;

    @Override public String id() { return "cordon-route"; }
    @Override public String label() { return "Cordon Road: the convoys' drive from the safe camp to the town gate"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        long seed = Long.getLong("snapshot.cordonRoute.seed", 7L);
        int stretches = Math.max(1, Integer.getInteger("snapshot.cordonRoute.stretches", 3));
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = new CordonBattleFixture(seed, CordonVariant.ROAD, SurfacePalette.ARID,
                CordonPlan.Density.TOWN, List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 4, 6))).build()) {
            CordonConvoys convoys = sim.getCordon().convoys();
            List<int[]> road = convoys.corridor();
            int from = Math.max(0, Math.min(convoys.entry(), road.size() - 1));
            ReviewAnnotations marks = marks(convoys, road, from, sim.getCordon().escalation());
            BattleReviewFrameRenderer whole = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H);
            String tag = "town-road-arid-seed" + seed + "-";
            out.add(new SnapshotArtifact(tag + "01-whole-map.png", whole.render(sim,
                    "Ain Tafrant, town road, arid seed " + seed + ": the convoys' drive, camp to town gate, "
                            + (road.size() - from) + " road cells", marks)));
            BattleReviewFrameRenderer near = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H, 0f, 0f, 1f);
            int span = road.size() - 1 - from;
            for (int i = 0; i < stretches; i++) {
                int a = from + span * i / stretches, b = from + span * (i + 1) / stretches;
                float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
                for (int c = a; c <= b; c++) {
                    minX = Math.min(minX, road.get(c)[0]);
                    maxX = Math.max(maxX, road.get(c)[0]);
                    minY = Math.min(minY, road.get(c)[1]);
                    maxY = Math.max(maxY, road.get(c)[1]);
                }
                // Frame the stretch with a margin, wide enough for its height at 16:9.
                float across = Math.max(maxX - minX, (maxY - minY) * FRAME_W / FRAME_H) + 40f;
                near.refocus((minX + maxX) / 2f + 0.5f, (minY + maxY) / 2f + 0.5f, across);
                out.add(new SnapshotArtifact(tag + String.format("02-stretch-%d-of-%d.png", i + 1, stretches),
                        near.render(sim, String.format("the drive, stretch %d of %d (road cells %d to %d), %.0f cells across",
                                i + 1, stretches, a - from, b - from, across), marks)));
            }
            CordonTown town = sim.getCordon().town();
            if (town != null) {
                // The town round the compound: its far side and east flank, then the alleys at play zoom.
                float front = sim.getCordon().town().centreY() > road.get(from)[1] ? 1f : -1f;
                float cx = town.right() + 18f, cy = town.centreY() + front * 18f;
                near.refocus(cx, cy, 170f);
                out.add(new SnapshotArtifact(tag + "03-town-blocks.png", near.render(sim,
                        "the cut-off town: the compound and the blocks past its far side and east flank, 170 cells across",
                        marks)));
                near.refocus(town.right() + 22f, town.centreY() + front * 22f, 64f);
                out.add(new SnapshotArtifact(tag + "04-town-alleys.png", near.render(sim,
                        "the alleys north-east of the compound at play zoom, 64 cells across", marks)));
            }
        }
        return out;
    }

    private static ReviewAnnotations marks(CordonConvoys convoys, List<int[]> road, int from,
                                           CordonBoreEscalation escalation) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        if (escalation != null) {
            for (CordonBoreEscalation.Site site : escalation.sites()) {
                if (site.town()) marks.label(site.x(), site.y(), "TOWN BORE " + site.order(), ReviewStyle.OBJECTIVE);
            }
        }
        List<ReviewAnnotation.Point> points = new ArrayList<>();
        for (int c = from; c < road.size(); c++) points.add(new ReviewAnnotation.Point(road.get(c)[0] + .5f, road.get(c)[1] + .5f));
        marks.polyline(points, "convoy drive", ReviewStyle.APPROACH);
        CordonCamp camp = convoys.camp();
        if (camp != null) {
            marks.label(camp.centreX(), camp.centreY(), "SAFE CAMP (drop-off)", ReviewStyle.LANDING);
        }
        int[] gap = road.get(Math.min(convoys.gapIndex(), road.size() - 1));
        marks.label(gap[0], gap[1], "THE GAP (crosses the line)", ReviewStyle.ALLY);
        int[] gate = road.get(road.size() - 1);
        marks.label(gate[0], gate[1], "TOWN GATE (pick-up)", ReviewStyle.KEEP);
        return marks.build();
    }
}
