package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.Landform;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.Fonts;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Buildings stood up ({@code voids.md}, buildings in depth; {@code battle.render.buildingFaces}): an open verdant town
 * and a frozen town in a crevasse, each drawn flat and stood up, whole ({@code <case>-<flat|faces>-whole.png}) and at
 * play zoom on its busiest block ({@code -play.png}), and stood up panned across that block at play zoom
 * ({@code <case>-pan-NN.png}), since the parallax is the point. Look preset; nothing is played.
 */
public final class BuildingFacesLookSuite implements SnapshotSuite {

    private static final String SWITCH = "battle.render.buildingFaces";
    /** Frames in a pan, and how far it runs either side of the block, cells. */
    private static final int PAN_FRAMES = 60;
    private static final float PAN_CELLS = 14f;

    private record Case(String id, SurfacePalette palette, Landform landform) { }

    private static final List<Case> CASES = List.of(
            new Case("open-town", SurfacePalette.VERDANT, null),
            new Case("frozen-crevasse-town", SurfacePalette.FROZEN, new Landform.Shelter(true)));

    @Override public String id() { return "building-faces-look"; }

    @Override public String label() { return "Buildings stood up with wall faces, before and after, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        String was = System.getProperty(SWITCH);
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            DustLedgerMissionPreviewSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            List<SnapshotArtifact> out = new ArrayList<>();
            long seed = Long.getLong("snapshot.buildingFaces.seed", 7L);
            for (Case c : CASES) {
                TargetProfile market = new TargetProfile(5, 5, 2, 1, "independent",
                        EnumSet.of(EconomicFunction.HABITATION, EconomicFunction.MINING), c.palette(),
                        SettlementLink.ROAD);
                try (BattleSimulation sim = BattleSetup.createPlaceholder(seed, BattleSetup.defaultManifest(),
                        false, OperationTier.ESTABLISHED, RiskLevel.MEDIUM, MissionType.ASSAULT, market,
                        FlybyRoster.EMPTY, FlybyRoster.EMPTY, PrecinctPlan.Sprawl.BALANCED, c.landform())) {
                    int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
                    float[] block = busiest(sim);
                    for (boolean faces : new boolean[]{false, true}) {
                        System.setProperty(SWITCH, Boolean.toString(faces));
                        String id = c.id() + (faces ? "-faces" : "-flat");
                        out.add(new SnapshotArtifact(id + "-whole.png", BattleReviewFrameRenderer.look(
                                context.modRoot(), 1920, 1080, w / 2f, h / 2f, Math.max(w, h * 16f / 9f) + 4f)
                                .render(sim, "")));
                        out.add(new SnapshotArtifact(id + "-play.png", BattleReviewFrameRenderer.look(
                                context.modRoot(), 1920, 1080, block[0], block[1],
                                BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, "")));
                    }
                    System.setProperty(SWITCH, "true");
                    for (int f = 0; f < PAN_FRAMES; f++) {
                        float t = f / (PAN_FRAMES - 1f) * 2f - 1f;
                        out.add(new SnapshotArtifact(String.format(Locale.ROOT, "%s-pan-%02d.png", c.id(), f),
                                BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080,
                                        block[0] + t * PAN_CELLS, block[1] + t * PAN_CELLS * .4f,
                                        BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, "")));
                    }
                }
            }
            return out;
        } finally {
            if (was == null) System.clearProperty(SWITCH);
            else System.setProperty(SWITCH, was);
        }
    }

    /** The centre of the most roofed cells within a play-zoom frame's reach of a building. */
    private static float[] busiest(BattleSimulation sim) {
        float bestX = sim.getGrid().getWidth() / 2f, bestY = sim.getGrid().getHeight() / 2f;
        int best = -1;
        List<Building> all = new ArrayList<>(sim.getBuildings().all());
        for (Building a : all) {
            float ax = (a.minX + a.maxX + 1) / 2f, ay = (a.minY + a.maxY + 1) / 2f;
            int near = 0;
            for (Building b : all) {
                float bx = (b.minX + b.maxX + 1) / 2f, by = (b.minY + b.maxY + 1) / 2f;
                if (Math.abs(bx - ax) < 20f && Math.abs(by - ay) < 11f) near += (b.maxX - b.minX + 1) * (b.maxY - b.minY + 1);
            }
            if (near > best) { best = near; bestX = ax; bestY = ay; }
        }
        return new float[]{bestX, bestY};
    }
}
