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
import com.dillon.starsectormarines.battle.world.model.CellTopology;
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
import java.util.Objects;

/**
 * One settled map on every surface palette: the size-4 heavy-industry Raid site (seed 7) built on a rock, a desert,
 * a frozen, an irradiated and a living world, so the colony's own ground can be judged against the planet's
 * ({@code mapgen-nouns.md}, cultivated ground). Each palette is framed whole ({@code <palette>-whole.png}) and at
 * play zoom on the town by the landing ({@code <palette>-town.png}), look preset; a desert and a frozen world under
 * storms add {@code <palette>-storm-*} with their drifts. Close on the drifts, a desert, a dust-blown rock and a
 * frozen world under storms are framed at {@link #DRIFT_CELLS} across on the stretch of town with the most drifted
 * cells, once with their storms ({@code <palette>-drift-on.png}) and once the same map without
 * ({@code <palette>-drift-off.png}), so the drift is the only difference. Nothing is played.
 */
public final class PlanetGroundLookSuite implements SnapshotSuite {

    private static final long SEED = 7L;
    /** How many cells across the drift close-ups frame: closer than play zoom, so a drift's cells read. */
    static final float DRIFT_CELLS = 34f;

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
                try (BattleSimulation sim = site(palette, (Climate) k[1], seed, size, defence, tier)) {
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
            for (SurfacePalette palette : new SurfacePalette[] {SurfacePalette.ARID, SurfacePalette.ROCK,
                    SurfacePalette.FROZEN}) {
                try (BattleSimulation on = site(palette, storms, SEED, 4, 3, OperationTier.ESTABLISHED);
                     BattleSimulation off = site(palette, Climate.NONE, SEED, 4, 3, OperationTier.ESTABLISHED)) {
                    float[] at = mostDrifted(on.getTopology(), off.getTopology());
                    String name = palette.name().toLowerCase(Locale.ROOT);
                    out.add(new SnapshotArtifact(name + "-drift-on.png", BattleReviewFrameRenderer.look(
                            context.modRoot(), 1920, 1080, at[0], at[1], DRIFT_CELLS).render(on, "")));
                    out.add(new SnapshotArtifact(name + "-drift-off.png", BattleReviewFrameRenderer.look(
                            context.modRoot(), 1920, 1080, at[0], at[1], DRIFT_CELLS).render(off, "")));
                }
            }
            return out;
        }
    }

    private static BattleSimulation site(SurfacePalette palette, Climate climate, long seed, int size, int defence,
                                         OperationTier tier) {
        TargetProfile market = new TargetProfile(size, 5, defence, 2, "independent",
                EnumSet.of(EconomicFunction.HEAVY_INDUSTRY, EconomicFunction.SPACEPORT, EconomicFunction.HABITATION),
                palette, SettlementLink.ROAD).withClimate(climate);
        return BattleSetup.createRaidWorks(seed, BattleSetup.defaultManifest(), false, tier, RiskLevel.MEDIUM, market,
                FlybyRoster.EMPTY, FlybyRoster.EMPTY, null, null, RaidWorksLayout.machinesFor(tier), "heavyindustry");
    }

    /** The centre of the close-up window holding the most cells whose surface the storm changed. */
    static float[] mostDrifted(CellTopology on, CellTopology off) {
        int w = on.getWidth(), h = on.getHeight();
        int ww = (int) DRIFT_CELLS, wh = (int) (DRIFT_CELLS * 9f / 16f);
        int[] sum = new int[(w + 1) * (h + 1)];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int d = Objects.equals(on.getSurfaceId(x, y), off.getSurfaceId(x, y)) ? 0 : 1;
                sum[(y + 1) * (w + 1) + x + 1] = d + sum[y * (w + 1) + x + 1] + sum[(y + 1) * (w + 1) + x]
                        - sum[y * (w + 1) + x];
            }
        }
        int best = -1;
        float[] at = {w / 2f, h / 2f};
        for (int y = 0; y + wh <= h; y += 2) {
            for (int x = 0; x + ww <= w; x += 2) {
                int n = sum[(y + wh) * (w + 1) + x + ww] - sum[y * (w + 1) + x + ww] - sum[(y + wh) * (w + 1) + x]
                        + sum[y * (w + 1) + x];
                if (n > best) { best = n; at = new float[] {x + ww / 2f, y + wh / 2f}; }
            }
        }
        return at;
    }
}
