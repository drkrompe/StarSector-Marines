package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.RaidWorksLayout;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SurfaceZoning;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
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

/**
 * The planet types that share a palette, each wearing its signature ({@code mapgen-nouns.md}, Surface signatures):
 * the size-4 Raid site (seed 7) as {@code PlanetGroundLookSuite} builds it, on a plain barren rock as the control and
 * on a bombarded, venus-like, unstable, cast-iron and cryovolcanic world. Each type is framed whole
 * ({@code <type>-whole.png}), at play zoom on the town ({@code <type>-town.png}) and at play zoom on open country
 * ({@code <type>-country.png}), look preset. Nothing is played.
 *
 * <p>{@code -Dsnapshot.signature.types=a,b} renders only those vanilla type ids.
 */
public final class PlanetSignatureLookSuite implements SnapshotSuite {

    private static final long SEED = 7L;
    private static final List<String> TYPES = List.of("barren", "barren-bombarded", "barren_venuslike",
            "rocky_unstable", "barren_castiron", "cryovolcanic");

    @Override public String id() { return "planet-signature-look"; }

    @Override public String label() { return "One Raid site on each signature planet type, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        String only = System.getProperty("snapshot.signature.types", "");
        List<String> types = only.isBlank() ? TYPES : List.of(only.split(","));
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            DustLedgerMissionPreviewSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            List<SnapshotArtifact> out = new ArrayList<>();
            for (String type : types) {
                SurfacePalette palette = SurfaceZoning.forPlanetType(type);
                TargetProfile market = new TargetProfile(4, 5, 3, 2, "independent",
                        EnumSet.of(EconomicFunction.HEAVY_INDUSTRY, EconomicFunction.SPACEPORT,
                                EconomicFunction.HABITATION),
                        palette, SettlementLink.ROAD).withSignature(SurfaceZoning.signatureFor(type));
                try (BattleSimulation sim = BattleSetup.createRaidWorks(SEED, BattleSetup.defaultManifest(),
                        false, OperationTier.ESTABLISHED, RiskLevel.MEDIUM, market, FlybyRoster.EMPTY,
                        FlybyRoster.EMPTY, null, null, RaidWorksLayout.machinesFor(OperationTier.ESTABLISHED),
                        "heavyindustry")) {
                    int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
                    out.add(new SnapshotArtifact(type + "-whole.png", BattleReviewFrameRenderer.look(
                            context.modRoot(), 1920, 1080, w / 2f, h / 2f, Math.max(w, h * 16f / 9f) + 4f)
                            .withMissionMarkers().render(sim, "")));
                    out.add(new SnapshotArtifact(type + "-town.png", BattleReviewFrameRenderer.look(
                            context.modRoot(), 1920, 1080, w * 0.3f, h * 0.5f,
                            BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 1.6f)
                            .withMissionMarkers().render(sim, "")));
                    out.add(new SnapshotArtifact(type + "-country.png", BattleReviewFrameRenderer.look(
                            context.modRoot(), 1920, 1080, w * 0.2f, h * 0.25f,
                            BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 1.6f)
                            .withMissionMarkers().render(sim, "")));
                }
            }
            return out;
        }
    }
}
