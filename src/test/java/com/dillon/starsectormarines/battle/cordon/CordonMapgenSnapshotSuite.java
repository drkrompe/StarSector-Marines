package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
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
 * {@code -Dsnapshot.cordonMapgen.variants=line,road} the variants, and
 * {@code -Dsnapshot.cordonMapgen.density=town} how built-up the country is
 * ({@link CordonPlan.Density}; rural, the default, plays the pools).
 */
public final class CordonMapgenSnapshotSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    /** 1920 px over sixty cells: 32 px a cell, the play zoom. */
    private static final float PLAY_ZOOM_CELLS = 60f;

    @Override public String id() { return "cordon-mapgen"; }

    @Override public String label() { return "Cordon mapgen: the line, the front and the rear"; }

    /** What a whole-map frame names: the same marks a played Cordon frame draws ({@link CordonReviewAnnotations}). */
    public static ReviewAnnotations annotations(BattleSimulation sim) {
        return CordonReviewAnnotations.forBattle(sim);
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer whole = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H);
        BattleReviewFrameRenderer near = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H, 0f, 0f, 1f);
        List<SnapshotArtifact> out = new ArrayList<>();
        CordonPlan.Density density = CordonPlan.Density.valueOf(
                System.getProperty("snapshot.cordonMapgen.density", "rural").trim().toUpperCase());
        String built = density == CordonPlan.Density.RURAL ? "" : density.name().toLowerCase() + "-";
        boolean look = Boolean.getBoolean("snapshot.cordonMapgen.look");
        for (String v : System.getProperty("snapshot.cordonMapgen.variants", "line,road").split(",")) {
            CordonVariant variant = CordonVariant.valueOf(v.trim().toUpperCase());
            String name = variant.name().toLowerCase();
            for (String token : System.getProperty("snapshot.cordonMapgen.seeds", "7117,3,11").split(",")) {
                long seed = Long.parseLong(token.trim());
                String tag = built + name + "-seed" + seed + "-";
                CordonSetup.CordonMap cordon = density == CordonPlan.Density.RURAL
                        ? CordonSetup.map(variant, SurfacePalette.VERDANT, seed)
                        : CordonSetup.rolled(variant, density, SurfacePalette.VERDANT, seed);
                PrecinctPlan plan = cordon.plan();
                try (BattleSimulation sim = CordonSetup.create(cordon, variant, List.of(),
                        CordonSequence.Timetable.STANDARD)) {
                    if (look) {
                        out.addAll(look(context, sim, plan, tag));
                        continue;
                    }
                    out.add(new SnapshotArtifact(tag + "01-whole-map.png",
                            whole.render(sim, "the " + name + (built.isEmpty() ? "" : ", " + density.name().toLowerCase()) + ", seed " + seed + ", whole map", annotations(sim))));
                    List<Precinct> redoubts = CordonPlan.redoubts(plan);
                    float lineY = 0f;
                    for (Precinct r : redoubts) lineY += r.seedY();
                    lineY /= Math.max(1, redoubts.size());
                    near.refocus(CordonSetup.WIDTH / 2f, lineY, 300f);
                    out.add(new SnapshotArtifact(tag + "02-the-line.png",
                            near.render(sim, "the " + name + ", seed " + seed + ", the line, 300 cells across",
                                    annotations(sim))));
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
                        } else if (p.name().equals(CordonPlan.BASE_PLACE)) {
                            // On the shed: the middle of its berths, where there are any.
                            float fx = p.seedX() + 0.5f, fy = p.seedY() + 0.5f;
                            if (!cordon.map().gantries.isEmpty()) {
                                fx = 0f;
                                fy = 0f;
                                for (Gantry g : cordon.map().gantries) {
                                    fx += g.worldCenterX();
                                    fy += g.worldCenterY();
                                }
                                fx /= cordon.map().gantries.size();
                                fy /= cordon.map().gantries.size();
                            }
                            near.refocus(fx, fy, PLAY_ZOOM_CELLS);
                            out.add(new SnapshotArtifact(tag + "08-play-zoom-logistics-base.png",
                                    near.render(sim, "the " + name + ", seed " + seed + ", the logistics base ("
                                            + cordon.map().gantries.size() + " berths), play zoom", annotations(sim))));
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

    /** Cells across the close look at a redoubt: the redoubt, the crest either side and the ground in front. */
    private static final float REDOUBT_CLOSE_CELLS = 90f;

    /**
     * The look preset's frames ({@code -Dsnapshot.cordonMapgen.look=true}): the whole map and redoubt 2 close and at
     * play zoom, on the real ground with nothing drawn over it, for the owner rather than for a verdict.
     */
    private static List<SnapshotArtifact> look(SnapshotContext context, BattleSimulation sim, PrecinctPlan plan,
                                               String tag) throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        BattleReviewFrameRenderer renderer = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                CordonSetup.WIDTH / 2f, CordonSetup.HEIGHT / 2f, CordonSetup.WIDTH);
        out.add(new SnapshotArtifact("look/" + tag + "01-whole-map.png", renderer.render(sim, "")));
        Precinct second = CordonPlan.redoubts(plan).get(1);
        renderer.refocus(second.seedX() + 0.5f, second.seedY() + 0.5f, REDOUBT_CLOSE_CELLS);
        out.add(new SnapshotArtifact("look/" + tag + "02-redoubt-2-close.png", renderer.render(sim, "")));
        renderer.refocus(second.seedX() + 0.5f, second.seedY() + 0.5f, PLAY_ZOOM_CELLS);
        out.add(new SnapshotArtifact("look/" + tag + "03-redoubt-2-play-zoom.png", renderer.render(sim, "")));
        return out;
    }
}
