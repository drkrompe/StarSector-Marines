package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the Cordon's redoubts and logistics base are built from, as production builds them
 * ({@code cordon-nouns.md}, Field redoubt, Logistics base; {@link CordonConstructions}): each pooled town and urban
 * Cordon map stood up as the mission stands it up, played a few seconds so its garrison is at its posts and its
 * gantries at work, and drawn through the look preset (real ground, shadows, roofs, no annotations).
 *
 * <p>Per map, {@code <density>-seed<N>-r<k>-<construction>.png} frames each redoubt at play zoom,
 * {@code <density>-seed<N>-mix.png} sets the four side by side (each quarter of one 1920x1080 frame over the
 * play-zoom frame's cells, so at half its scale; west to east, top row first), and on a city map {@code <density>-seed<N>-base-<construction>.png} frames the base
 * a little wider than play zoom, since it is wider than one. {@code -Dsnapshot.cordonStructure.town=8,2,10} and
 * {@code .urban=6,1,10} pick the pooled seeds (the Line's verdant pool by default), {@code .ticks} how long each map
 * is played first (150). Opt-in: a look review, not part of {@code all}.
 */
public final class CordonStructureLookSuite implements SnapshotSuite {

    static final int W = LookReview.WIDTH, H = LookReview.HEIGHT;
    static final float PLAY_CELLS = BattleReviewFrameRenderer.PLAY_ZOOM_CELLS;
    /** Cells across a base's frame: the fence is some fifty cells wide. */
    static final float BASE_CELLS = 72f;

    @Override public String id() { return "cordon-structure-look"; }

    @Override public String label() { return "Cordon: what the redoubts and the logistics base are built from"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        int ticks = Integer.getInteger("snapshot.cordonStructure.ticks", 150);
        List<SnapshotArtifact> out = new ArrayList<>();
        for (CordonPlan.Density density : List.of(CordonPlan.Density.TOWN, CordonPlan.Density.URBAN)) {
            String key = density.name().toLowerCase(Locale.ROOT);
            String seeds = System.getProperty("snapshot.cordonStructure." + key,
                    density == CordonPlan.Density.TOWN ? "8,2,10" : "6,1,10").trim();
            if (seeds.isEmpty()) continue;
            for (String token : seeds.split("\\s*,\\s*")) {
                out.addAll(render(context, density, Long.parseLong(token), ticks));
            }
        }
        return out;
    }

    private List<SnapshotArtifact> render(SnapshotContext context, CordonPlan.Density density, long seed, int ticks)
            throws Exception {
        CordonSetup.CordonMap cordon = CordonSetup.map(CordonVariant.LINE, density, SurfacePalette.VERDANT, seed);
        String tag = density.name().toLowerCase(Locale.ROOT) + "-seed" + seed;
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = CordonSetup.create(cordon, CordonVariant.LINE, List.of(),
                CordonSequence.Timetable.STANDARD)) {
            ReviewImpactFx fx = new ReviewImpactFx(context.modRoot());
            for (int t = 0; t < ticks; t++) {
                sim.advance(BattleSimulation.TICK_DT);
                fx.feed(sim);
            }
            List<CordonRedoubt> redoubts = sim.getCordon().redoubts();
            BufferedImage mix = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = mix.createGraphics();
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < redoubts.size(); i++) {
                CordonRedoubt r = redoubts.get(i);
                String kind = r.construction().id;
                line.append(i == 0 ? "" : ", ").append(r.number()).append('=').append(kind);
                float fx0 = r.centreX() + 0.5f, fy0 = r.centreY() + 0.5f;
                out.add(new SnapshotArtifact(tag + "-r" + r.number() + "-" + kind + ".png",
                        BattleReviewFrameRenderer.look(context.modRoot(), W, H, fx0, fy0, PLAY_CELLS)
                                .render(sim, "", ReviewAnnotations.NONE, fx)));
                // A quarter of the frame over the play-zoom frame's cells: the whole redoubt and its approaches.
                BufferedImage quarter = BattleReviewFrameRenderer.look(context.modRoot(), W / 2, H / 2, fx0, fy0,
                        PLAY_CELLS).render(sim, "", ReviewAnnotations.NONE, fx);
                g.drawImage(quarter, (i % 2) * (W / 2), (i / 2) * (H / 2), null);
            }
            g.dispose();
            out.add(new SnapshotArtifact(tag + "-mix.png", mix));
            Laid base = cordon.map().layout.find(CordonConstructions.BASE_WORKS);
            if (density == CordonPlan.Density.URBAN && base != null && base.cells().size() == 2) {
                float bx = (base.cells().get(0)[0] + base.cells().get(1)[0]) / 2f + 0.5f;
                float by = (base.cells().get(0)[1] + base.cells().get(1)[1]) / 2f + 0.5f;
                out.add(new SnapshotArtifact(tag + "-base-" + base.reason() + ".png",
                        BattleReviewFrameRenderer.look(context.modRoot(), W, H, bx, by, BASE_CELLS)
                                .render(sim, "", ReviewAnnotations.NONE, fx)));
            }
            int berths = 0;
            for (Gantry gantry : cordon.map().gantries) if (gantry.holds == Gantry.Holds.MACHINE) berths++;
            System.out.printf("    [cordon-structure-look] %s: redoubts %s; base %s, %d machine berths%n", tag, line,
                    base == null ? "none" : base.reason(), berths);
        }
        return out;
    }
}
