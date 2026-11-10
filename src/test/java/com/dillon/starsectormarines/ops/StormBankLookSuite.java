package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.Climate;
import com.dillon.starsectormarines.battle.world.gen.StormBank;
import com.dillon.starsectormarines.battle.world.gen.StormWind;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
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
 * The storm bank on Raid sites ({@code mapgen-nouns.md}, Climate; {@link StormBank}): the size-4 heavy-industry site
 * (seed 7, as {@code planet-ground-look}) on a desert and a dust-blown rock under storms, the wind from each side the
 * bank can face. Each is framed whole ({@code <world>-<wind>-whole.png}) and close on the bank's middle
 * ({@code <world>-<wind>-bank.png}), look preset, and reports how many cells the bank raised. Nothing is played.
 */
public final class StormBankLookSuite implements SnapshotSuite {

    private static final long SEED = 7L;

    @Override public String id() { return "storm-bank-look"; }

    @Override public String label() { return "Storm banks on desert Raid sites, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            DustLedgerMissionPreviewSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            List<SnapshotArtifact> out = new ArrayList<>();
            // world, wind, variant suffix, (unused), (unused), industry, size, seed
            Object[][] cases = {
                    {SurfacePalette.ARID, StormWind.Side.EAST, "-mine-size7", null, null, "mining", 7, 7L},
                    {SurfacePalette.ARID, StormWind.Side.NORTH, "-mine-size7", null, null, "mining", 7, 7L},
                    {SurfacePalette.ARID, StormWind.Side.EAST, "", null, null},
                    {SurfacePalette.ARID, StormWind.Side.NORTH, "", null, null},
                    {SurfacePalette.ARID, StormWind.Side.SOUTH, "", null, null},
                    {SurfacePalette.ROCK, StormWind.Side.WEST, "", null, null}};
            for (Object[] k : cases) {
                SurfacePalette palette = (SurfacePalette) k[0];
                StormWind.Side wind = (StormWind.Side) k[1];
                Climate climate = new Climate(EnumSet.of(Climate.Hazard.STORMS), wind);
                boolean other = k.length > 5;
                try (BattleSimulation sim = PlanetGroundLookSuite.site(palette, climate, other ? (Long) k[7] : SEED,
                        other ? (Integer) k[6] : 4, 3, OperationTier.ESTABLISHED, other ? (String) k[5] : "heavyindustry")) {
                    int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
                    String name = palette.name().toLowerCase(Locale.ROOT) + "-" + wind.name().toLowerCase(Locale.ROOT)
                            + k[2];
                    ElevationMap elevation = sim.getGrid().elevation();
                    int raised = 0;
                    long along = 0;
                    if (elevation != null) {
                        for (int i = 0; i < w * h; i++) {
                            if (elevation.sightHeightAt(i) <= 0) continue;
                            raised++;
                            along += wind.alongX() ? i % w : i / w;
                        }
                    }
                    // Close on the middle of what the bank raised, the town's edge in frame below it.
                    float mean = raised > 0 ? (float) along / raised : (wind.alongX() ? w : h) / 2f, mid = mean;
                    // The raised cell nearest that middle, so a pass between two stretches is not all the frame holds.
                    if (elevation != null) {
                        for (int i = 0; i < w * h; i++) {
                            int a = wind.alongX() ? i % w : i / w;
                            if (elevation.sightHeightAt(i) > 0 && Math.abs(a - mean) < Math.abs(mid - mean)
                                    || mid == mean && elevation.sightHeightAt(i) > 0) mid = a;
                        }
                    }
                    System.out.println("[storm-bank-look] " + name + " " + w + "x" + h + " raised cells " + raised);
                    out.add(new SnapshotArtifact(name + "-whole.png", BattleReviewFrameRenderer.look(
                            context.modRoot(), 1920, 1080, w / 2f, h / 2f, Math.max(w, h * 16f / 9f) + 4f)
                            .withMissionMarkers().render(sim, "")));
                    int depth = StormBank.OFFSET + StormBank.THICKNESS / 2;
                    float cx = wind.alongX() ? mid : wind.low() ? depth + 20 : w - depth - 20;
                    float cy = wind.alongX() ? (wind.low() ? depth + 11 : h - depth - 11) : mid;
                    out.add(new SnapshotArtifact(name + "-bank.png", BattleReviewFrameRenderer.look(
                            context.modRoot(), 1920, 1080, cx, cy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS)
                            .render(sim, "")));
                }
            }
            return out;
        }
    }
}
