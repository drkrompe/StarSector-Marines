package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.RaidOwnedMap;
import com.dillon.starsectormarines.battle.setup.RaidWorksLayout;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import com.dillon.starsectormarines.battle.world.model.Doodad;
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
 * The refining Raid site ({@code RefiningWorks}): a break Raid on a refining market across two seeds and both market
 * sizes, as {@link RaidSiteLookSuite} lays heavy industry's. Each case is framed whole ({@code <case>-whole.png}), on
 * the process area's units ({@code <case>-works.png}), and on the plant from the units to the tank farm
 * ({@code <case>-plant.png}): the receiving manifold, the units' columns, the product rack over the haul road, the
 * bunded tanks and the loading rack. Last, the owned map a campaign raid on a refinery plays ({@code owned-seed<n>}).
 * Look preset, with the battle's mission markers. Nothing is played.
 */
public final class RaidRefinerySiteLookSuite implements SnapshotSuite {

    private record Case(String id, long seed, int marketSize, int defence, OperationTier tier) { }

    private static final List<Case> CASES = List.of(
            new Case("size4-seed141418", 141_418L, 4, 3, OperationTier.ESTABLISHED),
            new Case("size4-seed7", 7L, 4, 3, OperationTier.ESTABLISHED),
            new Case("size7-seed141418", 141_418L, 7, 5, OperationTier.REINFORCED),
            new Case("size7-seed7", 7L, 7, 5, OperationTier.REINFORCED));

    /** The tank farm's vessels: the refinery sheet's lower row (tanks and spheres), drawn at least this big. */
    private static final int TANK_MIN = 8;
    private static final int VESSEL_ROW_PX = 480;

    @Override public String id() { return "raid-refinery-site-look"; }

    @Override public String label() {
        return "The refining Raid site across seeds and market sizes, look preset";
    }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            DustLedgerMissionPreviewSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            List<SnapshotArtifact> out = new ArrayList<>();
            for (Case c : CASES) {
                TargetProfile market = new TargetProfile(c.marketSize(), 5, c.defence(), 2, "independent",
                        EnumSet.of(EconomicFunction.REFINING, EconomicFunction.SPACEPORT, EconomicFunction.HABITATION),
                        SurfacePalette.ROCK, SettlementLink.ROAD);
                try (BattleSimulation sim = BattleSetup.createRaidWorks(c.seed(), BattleSetup.defaultManifest(),
                        false, c.tier(), RiskLevel.MEDIUM, market, FlybyRoster.EMPTY, FlybyRoster.EMPTY, null, null,
                        RaidWorksLayout.machinesFor(c.tier()), "refining")) {
                    out.addAll(frames(context, c.id(), sim));
                }
            }
            // The map a campaign raid on a refinery plays: the owned seed on the reference market.
            RaidOwnedMap.Ground ground = RaidOwnedMap.Ground.of(StoryMap.RAID_REFINING,
                    StoryMapPools.pool(StoryMap.RAID_REFINING).home());
            long owned = StoryMapPools.pool(StoryMap.RAID_REFINING).owned().seed();
            try (BattleSimulation sim = BattleSetup.createPooledRaid(ground, ground.home(), owned, true, List.of())) {
                out.addAll(frames(context, "owned-seed" + owned, sim));
            }
            return out;
        }
    }

    /** The case whole, on the works' units, and on the plant from the units to the tank farm. */
    private List<SnapshotArtifact> frames(SnapshotContext context, String id, BattleSimulation sim) throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        RaidObjective works = null;
        for (Objective objective : sim.getObjectives()) {
            if (objective instanceof RaidObjective raid && raid.works()) works = raid;
        }
        if (works == null) throw new IllegalStateException(id + ": the site laid no works");
        float sx = 0, sy = 0;
        for (RaidObjective.Site site : works.sites()) {
            sx += site.cellX();
            sy += site.cellY();
        }
        int n = works.sites().size();
        float ux = sx / n + .5f, uy = sy / n + .5f;
        // The tank farm: the big vessels outside the works, their middle.
        float tx = 0, ty = 0;
        int tanks = 0;
        for (Doodad d : sim.getDoodads()) {
            if (d.footprintCellsX < TANK_MIN || !d.sheetPath.contains("refinery-plant")
                    || d.tile.row * d.sourceCellPx < VESSEL_ROW_PX) continue;
            float dx = d.cellX + d.footprintCellsX / 2f, dy = d.cellY + d.footprintCellsY / 2f;
            if (Math.hypot(dx - ux, dy - uy) < 30) continue;
            tx += dx;
            ty += dy;
            tanks++;
        }
        System.out.printf(Locale.ROOT, "[%s] %s units %d, farm tanks %d%n", id(), id, n, tanks);
        int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
        out.add(new SnapshotArtifact(id + "-whole.png", BattleReviewFrameRenderer.look(
                context.modRoot(), 1920, 1080, w / 2f, h / 2f, Math.max(w, h * 16f / 9f) + 4f)
                .withMissionMarkers().render(sim, "")));
        // The process area: the units' fired heaters, framed whole; the raid's sites where no heater stands.
        float hx0 = Float.MAX_VALUE, hy0 = Float.MAX_VALUE, hx1 = -Float.MAX_VALUE, hy1 = -Float.MAX_VALUE;
        for (Doodad d : sim.getDoodads()) {
            if (!d.sheetPath.contains("refinery-utilities") || d.tile.col != 0 || d.tile.row != 0) continue;
            hx0 = Math.min(hx0, d.cellX);
            hy0 = Math.min(hy0, d.cellY);
            hx1 = Math.max(hx1, d.cellX + d.footprintCellsX);
            hy1 = Math.max(hy1, d.cellY + d.footprintCellsY);
        }
        float cx = ux, cy = uy, close = BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 1.6f;
        if (hx1 > hx0) {
            cx = (hx0 + hx1) / 2f;
            cy = (hy0 + hy1) / 2f;
            close = Math.max(close, Math.max(hx1 - hx0, (hy1 - hy0) * 16f / 9f) + 30f);
        }
        out.add(new SnapshotArtifact(id + "-works.png", BattleReviewFrameRenderer.look(
                context.modRoot(), 1920, 1080, cx, cy, close)
                .withMissionMarkers().render(sim, "")));
        if (tanks > 0) {
            float fx = tx / tanks, fy = ty / tanks;
            float span = Math.max(Math.abs(fx - ux) * 1.35f, Math.abs(fy - uy) * 1.35f * 16f / 9f) + 40f;
            out.add(new SnapshotArtifact(id + "-plant.png", BattleReviewFrameRenderer.look(
                    context.modRoot(), 1920, 1080, (fx + ux) / 2f, (fy + uy) / 2f,
                    Math.max(BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 1.6f, span))
                    .withMissionMarkers().render(sim, "")));
        }
        return out;
    }
}
