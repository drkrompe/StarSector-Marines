package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Branch;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
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
import java.util.EnumSet;
import java.util.List;

/**
 * A ruins site's works ({@code ruins-works.md}) through the look preset at 1920x1080, centred on the first layer's store door, on a widespread site with nobody
 * holding it: the first layer's sealed store and the seal gate shut, then the hall restarted and the first junction
 * thrown to the store, so the store stands open while the gate, on the way on, stays shut. The power is routed through the works'
 * own rules ({@link RuinsWorks#settle}), not drawn; the roofs are off so the interior shows. {@code -Dsnapshot.ruinsWorks.seed} picks the site (default 31).
 */
public final class RuinsWorksLookSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;

    @Override public String id() { return "ruins-works-look"; }

    @Override public String label() { return "Ruins works: a sealed store shut, then powered open, look preset"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        long seed = Long.getLong("snapshot.ruinsWorks.seed", 31L);
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = BattleSetup.createRuins(seed, BattleSetup.defaultManifest(), false,
                OperationTier.ESTABLISHED, RiskLevel.MEDIUM, profile(), FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                RuinsGrade.WIDESPREAD, RuinsOccupant.NOBODY)) {
            for (int t = 0; t < Math.round(5f / BattleSimulation.TICK_DT); t++) sim.advance(BattleSimulation.TICK_DT);
            RuinsWorks works = sim.getRuinsWorks();
            SealDoor store = works.doors().get(0);
            float cx = store.middle()[0];
            float cy = store.middle()[1];
            out.add(new SnapshotArtifact("01-sealed.png", BattleReviewFrameRenderer.look(context.modRoot(), W, H,
                    cx, cy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).withoutRoofs().render(sim, "")));
            works.graph().restart();
            works.graph().throwJunction(0, Branch.STORE);
            sim.advance(BattleSimulation.TICK_DT);
            out.add(new SnapshotArtifact("02-store-powered-open.png", BattleReviewFrameRenderer.look(context.modRoot(),
                    W, H, cx, cy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).withoutRoofs().render(sim, "")));
        }
        return out;
    }

    private static TargetProfile profile() {
        return new TargetProfile(0, 0, 0, 0, "", EnumSet.noneOf(EconomicFunction.class), SurfacePalette.ARID,
                SettlementLink.NONE, 0f, 0f, false, false, false, GroundDefences.NONE);
    }
}
