package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.cordon.CordonBoreEscalation.Site;
import com.dillon.starsectormarines.battle.cordon.CordonBoreEscalation.Step;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
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
 * The Road's line bores as planned at setup ({@code cordon-nouns.md}, Line bores): every site along the line named
 * with its opening order and when it is due, the surge's sites apart, over the redoubts, the convoys' road, the camp
 * and the front's own bores. Built at tick 0; no battle is played. {@code -Dsnapshot.cordonLineBores.seed} picks the
 * town pool's ARID seed (Ain Tafrant's pinned 7 by default).
 */
public final class CordonLineBoresSnapshotSuite implements SnapshotSuite {
    private static final int FRAME_W = 1920, FRAME_H = 1080;
    /** Half the side of the square drawn round a site, in cells. */
    private static final int MARK = 2;
    /** Cells across the pull-out zone's close frame. */
    private static final int ZONE_ACROSS = 140;

    @Override public String id() { return "cordon-line-bores"; }
    @Override public String label() { return "Cordon Road: the bores planned along the line and by the town, in opening order, and the pull-out zone"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        long seed = Long.getLong("snapshot.cordonLineBores.seed", 7L);
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = new CordonBattleFixture(seed, CordonVariant.ROAD, SurfacePalette.ARID,
                CordonPlan.Density.TOWN, List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 4, 6))).build()) {
            CordonSequence cordon = sim.getCordon();
            CordonBoreEscalation escalation = cordon.escalation();
            List<Site> sites = escalation == null ? List.of() : escalation.sites();
            ReviewAnnotations marks = marks(sim, cordon, sites);
            String tag = "town-road-arid-seed" + seed + "-";
            BattleReviewFrameRenderer whole = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H);
            out.add(new SnapshotArtifact(tag + "01-whole-map.png", whole.render(sim,
                    "Ain Tafrant, town road, arid seed " + seed + ": " + sites.size()
                            + " bores planned (line and town), numbered in opening order, and the pull-out zone", marks)));
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (CordonRedoubt r : cordon.redoubts()) {
                minX = Math.min(minX, r.left());
                maxX = Math.max(maxX, r.right());
                minY = Math.min(minY, r.top());
                maxY = Math.max(maxY, r.bottom());
            }
            for (Site s : sites) {
                minX = Math.min(minX, s.x());
                maxX = Math.max(maxX, s.x());
                minY = Math.min(minY, s.y());
                maxY = Math.max(maxY, s.y());
            }
            BattleReviewFrameRenderer near = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H, 0f, 0f, 1f);
            // The line runs edge to edge, so the whole map is the whole line: each half closer, so the labels read.
            float half = (minX + maxX) / 2f;
            float[][] halves = {{minX, half}, {half, maxX}};
            for (int h = 0; h < 2; h++) {
                float a = halves[h][0], b = halves[h][1];
                float span = Math.max(b - a, (maxY - minY) * FRAME_W / FRAME_H) + 40f;
                near.refocus((a + b) / 2f + 0.5f, (minY + maxY) / 2f + 0.5f, span);
                out.add(new SnapshotArtifact(tag + (h == 0 ? "02-west-half.png" : "03-east-half.png"),
                        near.render(sim, String.format("the line's %s half, %.0f cells across",
                                h == 0 ? "west" : "east", span), marks)));
            }
            CordonTown town = cordon.town();
            if (town != null) {
                float tx0 = town.left(), tx1 = town.right(), ty0 = town.top(), ty1 = town.bottom();
                for (Site s : sites) {
                    if (!s.town()) continue;
                    tx0 = Math.min(tx0, s.x());
                    tx1 = Math.max(tx1, s.x());
                    ty0 = Math.min(ty0, s.y());
                    ty1 = Math.max(ty1, s.y());
                }
                float span = Math.max(tx1 - tx0, (ty1 - ty0) * FRAME_W / FRAME_H) + 40f;
                near.refocus((tx0 + tx1) / 2f + 0.5f, (ty0 + ty1) / 2f + 0.5f, span);
                out.add(new SnapshotArtifact(tag + "04-town-bores.png", near.render(sim,
                        String.format("the surge's bores by the town, %.0f cells across", span), marks)));
            }
            CordonPullOut zone = cordon.pullOut();
            if (zone != null) {
                near.refocus(zone.x() + 0.5f, zone.y() + 0.5f, ZONE_ACROSS);
                out.add(new SnapshotArtifact(tag + "05-pull-out-zone.png", near.render(sim,
                        String.format("the pull-out zone at (%d, %d), %d cells across: the trigger point behind the line",
                                zone.x(), zone.y(), ZONE_ACROSS), marks)));
            }
        }
        return out;
    }

    private static ReviewAnnotations marks(BattleSimulation sim, CordonSequence cordon, List<Site> sites) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        CordonConvoys convoys = cordon.convoys();
        if (convoys != null) {
            List<int[]> road = convoys.corridor();
            List<ReviewAnnotation.Point> points = new ArrayList<>();
            for (int c = Math.max(0, convoys.entry()); c < road.size(); c++) {
                points.add(new ReviewAnnotation.Point(road.get(c)[0] + .5f, road.get(c)[1] + .5f));
            }
            marks.polyline(points, "convoy road", ReviewStyle.APPROACH);
            if (convoys.camp() != null) {
                marks.label(convoys.camp().centreX(), convoys.camp().centreY(), "SAFE CAMP", ReviewStyle.LANDING);
            }
        }
        CordonPullOut zone = cordon.pullOut();
        if (zone != null) {
            List<ReviewAnnotation.Cell> cells = new ArrayList<>();
            for (int[] c : zone.cells()) cells.add(new ReviewAnnotation.Cell(c[0], c[1]));
            marks.cells(cells, "", ReviewStyle.LANDING);
            marks.label(zone.x(), zone.y() + CordonPullOut.RADIUS + 3,
                    "PULL-OUT ZONE (order a squad here; hold it to finish)", ReviewStyle.LANDING);
        }
        for (CordonRedoubt r : cordon.redoubts()) {
            marks.label(r.centreX(), r.centreY(), "REDOUBT " + r.number()
                    + (cordon.gap().contains(r.number() - 1) ? " (gap)" : ""), ReviewStyle.ALLY);
        }
        for (SwarmBore b : sim.swarmBores().bores()) {
            marks.label(b.x(), b.y(), "front bore " + b.number(), ReviewStyle.NOTE);
        }
        List<Step> schedule = CordonBoreEscalation.STANDARD;
        for (Site s : sites) {
            List<ReviewAnnotation.Cell> cells = new ArrayList<>();
            for (int dy = -MARK; dy <= MARK; dy++) {
                for (int dx = -MARK; dx <= MARK; dx++) cells.add(new ReviewAnnotation.Cell(s.x() + dx, s.y() + dy));
            }
            String when;
            if (s.surge()) {
                when = "final surge";
            } else {
                Step step = schedule.get(s.order() - 1);
                when = String.format("%d:%02d or convoy %d home or %d%% out", step.seconds() / 60, step.seconds() % 60,
                        step.tripsHome(), Math.round(step.peopleShare() * 100));
            }
            String where = switch (s.kind()) {
                case BETWEEN -> "R" + s.west() + "-R" + s.east();
                case FRONT -> "before R" + s.west();
                case TOWN_BEYOND -> "beyond the town";
                case TOWN_FLANK -> "town's far flank";
            };
            marks.cells(cells, "", s.surge() ? ReviewStyle.OBJECTIVE : ReviewStyle.KEEP);
            marks.label(s.x(), s.y() - 4, (s.town() ? "TOWN BORE " : "LINE BORE ") + s.order() + " (" + where + "): " + when,
                    s.surge() ? ReviewStyle.OBJECTIVE : ReviewStyle.KEEP);
        }
        return marks.build();
    }
}
