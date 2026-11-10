package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.RaidWorksLayout;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
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
 * The heavy-industry Raid site as it is built ({@code raid-mission.md}, Site templates): a break Raid on a
 * heavy-industry market, laid from {@code RaidSiteTemplate}, across two seeds and two market sizes (a size-4 market
 * at Established on the 280x168 map, a size-7 market at Reinforced on the 400x240 map). Each is framed whole
 * ({@code <case>-whole.png}) and at play zoom on the works ({@code <case>-works.png}), look preset, with the
 * battle's own mission markers. Nothing is played. Each other industry's site is a subclass naming its industry
 * ({@link RaidMineSiteLookSuite}).
 */
public class RaidSiteLookSuite implements SnapshotSuite {

    private record Case(String id, long seed, int marketSize, int defence, OperationTier tier) { }

    private static final List<Case> CASES = List.of(
            new Case("size4-seed141418", 141_418L, 4, 3, OperationTier.ESTABLISHED),
            new Case("size4-seed7", 7L, 4, 3, OperationTier.ESTABLISHED),
            new Case("size7-seed141418", 141_418L, 7, 5, OperationTier.REINFORCED),
            new Case("size7-seed7", 7L, 7, 5, OperationTier.REINFORCED));

    private final String id;
    private final String industry;
    private final EconomicFunction function;
    private final SurfacePalette palette;

    public RaidSiteLookSuite() {
        this("raid-site-look", "heavyindustry", EconomicFunction.HEAVY_INDUSTRY);
    }

    protected RaidSiteLookSuite(String id, String industry, EconomicFunction function) {
        this(id, industry, function, SurfacePalette.ROCK);
    }

    /** A site shown on the world it belongs on: a farm on living ground rather than bare rock. */
    protected RaidSiteLookSuite(String id, String industry, EconomicFunction function, SurfacePalette palette) {
        this.id = id;
        this.industry = industry;
        this.function = function;
        this.palette = palette;
    }

    @Override public String id() { return id; }

    /** The works shot's centre and width in cells: on the machines, a little wider than play zoom. */
    protected float[] worksFrame(RaidObjective works) {
        float sx = 0, sy = 0;
        for (RaidObjective.Site site : works.sites()) {
            sx += site.cellX();
            sy += site.cellY();
        }
        int n = works.sites().size();
        return new float[]{sx / n + .5f, sy / n + .5f, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 1.6f};
    }

    @Override public String label() {
        return "The " + industry + " Raid site across seeds and market sizes, look preset";
    }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            DustLedgerMissionPreviewSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            List<SnapshotArtifact> out = new ArrayList<>();
            for (Case c : CASES) {
                TargetProfile market = new TargetProfile(c.marketSize(), 5, c.defence(), 2, "independent",
                        EnumSet.of(function, EconomicFunction.SPACEPORT,
                                EconomicFunction.HABITATION),
                        palette, SettlementLink.ROAD);
                try (BattleSimulation sim = BattleSetup.createRaidWorks(c.seed(), BattleSetup.defaultManifest(),
                        false, c.tier(), RiskLevel.MEDIUM, market, FlybyRoster.EMPTY, FlybyRoster.EMPTY, null, null,
                        RaidWorksLayout.machinesFor(c.tier()), industry)) {
                    RaidObjective works = null;
                    for (Objective objective : sim.getObjectives()) {
                        if (objective instanceof RaidObjective raid && raid.works()) works = raid;
                    }
                    if (works == null) throw new IllegalStateException(c.id() + ": the site laid no works");
                    for (RaidObjective.Site site : works.sites()) {
                        System.out.printf(Locale.ROOT, "[%s] %s machine at %d,%d%n", id(), c.id(),
                                site.cellX(), site.cellY());
                    }
                    int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
                    out.add(new SnapshotArtifact(c.id() + "-whole.png", BattleReviewFrameRenderer.look(
                            context.modRoot(), 1920, 1080, w / 2f, h / 2f, Math.max(w, h * 16f / 9f) + 4f)
                            .withMissionMarkers().render(sim, "")));
                    float[] frame = worksFrame(works);
                    out.add(new SnapshotArtifact(c.id() + "-works.png", BattleReviewFrameRenderer.look(
                            context.modRoot(), 1920, 1080, frame[0], frame[1], frame[2])
                            .withMissionMarkers().render(sim, "")));
                }
            }
            return out;
        }
    }
}
