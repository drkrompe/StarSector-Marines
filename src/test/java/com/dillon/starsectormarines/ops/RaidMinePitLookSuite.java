package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.RaidWorksLayout;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.NaturalVoid;
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
 * The mining Raid site's open pit ({@code voids.md}, the open pit): a break Raid on a mining market across two seeds
 * and both market sizes, as {@link RaidSiteLookSuite} lays them. Each case is framed whole ({@code <case>-whole.png})
 * and on the pit and the head works beside it ({@code <case>-pit.png}); the first also close up at play zoom on the
 * pit's haul road and conveyor ({@code <case>-pit-close.png}). Look preset; nothing is played.
 */
public final class RaidMinePitLookSuite implements SnapshotSuite {

    private record Case(String id, long seed, int marketSize, int defence, OperationTier tier) { }

    private static final List<Case> CASES = List.of(
            new Case("size4-seed141418", 141_418L, 4, 3, OperationTier.ESTABLISHED),
            new Case("size4-seed7", 7L, 4, 3, OperationTier.ESTABLISHED),
            new Case("size7-seed141418", 141_418L, 7, 5, OperationTier.REINFORCED),
            new Case("size7-seed7", 7L, 7, 5, OperationTier.REINFORCED));

    /** The pit's look with its benches scaled to a floor {@code depth} metres down, each bench kept where it is. */
    static NaturalVoid deeper(NaturalVoid look, float depth) {
        float f = depth / look.floorMeters();
        List<NaturalVoid.Step> steps = new ArrayList<>();
        for (NaturalVoid.Step step : look.steps()) steps.add(new NaturalVoid.Step(step.depthMeters() * f, step.insetCells()));
        return new NaturalVoid(look.style(), steps, look.eyeMeters(), look.wallTexture(), look.wallTileMeters(),
                look.seed(), look.belts());
    }

    @Override public String id() { return "raid-mine-pit-look"; }

    @Override public String label() { return "The mining Raid site's open pit across seeds and market sizes, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            DustLedgerMissionPreviewSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            List<SnapshotArtifact> out = new ArrayList<>();
            boolean first = true;
            for (Case c : CASES) {
                TargetProfile market = new TargetProfile(c.marketSize(), 5, c.defence(), 2, "independent",
                        EnumSet.of(EconomicFunction.MINING, EconomicFunction.SPACEPORT, EconomicFunction.HABITATION),
                        SurfacePalette.ROCK, SettlementLink.ROAD);
                try (BattleSimulation sim = BattleSetup.createRaidWorks(c.seed(), BattleSetup.defaultManifest(),
                        false, c.tier(), RiskLevel.MEDIUM, market, FlybyRoster.EMPTY, FlybyRoster.EMPTY, null, null,
                        RaidWorksLayout.machinesFor(c.tier()), "mining")) {
                    CellTopology topology = sim.getTopology();
                    int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
                    float sx = 0, sy = 0;
                    int cells = 0;
                    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                        if (topology.getGroundKind(x, y) != GroundKind.VOID) continue;
                        sx += x + .5f;
                        sy += y + .5f;
                        cells++;
                    }
                    System.out.printf(Locale.ROOT, "[%s] %s %dx%d pit %d cells, belts %d%n", id(), c.id(), w, h, cells,
                            topology.naturalVoid() == null ? 0 : topology.naturalVoid().belts().size());
                    out.add(new SnapshotArtifact(c.id() + "-whole.png", BattleReviewFrameRenderer.look(
                            context.modRoot(), 1920, 1080, w / 2f, h / 2f, Math.max(w, h * 16f / 9f) + 4f)
                            .withMissionMarkers().render(sim, "")));
                    if (cells == 0) continue;
                    float px = sx / cells, py = sy / cells;
                    out.add(new SnapshotArtifact(c.id() + "-pit.png", BattleReviewFrameRenderer.look(
                            context.modRoot(), 1920, 1080, px, py, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 2.4f)
                            .withMissionMarkers().render(sim, "")));
                    if (first) {
                        out.add(new SnapshotArtifact(c.id() + "-pit-close.png", BattleReviewFrameRenderer.look(
                                context.modRoot(), 1920, 1080, px, py, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS)
                                .render(sim, "")));
                        // The same ground drawn at other depths, for the owner's pick: only the look changes.
                        NaturalVoid standing = topology.naturalVoid();
                        for (String text : System.getProperty("snapshot.pit.depths", "27,60").split(",")) {
                            float depth = Float.parseFloat(text.trim());
                            topology.setNaturalVoid(deeper(standing, depth));
                            String tag = String.format(Locale.ROOT, "%s-depth%02dm", c.id(), Math.round(depth));
                            out.add(new SnapshotArtifact(tag + "-pit.png", BattleReviewFrameRenderer.look(
                                    context.modRoot(), 1920, 1080, px, py,
                                    BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 2.4f).render(sim, "")));
                        }
                        topology.setNaturalVoid(standing);
                        first = false;
                    }
                }
            }
            return out;
        }
    }
}
