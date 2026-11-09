package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.RaidWorksLayout;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
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
 * A disrupting raid's works as it is built ({@code raid-wreck-the-works.md}): an established break Raid on the Raid
 * fixture's market, its machines and landing zone drawn by the battle's own mission markers. Look preset: the works
 * at play zoom ({@code works-close.png}) and framed whole with the berth ({@code works-whole.png}). Nothing is played.
 */
public final class RaidWorksLookSuite implements SnapshotSuite {

    private static final long SEED = 141_418L;
    private static final TargetProfile MARKET = new TargetProfile(
            6, 5, 4, 2, "independent",
            EnumSet.of(EconomicFunction.SPACEPORT, EconomicFunction.HEAVY_INDUSTRY, EconomicFunction.COMMERCE),
            SurfacePalette.ROCK, SettlementLink.ROAD);

    @Override public String id() { return "raid-works-look"; }

    @Override public String label() { return "A break Raid's works and machines, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            DustLedgerMissionPreviewSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            OperationTier tier = OperationTier.ESTABLISHED;
            List<SnapshotArtifact> out = new ArrayList<>();
            try (BattleSimulation sim = BattleSetup.createRaidWorks(SEED, BattleSetup.defaultManifest(), false, tier,
                    RiskLevel.MEDIUM, MARKET, FlybyRoster.EMPTY, FlybyRoster.EMPTY, null, null,
                    RaidWorksLayout.machinesFor(tier))) {
                RaidObjective works = null;
                for (Objective objective : sim.getObjectives()) {
                    if (objective instanceof RaidObjective raid && raid.works()) works = raid;
                }
                if (works == null) throw new IllegalStateException("the break Raid laid no works");
                float minX = works.egressCellX(), maxX = minX, minY = works.egressCellY(), maxY = minY;
                float sx = 0, sy = 0;
                for (RaidObjective.Site site : works.sites()) {
                    System.out.printf(Locale.ROOT, "[%s] machine at %d,%d%n", id(), site.cellX(), site.cellY());
                    sx += site.cellX();
                    sy += site.cellY();
                    minX = Math.min(minX, site.cellX());
                    maxX = Math.max(maxX, site.cellX());
                    minY = Math.min(minY, site.cellY());
                    maxY = Math.max(maxY, site.cellY());
                }
                int n = works.sites().size();
                out.add(new SnapshotArtifact("works-close.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        1920, 1080, sx / n + .5f, sy / n + .5f, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS)
                        .withMissionMarkers().render(sim, "")));
                float across = Math.max(maxX - minX, (maxY - minY) * 16f / 9f) + 30f;
                out.add(new SnapshotArtifact("works-whole.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        1920, 1080, (minX + maxX) / 2f + .5f, (minY + maxY) / 2f + .5f, across)
                        .withMissionMarkers().render(sim, "")));
            }
            return out;
        }
    }
}
