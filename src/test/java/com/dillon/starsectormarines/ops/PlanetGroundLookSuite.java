package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.RaidWorksLayout;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.Climate;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
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
import java.util.Locale;

/**
 * One settled map on every surface palette: the size-4 heavy-industry Raid site (seed 7) built on a rock, a desert,
 * a frozen, an irradiated and a living world, so the colony's own ground can be judged against the planet's
 * ({@code mapgen-nouns.md}, cultivated ground). Each palette is framed whole ({@code <palette>-whole.png}) and at
 * play zoom on the town by the landing ({@code <palette>-town.png}), look preset; a desert and a frozen world under
 * storms add {@code <palette>-storm-*} with their drifts. Nothing is played.
 */
public final class PlanetGroundLookSuite implements SnapshotSuite {

    private static final long SEED = 7L;

    @Override public String id() { return "planet-ground-look"; }

    @Override public String label() { return "One Raid site on each surface palette, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            DustLedgerMissionPreviewSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            List<SnapshotArtifact> out = new ArrayList<>();
            List<Object[]> cases = new ArrayList<>();
            for (SurfacePalette palette : SurfacePalette.values()) cases.add(new Object[] {palette, Climate.NONE, ""});
            Climate storms = new Climate(EnumSet.of(Climate.Hazard.STORMS));
            cases.add(new Object[] {SurfacePalette.ARID, storms, "-storm"});
            cases.add(new Object[] {SurfacePalette.FROZEN, storms, "-storm"});
            for (Object[] k : cases) {
                SurfacePalette palette = (SurfacePalette) k[0];
                long seed = k.length > 3 ? (Long) k[3] : SEED;
                int size = k.length > 3 ? (Integer) k[4] : 4, defence = k.length > 3 ? (Integer) k[5] : 3;
                OperationTier tier = k.length > 3 ? (OperationTier) k[6] : OperationTier.ESTABLISHED;
                TargetProfile market = new TargetProfile(size, 5, defence, 2, "independent",
                        EnumSet.of(EconomicFunction.HEAVY_INDUSTRY, EconomicFunction.SPACEPORT,
                                EconomicFunction.HABITATION),
                        palette, SettlementLink.ROAD).withClimate((Climate) k[1]);
                try (BattleSimulation sim = BattleSetup.createRaidWorks(seed, BattleSetup.defaultManifest(),
                        false, tier, RiskLevel.MEDIUM, market, FlybyRoster.EMPTY,
                        FlybyRoster.EMPTY, null, null, RaidWorksLayout.machinesFor(tier),
                        "heavyindustry")) {
                    String name = palette.name().toLowerCase(Locale.ROOT) + k[2];
                    int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
                    out.add(new SnapshotArtifact(name + "-whole.png", BattleReviewFrameRenderer.look(
                            context.modRoot(), 1920, 1080, w / 2f, h / 2f, Math.max(w, h * 16f / 9f) + 4f)
                            .withMissionMarkers().render(sim, "")));
                    out.add(new SnapshotArtifact(name + "-town.png", BattleReviewFrameRenderer.look(
                            context.modRoot(), 1920, 1080, w * 0.3f, h * 0.5f,
                            BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 1.6f)
                            .withMissionMarkers().render(sim, "")));
                }
            }
            return out;
        }
    }
}
