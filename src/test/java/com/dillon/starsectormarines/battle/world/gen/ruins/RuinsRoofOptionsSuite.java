package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.EmplacementStyle;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
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

import java.util.List;

/**
 * The ruins interior's roof from above, for the owner's pick ({@code mapgen-nouns.md}, Ruins ground): an extensive
 * site, seed 7, a minute in, framed on the megastructure through the look preset with roofs on. {@code roof.png} is
 * the roof as the ground builds it; {@code roof-plate-mock.png} is the same frame with the megastructure's roof
 * drawn from the armour-plate row of the roof sheet, standing in for a dedicated megastructure roof set (the
 * interior's floors are marked with a plate works style, which changes nothing a roofed frame shows but the
 * roof).
 */
public final class RuinsRoofOptionsSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;

    @Override public String id() { return "ruins-roof-options"; }

    @Override public String label() { return "Ruins interior roof options: extensive, look preset, roofs on"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        // Two plays of the same battle: a frame's roof plans are bound to the map they first drew.
        return List.of(frame(context, "roof.png", false), frame(context, "roof-plate-mock.png", true));
    }

    private static SnapshotArtifact frame(SnapshotContext context, String name, boolean plate) throws Exception {
        try (BattleSimulation sim = BattleSetup.createRuins(7L, BattleSetup.defaultManifest(), false,
                OperationTier.VETERAN, RiskLevel.MEDIUM, RuinsGroundEvidence.profile(SurfacePalette.ARID),
                FlybyRoster.EMPTY, FlybyRoster.EMPTY, RuinsGrade.EXTENSIVE, RuinsOccupant.SCAVENGERS)) {
            for (int t = 0; t < Math.round(60f / BattleSimulation.TICK_DT); t++) sim.advance(BattleSimulation.TICK_DT);
            int width = sim.getGrid().getWidth(), height = sim.getGrid().getHeight();
            if (plate) {
                CellTopology topology = sim.getTopology();
                int style = EmplacementStyle.MODULAR.ordinal() + 1;
                for (int y = 0; y < height; y++) {
                    for (int x = width / 3; x < width; x++) {
                        if (topology.getBuildingId(x, y) != 0
                                && topology.getBuildingKindHint(x, y) == BuildingKind.FORTIFIED) {
                            topology.setWorksStyle(x, y, style);
                        }
                    }
                }
            }
            return new SnapshotArtifact(name, BattleReviewFrameRenderer.look(context.modRoot(), W, H,
                    width * 0.74f, height / 2f, width * 0.54f).render(sim, ""));
        }
    }
}
