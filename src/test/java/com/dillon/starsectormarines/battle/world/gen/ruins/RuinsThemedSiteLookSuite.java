package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.fixture.RuinsBattleFixture;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.ruins.RuinsBatteryGuns;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultPrize;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultTheme;
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
import java.util.Locale;

/**
 * A ruins site built round a colony item ({@code ruins-expedition.md}, The vault and its prize): an extensive site
 * held by scavengers whose vault keeps a cryoarithmetic engine, so it keeps the old colony's batteries
 * ({@code RuinsBatteries}) on its approach. Built through {@link RuinsBattleFixture} as a launched expedition is, then
 * through the look preset at 1920x1080: the whole map, the approach at twice play zoom, and the middle battery close,
 * dark at landfall ({@code 01-dark}) and awake once the hall is restarted ({@code 02-awake}). Its ground is today's ruins ground: the
 * theme's own interior set is still owed. Prints each battery's cell and state. {@code -Dsnapshot.ruinsBattle.seed}
 * (default 7) and {@code -Dsnapshot.ruinsBattle.surface} (ARID).
 */
public final class RuinsThemedSiteLookSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    /** How wide the close frame on a battery is, cells. */
    private static final float CLOSE_CELLS = 28f;
    private static final RuinsGrade GRADE = RuinsGrade.EXTENSIVE;
    private static final RuinsVaultPrize PRIZE = new RuinsVaultPrize(RuinsVaultPrize.Family.COLONY_ITEM,
            RuinsVaultTheme.CRYOARITHMETIC_ENGINE, RuinsVaultTheme.CRYOARITHMETIC_ENGINE.itemId, 2);

    @Override public String id() { return "ruins-themed-site-look"; }

    @Override public String label() { return "Ruins themed site: a colony-item site's batteries, dark and awake, look preset"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        SurfacePalette surface = SurfacePalette.valueOf(
                System.getProperty("snapshot.ruinsBattle.surface", "ARID").toUpperCase(Locale.ROOT));
        long seed = Long.getLong("snapshot.ruinsBattle.seed", 7L);
        RuinsBattleFixture fixture = RuinsBattleFixture.fromFactoryInputs(seed, BattleSetup.defaultManifest(), false,
                OperationTier.VETERAN, RiskLevel.MEDIUM, RuinsGroundEvidence.profile(surface), FlybyRoster.EMPTY,
                FlybyRoster.EMPTY, GRADE, RuinsOccupant.SCAVENGERS, List.of(), PRIZE);
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = fixture.build()) {
            RuinsBatteryGuns batteries = sim.getRuinsBatteries();
            if (batteries == null) throw new IllegalStateException("the themed site stood no batteries");
            advance(sim, 2f);
            int width = sim.getGrid().getWidth(), height = sim.getGrid().getHeight();
            float[] frame = frame(sim, batteries);
            out.add(new SnapshotArtifact("00-whole.png", BattleReviewFrameRenderer.look(context.modRoot(),
                    W, H, width / 2f, height / 2f, width * 1.04f).render(sim, "")));
            report(sim, batteries);
            out.add(new SnapshotArtifact("01-dark.png", BattleReviewFrameRenderer.look(context.modRoot(),
                    W, H, frame[0], frame[1], frame[2]).render(sim, "")));
            out.add(new SnapshotArtifact("01-approach.png", BattleReviewFrameRenderer.look(context.modRoot(),
                    W, H, frame[0] - 20f, height / 2f, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 2f)
                    .render(sim, "")));
            // The company restarts the hall: the vault's power, and the guns' with it.
            sim.getRuinsWorks().graph().restart();
            advance(sim, 3f);
            report(sim, batteries);
            out.add(new SnapshotArtifact("02-awake.png", BattleReviewFrameRenderer.look(context.modRoot(),
                    W, H, frame[0], frame[1], frame[2]).render(sim, "")));
        }
        // Inside: each shown theme's vault room and a chamber, roofs off, on the same ground.
        for (RuinsVaultTheme theme : INSIDE) {
            RuinsVaultPrize prize = new RuinsVaultPrize(theme.family, theme,
                    theme.family == RuinsVaultPrize.Family.AI_CORE ? RuinsVaultPrize.BETA_CORE : theme.itemId, 2);
            RuinsBattleFixture themed = RuinsBattleFixture.fromFactoryInputs(seed, BattleSetup.defaultManifest(),
                    false, OperationTier.VETERAN, RiskLevel.MEDIUM, RuinsGroundEvidence.profile(surface),
                    FlybyRoster.EMPTY, FlybyRoster.EMPTY, GRADE, RuinsOccupant.SCAVENGERS, List.of(), prize);
            try (BattleSimulation sim = themed.build()) {
                // Generation itself holds the dressed ground to the delve laws (DelveCheck), so it built.
                RuinsDelve delve = sim.getRuinsWorks().delve();
                String name = theme.name().toLowerCase(Locale.ROOT).replace('_', '-');
                RuinsDelve.Area vault = delve.vault().footprint();
                out.add(new SnapshotArtifact("inside/" + name + "-vault.png", BattleReviewFrameRenderer.look(
                        context.modRoot(), W, H, vault.centreX() + 0.5f, vault.centreY() + 0.5f, CLOSE_CELLS)
                        .withoutRoofs().render(sim, "")));
                List<RuinsDelve.Area> chambers = delve.interior().chambers();
                if (!chambers.isEmpty()) {
                    RuinsDelve.Area chamber = chambers.get(chambers.size() - 1);
                    out.add(new SnapshotArtifact("inside/" + name + "-chamber.png", BattleReviewFrameRenderer.look(
                            context.modRoot(), W, H, chamber.centreX() + 0.5f, chamber.centreY() + 0.5f,
                            CLOSE_CELLS).withoutRoofs().render(sim, "")));
                }
            }
        }
        return out;
    }

    /** The themes shown inside: one of each kind of prize, and a reactor site. */
    private static final List<RuinsVaultTheme> INSIDE = List.of(RuinsVaultTheme.CRYOARITHMETIC_ENGINE,
            RuinsVaultTheme.SERVER_HALL, RuinsVaultTheme.ARMORY, RuinsVaultTheme.FUSION_LAMP, RuinsVaultTheme.DEPOT);

    private static void advance(BattleSimulation sim, float seconds) {
        for (int t = 0; t < Math.round(seconds / BattleSimulation.TICK_DT); t++) sim.advance(BattleSimulation.TICK_DT);
    }

    /** Close on the middle battery, which stands before the middle entrance: centre x, centre y, cells across. */
    private static float[] frame(BattleSimulation sim, RuinsBatteryGuns batteries) {
        long gun = batteries.guns().get(batteries.guns().size() / 2);
        return new float[] {sim.world().x(gun), sim.world().y(gun), CLOSE_CELLS};
    }

    private static void report(BattleSimulation sim, RuinsBatteryGuns batteries) {
        StringBuilder line = new StringBuilder("  [ruins-themed-site-look] batteries " + batteries.state() + ":");
        for (long gun : batteries.guns()) {
            line.append(String.format(Locale.ROOT, " (%.0f, %.0f)%s", sim.world().x(gun), sim.world().y(gun),
                    sim.turretState().unpowered(gun) ? " cut" : " live"));
        }
        System.out.println(line);
    }
}
