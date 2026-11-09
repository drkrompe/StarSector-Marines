package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Branch;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Consumer;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsGroundPlan;
import com.dillon.starsectormarines.campaign.ruins.RuinsDelve;
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
 * The owner's question on the vault's seal ({@code ruins-works.md}), through the look preset at 1920x1080 on an empty
 * scattered site, the company landed and the vault unbreached: (a) the vault's inner door shut as a seal door, as the
 * works shut a store, so power or a charge is the way in; (b) the vault as it stands now, open, its seal only the
 * breach's length. The seal is shut through the works' own install. Opt-in: {@code -Psnapshot=ruins-vault-seal-options}.
 */
public final class RuinsVaultSealOptionsSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    private static final long SEED = 31L;

    @Override public String id() { return "ruins-vault-seal-options"; }

    @Override public String label() { return "Ruins works: the vault's inner door sealed, or open as now"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        RuinsGrade grade = RuinsGrade.SCATTERED;
        MapResult ground = new BspCityGenerator().generateRuins(SEED, profile(),
                RuinsGroundPlan.forLayers(RuinsDelve.of(grade).layers()));
        var vault = ground.ruinsDelve.vault();
        List<SnapshotArtifact> out = new ArrayList<>();
        for (boolean sealed : new boolean[]{true, false}) {
            try (BattleSimulation sim = BattleSetup.createRuins(SEED, BattleSetup.defaultManifest(), false,
                    OperationTier.FIRST_CONTRACT, RiskLevel.MEDIUM, profile(), FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                    grade, RuinsOccupant.NOBODY)) {
                if (sealed) seal(sim, vault.innerDoor().x(), vault.innerDoor().y(), vault.source().x(), vault.source().y());
                for (int t = 0; t < Math.round(5f / BattleSimulation.TICK_DT); t++) sim.advance(BattleSimulation.TICK_DT);
                String name = sealed ? "a-sealed.png" : "b-open-as-now.png";
                out.add(new SnapshotArtifact(name, BattleReviewFrameRenderer.look(context.modRoot(), W, H,
                        vault.innerDoor().x(), vault.innerDoor().y(), BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 0.75f)
                        .withoutRoofs().render(sim, "")));
            }
        }
        return out;
    }

    /** Shuts the inner door as the works shut any seal door, on a works of its own that is never powered. */
    private static void seal(BattleSimulation sim, int x, int y, int sourceX, int sourceY) {
        SealDoor door = new SealDoor(RuinsWorks.VAULT_SEAL, "vault seal", new int[][]{{x, y}}, sourceX, sourceY,
                new int[]{x, y, x, y}, false);
        new RuinsWorks(new PowerGraph(1, List.of(Branch.ONWARD), List.of(new Consumer(RuinsWorks.VAULT_SEAL, 0,
                Branch.ONWARD))), new RuinsWorks.Site(RuinsWorks.HALL, "hall", 0, 0),
                List.of(new RuinsWorks.Site(RuinsWorks.JUNCTION + 0, "junction", 0, 0)), List.of(door), List.of(),
                null, null).install(sim);
    }

    private static TargetProfile profile() {
        return new TargetProfile(0, 0, 0, 0, "", EnumSet.noneOf(EconomicFunction.class), SurfacePalette.ARID,
                SettlementLink.NONE, 0f, 0f, false, false, false, GroundDefences.NONE);
    }
}
