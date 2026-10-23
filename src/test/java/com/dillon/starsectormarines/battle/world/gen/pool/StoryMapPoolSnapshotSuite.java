package com.dillon.starsectormarines.battle.world.gen.pool;

import com.dillon.starsectormarines.battle.cordon.CordonMapgenSnapshotSuite;
import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.evacuation.HeartBores;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
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

/**
 * Every pooled story map, whole, one frame each ({@code story-map-pools.md}):
 * the contact sheet a person picks or swaps pool seeds from. 1920x1080.
 * {@code -Dsnapshot.storyMapPools.maps=cordon-line,heart-burn} narrows it.
 */
public final class StoryMapPoolSnapshotSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;

    @Override public String id() { return "story-map-pools"; }

    @Override public String label() { return "Story map pools: every pooled Cordon and Heart map, whole"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        BattleReviewFrameRenderer whole = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H);
        String selected = System.getProperty("snapshot.storyMapPools.maps", "");
        List<SnapshotArtifact> out = new ArrayList<>();
        for (StoryMap map : StoryMap.values()) {
            if (!selected.isBlank() && !List.of(selected.split(",")).contains(map.id)) continue;
            int index = 1;
            for (StoryMapPool.Entry entry : StoryMapPools.pool(map).entries()) {
                String name = String.format("%s/%02d-seed%d.png", map.id, index++, entry.seed());
                String head = map.id + " seed " + entry.seed() + (entry.note().isBlank() ? "" : " | " + entry.note());
                out.add(new SnapshotArtifact(name, frame(whole, map, entry, head)));
            }
        }
        return out;
    }

    private static BufferedImage frame(BattleReviewFrameRenderer whole, StoryMap map,
                                                      StoryMapPool.Entry entry, String head) throws Exception {
        switch (map) {
            case CORDON_LINE, CORDON_ROAD -> {
                CordonVariant variant = map == StoryMap.CORDON_LINE ? CordonVariant.LINE : CordonVariant.ROAD;
                CordonSetup.CordonMap cordon = CordonSetup.map(variant, entry, TargetProfile.NEUTRAL);
                try (BattleSimulation sim = CordonSetup.create(cordon, variant, List.of(), TargetProfile.NEUTRAL,
                        CordonSequence.Timetable.STANDARD)) {
                    return whole.render(sim, head, CordonMapgenSnapshotSuite.annotations(sim, cordon));
                }
            }
            default -> {
                Form form = map == StoryMap.HEART_BURN ? Form.THE_BURN : Form.BROAD_FRONT;
                BloomHeartSetup.Built built = BloomHeartSetup.country(form, entry);
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
