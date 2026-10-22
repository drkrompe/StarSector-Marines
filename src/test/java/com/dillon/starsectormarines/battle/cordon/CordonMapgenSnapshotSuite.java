package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * The Cordon's map as generated, both variants ({@code cordon-nouns.md}, The
 * map): the whole map, the line across it, and play zoom on a redoubt, the
 * front and the rear. Look evidence, 1920x1080.
 *
 * <p>{@code -Dsnapshot.cordonMapgen.seeds=7117,3,11} picks the seeds and
 * {@code -Dsnapshot.cordonMapgen.variants=line,road} the variants.
 */
public final class CordonMapgenSnapshotSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    /** 1920 px over sixty cells: 32 px a cell, the play zoom. */
    private static final float PLAY_ZOOM_CELLS = 60f;

    @Override public String id() { return "cordon-mapgen"; }

    @Override public String label() { return "Cordon mapgen: the line, the front and the rear"; }

    /** What a whole-map frame names: each redoubt, the places, the bores and the Road's corridor. */
    static ReviewAnnotations annotations(BattleSimulation sim, CordonSetup.CordonMap cordon) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        List<Precinct> redoubts = CordonPlan.redoubts(cordon.plan());
        for (int i = 0; i < redoubts.size(); i++) {
            Precinct r = redoubts.get(i);
            marks.box(r.seedX() - 16, r.seedY() - 16, r.seedX() + 16, r.seedY() + 16, "redoubt " + (i + 1), ReviewStyle.KEEP);
        }
        for (Precinct p : cordon.plan().precincts()) {
            String text = switch (p.name()) {
                case "colony" -> "the colony";
                case "installation" -> "installation";
                case "suburb" -> "suburb";
                case "rear-west", "rear-east" -> "rear hamlet";
                case CivilDefenceCompound.PLACE -> "the town's compound";
                default -> null;
            };
            if (text != null) marks.label(p.seedX(), p.seedY(), text, ReviewStyle.NOTE);
        }
        for (SwarmBore bore : sim.swarmBores().bores()) {
            marks.label(bore.x(), bore.y(), "bore " + bore.number() + " (" + bore.tier().name().toLowerCase() + ")", ReviewStyle.OBJECTIVE);
        }
        if (cordon.corridor() != null) {
            List<ReviewAnnotation.Point> points = new ArrayList<>();
            for (int[] cell : cordon.corridor()) points.add(new ReviewAnnotation.Point(cell[0] + 0.5f, cell[1] + 0.5f));
            marks.polyline(points, String.format("corridor, %.0f%% on road", 100f * CordonCorridor.pavedShare(sim.getTopology(), cordon.corridor())), ReviewStyle.ROUTE);
        }
        return marks.build();
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer whole = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H);
        BattleReviewFrameRenderer near = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H, 0f, 0f, 1f);
        List<SnapshotArtifact> out = new ArrayList<>();
        for (String v : System.getProperty("snapshot.cordonMapgen.variants", "line,road").split(",")) {
            CordonVariant variant = CordonVariant.valueOf(v.trim().toUpperCase());
            String name = variant.name().toLowerCase();
            for (String token : System.getProperty("snapshot.cordonMapgen.seeds", "7117,3,11").split(",")) {
                long seed = Long.parseLong(token.trim());
                String tag = name + "-seed" + seed + "-";
                CordonSetup.CordonMap cordon = CordonSetup.map(variant, seed, TargetProfile.NEUTRAL);
                PrecinctPlan plan = cordon.plan();
                try (BattleSimulation sim = CordonSetup.create(cordon, variant, List.of(), TargetProfile.NEUTRAL,
                        CordonSequence.Timetable.STANDARD)) {
                    out.add(new SnapshotArtifact(tag + "01-whole-map.png",
                            whole.render(sim, "the " + name + ", seed " + seed + ", whole map", annotations(sim, cordon))));
                    List<Precinct> redoubts = CordonPlan.redoubts(plan);
                    float lineY = 0f;
                    for (Precinct r : redoubts) lineY += r.seedY();
                    lineY /= Math.max(1, redoubts.size());
                    near.refocus(CordonSetup.WIDTH / 2f, lineY, 300f);
                    out.add(new SnapshotArtifact(tag + "02-the-line.png",
                            near.render(sim, "the " + name + ", seed " + seed + ", the line, 300 cells across")));
                    if (cordon.corridor() != null) {
                        int[] cross = cordon.corridor().get(0);
                        for (int[] cell : cordon.corridor()) {
                            if (Math.abs(cell[1] - lineY) < Math.abs(cross[1] - lineY)) cross = cell;
                        }
                        near.refocus(cross[0] + 0.5f, cross[1] + 0.5f, 120f);
                        out.add(new SnapshotArtifact(tag + "07-corridor-at-the-line.png",
                                near.render(sim, "the road, seed " + seed + ", where the corridor crosses the line, 120 cells")));
                    }
                    Precinct second = redoubts.get(1);
                    near.refocus(second.seedX() + 0.5f, second.seedY() + 0.5f, PLAY_ZOOM_CELLS);
                    out.add(new SnapshotArtifact(tag + "03-play-zoom-redoubt-2.png",
                            near.render(sim, "the " + name + ", seed " + seed + ", redoubt 2, play zoom")));
                    for (Precinct p : plan.precincts()) {
                        if (p.name().equals("colony")) {
                            near.refocus(p.seedX() + 0.5f, p.seedY() + 0.5f, 120f);
                            out.add(new SnapshotArtifact(tag + "04-the-colony.png",
                                    near.render(sim, "the " + name + ", seed " + seed + ", the colony in front, 120 cells")));
                        } else if (p.name().equals(CivilDefenceCompound.PLACE)) {
                            near.refocus(p.seedX() + 0.5f, p.seedY() + 0.5f, PLAY_ZOOM_CELLS);
                            out.add(new SnapshotArtifact(tag + "05-play-zoom-compound.png",
                                    near.render(sim, "the " + name + ", seed " + seed + ", the town's compound, play zoom")));
                        } else if (p.name().equals("rear-west")) {
                            near.refocus(p.seedX() + 0.5f, p.seedY() + 0.5f, 120f);
                            out.add(new SnapshotArtifact(tag + "06-rear-hamlet.png",
                                    near.render(sim, "the " + name + ", seed " + seed + ", a rear hamlet, 120 cells")));
                        }
                    }
                }
            }
        }
        return out;
    }
}
