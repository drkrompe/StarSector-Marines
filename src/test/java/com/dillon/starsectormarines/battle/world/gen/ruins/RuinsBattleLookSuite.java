package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;
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
 * A Ruins battle as {@code BattleSetup.createRuins} builds it, on its ruins ground ({@code mapgen-nouns.md}, Ruins ground),
 * through the look preset at 1920x1080: per grade, the whole map and the landing at play zoom once the company is
 * down, the vault, and the interior with its roofs off. The scavengers hold the site; the default manifest lands. Played
 * {@code -Dsnapshot.ruinsBattle.seconds} (default 60) unrecorded first; {@code -Dsnapshot.ruinsBattle.surface}
 * picks the world (default ARID).
 */
public final class RuinsBattleLookSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;

    @Override public String id() { return "ruins-battle-look"; }

    @Override public String label() { return "Ruins battle: createRuins on its ruins ground, every grade, look preset"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        SurfacePalette surface = SurfacePalette.valueOf(
                System.getProperty("snapshot.ruinsBattle.surface", "ARID").toUpperCase(Locale.ROOT));
        float seconds = Float.parseFloat(System.getProperty("snapshot.ruinsBattle.seconds", "60"));
        long seed = Long.getLong("snapshot.ruinsBattle.seed", 7L);
        List<SnapshotArtifact> out = new ArrayList<>();
        for (RuinsGrade grade : RuinsGrade.values()) {
            OperationTier tier = switch (grade) {
                case SCATTERED -> OperationTier.FIRST_CONTRACT;
                case WIDESPREAD -> OperationTier.ESTABLISHED;
                case EXTENSIVE -> OperationTier.VETERAN;
                case VAST -> OperationTier.REINFORCED;
            };
            try (BattleSimulation sim = BattleSetup.createRuins(seed, BattleSetup.defaultManifest(), false, tier,
                    RiskLevel.MEDIUM, RuinsGroundEvidence.profile(surface), FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                    grade, RuinsOccupant.SCAVENGERS)) {
                for (int t = 0; t < Math.round(seconds / BattleSimulation.TICK_DT); t++) sim.advance(BattleSimulation.TICK_DT);
                int width = sim.getGrid().getWidth(), height = sim.getGrid().getHeight();
                String dir = String.format("%d-%s/", grade.ordinal() + 1, grade.name().toLowerCase(Locale.ROOT));
                out.add(new SnapshotArtifact(dir + "01-whole.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        W, H, width / 2f, height / 2f, width * 1.04f).render(sim, "")));
                out.add(new SnapshotArtifact(dir + "02-landing.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        W, H, 22f, height / 2f, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, "")));
                out.add(new SnapshotArtifact(dir + "03-vault.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        W, H, width - 16f, height / 2f, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 1.5f)
                        .render(sim, "")));
                // The interior's east half with the roofs taken off: its halls, corridors and chambers.
                out.add(new SnapshotArtifact(dir + "04-interior.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        W, H, width * 0.78f, height / 2f, width * 0.46f).withoutRoofs().render(sim, "")));
            }
        }
        return out;
    }
}
