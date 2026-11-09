package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Ruins ground set beside the maps the company fights on elsewhere ({@code mapgen-nouns.md}, Ruins ground):
 * scattered and extensive ruins at the sizes {@link RuinsGroundPlan#forLayers} gives, the owner's pick
 * ({@code ruins-map-size} b, two steps up the tier band), and a Raid on the established map and an Extraction on the
 * full-strength map. Every map is drawn twice through the look preset, roofs on: fitted to the
 * frame ({@code *-fit.png}), and at one shared scale, {@value #SHARED_CELLS} cells across, so sizes compare
 * ({@code *-shared.png}). Seed 7, ground only for the ruins, the other missions as they open.
 */
public final class RuinsScaleOptionsSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    /** Wide enough for the largest map drawn here. */
    static final int SHARED_CELLS = 420;
    private static final long SEED = 7L;

    @Override public String id() { return "ruins-scale-options"; }

    @Override public String label() { return "Ruins map size against other missions' maps, fitted and at one scale"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        ruins(context, "scattered", RuinsGroundPlan.forLayers(1), out);
        ruins(context, "extensive", RuinsGroundPlan.forLayers(3), out);
        try (BattleSimulation raid = BattleSetup.createRaid(SEED, BattleSetup.defaultManifest(), false,
                OperationTier.ESTABLISHED, RiskLevel.MEDIUM, TargetProfile.NEUTRAL, FlybyRoster.EMPTY, FlybyRoster.EMPTY)) {
            frames(context, "raid-established", raid, out);
        }
        try (BattleSimulation extraction = BattleSetup.createExtraction(SEED, BattleSetup.defaultManifest(), false,
                OperationTier.REINFORCED, RiskLevel.MEDIUM, TargetProfile.NEUTRAL, FlybyRoster.EMPTY,
                FlybyRoster.EMPTY)) {
            frames(context, "extraction-reinforced", extraction, out);
        }
        return out;
    }

    private static void ruins(SnapshotContext context, String name, RuinsGroundPlan plan, List<SnapshotArtifact> out)
            throws Exception {
        MapResult map = new BspCityGenerator().generateRuins(SEED, RuinsGroundEvidence.profile(SurfacePalette.ARID), plan);
        try (BattleSimulation sim = BattleSetup.buildMap(map, List.of(), map.defensePosts, List.of(), SEED).sim()) {
            frames(context, name, sim, out);
        }
    }

    private static void frames(SnapshotContext context, String name, BattleSimulation sim, List<SnapshotArtifact> out)
            throws Exception {
        int width = sim.getGrid().getWidth(), height = sim.getGrid().getHeight();
        out.add(new SnapshotArtifact(name + "-fit.png", BattleReviewFrameRenderer.look(context.modRoot(),
                W, H, width / 2f, height / 2f, width * 1.04f).render(sim, "")));
        out.add(new SnapshotArtifact(name + "-shared.png", BattleReviewFrameRenderer.look(context.modRoot(),
                W, H, width / 2f, height / 2f, SHARED_CELLS).render(sim, "")));
    }
}
