package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Layer;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Way;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ruins ground at every grade ({@code ruins-ground.md}), 1920x1080 through the look preset: each grade's whole map,
 * the same map with its published places marked, and play-zoom frames on the landing, a layer's way through, a
 * landing court, the power hall, the collapse and the vault. {@code -Dsnapshot.ruinsGround.surface=ARID} picks
 * the world (default ROCK); {@code -Dsnapshot.ruinsGround.seed=N} the seed (default 1).
 */
public final class RuinsGroundSnapshotSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;

    @Override public String id() { return "ruins-ground"; }

    @Override public String label() { return "Ruins ground: every grade's delve, whole and at play zoom"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        SurfacePalette surface = SurfacePalette.valueOf(
                System.getProperty("snapshot.ruinsGround.surface", "ROCK").toUpperCase(Locale.ROOT));
        long seed = Long.getLong("snapshot.ruinsGround.seed", 1L);
        List<SnapshotArtifact> out = new ArrayList<>();
        for (int layers = 1; layers <= RuinsGroundPlan.MAX_LAYERS; layers++) {
            RuinsGroundPlan plan = RuinsGroundPlan.forLayers(layers);
            MapResult map = new BspCityGenerator().generateRuins(seed, RuinsGroundEvidence.profile(surface), plan);
            RuinsDelve delve = map.ruinsDelve;
            String dir = String.format("%d-layers-%s-seed%d/", layers, surface.name().toLowerCase(Locale.ROOT), seed);
            try (BattleSimulation sim = BattleSetup.buildMap(map, List.of(), map.defensePosts, List.of(), seed).sim()) {
                float across = plan.width() * 1.04f;
                out.add(new SnapshotArtifact(dir + "01-whole.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        W, H, plan.width() / 2f, plan.height() / 2f, across).render(sim, "")));
                out.add(new SnapshotArtifact(dir + "02-places.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        W, H, plan.width() / 2f, plan.height() / 2f, across).renderMarked(sim, marks(delve))));
                int n = 3;
                for (Map.Entry<String, float[]> view : closeViews(delve).entrySet()) {
                    float[] at = view.getValue();
                    out.add(new SnapshotArtifact(String.format("%s%02d-%s.png", dir, n++, view.getKey()),
                            BattleReviewFrameRenderer.look(context.modRoot(), W, H, at[0], at[1],
                                    BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, "")));
                }
            }
        }
        return out;
    }

    private static Map<String, float[]> closeViews(RuinsDelve delve) {
        Map<String, float[]> views = new LinkedHashMap<>();
        Area landing = delve.landingGround();
        views.put("landing", new float[]{landing.centreX() + 14, landing.centreY()});
        Way way = delve.layers().get(0).openWays().get(0);
        views.put("first-layer-way", new float[]{way.span().centreX(), way.span().centreY()});
        for (Layer layer : delve.layers()) {
            if (layer.court() != null) {
                views.put("court", new float[]{layer.court().ground().centreX(), layer.court().ground().centreY()});
                break;
            }
        }
        Area hall = delve.powerHall().footprint();
        views.put("power-hall", new float[]{hall.centreX(), hall.centreY()});
        Area collapse = delve.collapse().shortWay().span();
        views.put("collapse", new float[]{collapse.centreX(), collapse.centreY()});
        Area vault = delve.vault().footprint();
        views.put("vault", new float[]{vault.centreX() - 6, vault.centreY()});
        return views;
    }

    private static ReviewAnnotations marks(RuinsDelve delve) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        box(marks, delve.landingGround(), "landing", ReviewStyle.LANDING);
        box(marks, delve.vault().footprint(), "vault", ReviewStyle.KEEP);
        for (Layer layer : delve.layers()) {
            marks.label(layer.lineLeft(), 3, "layer " + (layer.index() + 1) + " "
                    + layer.kind().name().toLowerCase(Locale.ROOT).replace('_', ' '), ReviewStyle.FRONT);
            for (Way way : layer.ways()) {
                box(marks, way.span(), way.kind().name().toLowerCase(Locale.ROOT),
                        way.open() ? ReviewStyle.ROUTE : ReviewStyle.DUG_IN);
            }
            for (RuinsDelve.SideRoom room : layer.rooms()) box(marks, room.footprint(), "room", ReviewStyle.POSITION);
            if (layer.court() != null) box(marks, layer.court().ground(), "court", ReviewStyle.LANDING);
        }
        box(marks, delve.powerHall().footprint(), "power hall", ReviewStyle.OBJECTIVE);
        box(marks, delve.records().footprint(), "records", ReviewStyle.OBJECTIVE);
        for (RuinsDelve.Junction junction : delve.junctions()) {
            marks.label(junction.stand().x(), junction.stand().y(), "junction", ReviewStyle.NOTE);
        }
        return marks.build();
    }

    private static void box(ReviewAnnotations.Builder marks, Area area, String text, ReviewStyle style) {
        marks.box(area.left(), area.bottom(), area.right(), area.top(), text, style);
    }
}
