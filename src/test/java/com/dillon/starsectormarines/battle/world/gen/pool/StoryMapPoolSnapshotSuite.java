package com.dillon.starsectormarines.battle.world.gen.pool;

import com.dillon.starsectormarines.battle.cordon.CordonMapgenSnapshotSuite;
import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.evacuation.HeartBores;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartLayout;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every pooled story map, whole, one frame each ({@code story-map-pools.md}):
 * the contact sheet a person picks or swaps pool seeds from. 1920x1080.
 * {@code -Dsnapshot.storyMapPools.maps=cordon-line,heart-burn} narrows it.
 */
public final class StoryMapPoolSnapshotSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;

    @Override public String id() { return "story-map-pools"; }

    @Override public String label() { return "Story map pools: every pooled Cordon, Heart and Colony Breach map, whole"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        BattleReviewFrameRenderer whole = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H);
        String selected = System.getProperty("snapshot.storyMapPools.maps", "");
        List<SnapshotArtifact> out = new ArrayList<>();
        for (StoryMap map : StoryMap.values()) {
            if (!selected.isBlank() && !List.of(selected.split(",")).contains(map.id)) continue;
            int index = 1;
            for (SurfacePalette biome : SurfacePalette.values()) {
                for (StoryMapPool.Entry entry : StoryMapPools.pool(map).entries(biome)) {
                    String name = String.format("%s/%02d-%s-seed%d.png", map.id, index++,
                            biome.name().toLowerCase(Locale.ROOT), entry.seed());
                    String head = map.id + " " + biome.name().toLowerCase(Locale.ROOT) + " seed " + entry.seed()
                            + (entry.note().isBlank() ? "" : " | " + entry.note());
                    out.add(new SnapshotArtifact(name, frame(whole, map, biome, entry, head)));
                    if (AIRBASES && map.id.startsWith("heart-front")) out.addAll(airbases(context, map, biome, entry, name, head));
                }
            }
        }
        return out;
    }

    /** {@code -Dsnapshot.storyMapPools.airbases=true}: a closer frame of each allied airbase and its hamlet too. */
    private static final boolean AIRBASES = Boolean.getBoolean("snapshot.storyMapPools.airbases");
    /** Cells across an airbase's close frame: the field, its hamlet and the ways round them. */
    private static final float AIRBASE_CELLS_ACROSS = 170f;

    private static List<SnapshotArtifact> airbases(SnapshotContext context, StoryMap map, SurfacePalette biome,
                                                   StoryMapPool.Entry entry, String name, String head) throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        BloomHeartSetup.Built built = BloomHeartSetup.country(BloomHeartSetup.formOf(map), biome,
                BloomHeartSetup.densityOf(map), entry);
        try (BattleSimulation sim = built.sim()) {
            for (Laid piece : built.map().layout.pieces()) {
                if (!piece.name().matches(HeartLayout.AIRBASE + "[0-9]+") || !piece.isLaid()) continue;
                // Framed on the field and every building of its hamlet.
                int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
                for (Laid part : built.map().layout.pieces()) {
                    if (!part.isLaid() || !(part.name().equals(piece.name()) || part.name().startsWith(piece.name() + "."))) continue;
                    for (int[] c : part.cells()) {
                        x0 = Math.min(x0, c[0]);
                        y0 = Math.min(y0, c[1]);
                        x1 = Math.max(x1, c[0]);
                        y1 = Math.max(y1, c[1]);
                    }
                }
                BattleReviewFrameRenderer close = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H,
                        (x0 + x1) / 2f, (y0 + y1) / 2f, Math.max(AIRBASE_CELLS_ACROSS, x1 - x0 + 30f));
                out.add(new SnapshotArtifact(name.replace(".png", "-" + piece.name() + ".png"),
                        close.render(sim, head + " | " + piece.name(), ReviewAnnotations.builder().build())));
            }
        }
        return out;
    }

    private static BufferedImage frame(BattleReviewFrameRenderer whole, StoryMap map, SurfacePalette biome,
                                       StoryMapPool.Entry entry, String head) throws Exception {
        switch (map) {
            case CORDON_LINE, CORDON_ROAD, CORDON_LINE_TOWN, CORDON_ROAD_TOWN, CORDON_LINE_URBAN, CORDON_ROAD_URBAN -> {
                CordonVariant variant = CordonSetup.variantOf(map);
                CordonSetup.CordonMap cordon = CordonSetup.map(variant, CordonSetup.densityOf(map), biome, entry);
                try (BattleSimulation sim = CordonSetup.create(cordon, variant, List.of(),
                        CordonSequence.Timetable.STANDARD)) {
                    return whole.render(sim, head, CordonMapgenSnapshotSuite.annotations(sim));
                }
            }
            case COLONY_BREACH -> {
                // The marked plan (holdouts, bores, objectives) is colony-breach-pressure-look's; this is the map.
                try (BattleSimulation sim = BattleSetup.createPooledColonyBreach(biome, entry, List.of())) {
                    return whole.render(sim, head, ReviewAnnotations.builder().build());
                }
            }
            default -> {
                BloomHeartSetup.Built built = BloomHeartSetup.country(BloomHeartSetup.formOf(map), biome,
                        BloomHeartSetup.densityOf(map), entry);
                try (BattleSimulation sim = built.sim()) {
                    ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
                    for (HeartBores.Site site : built.heart().sites()) {
                        marks.label(site.x(), site.y(), site.mother() ? "mother" : "satellite", ReviewStyle.OBJECTIVE);
                    }
                    marks.label(built.plan().landingPlace().seedX(), built.plan().landingPlace().seedY(), "LZ",
                            ReviewStyle.NOTE);
                    return whole.render(sim, head, marks.build());
                }
            }
        }
    }
}
