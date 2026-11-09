package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.door.HardenedWalls;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
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
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * The vault's locked door and hardened walls ({@code ruins-expedition.md}, Works; {@code garrisonables.md}, Locked doors and
 * hardened walls) through the look preset at 1920x1080 on an empty scattered site, centred on the vault's inner door:
 * the door locked with the hall cold; the same frame with every hardened cell tinted, a review overlay that the game
 * never draws; the door powered once the hall runs and its junction feeds it, its lamp amber while it waits on
 * whatever access its site drew; and the door open once that access is met. The power is routed through the works'
 * own rules. {@code -Dsnapshot.ruinsVaultLock.seed} picks the site (default 31). Opt-in:
 * {@code -Psnapshot=ruins-vault-lock-look}.
 */
public final class RuinsVaultLockLookSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    private static final float ZOOM = BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 0.75f;

    @Override public String id() { return "ruins-vault-lock-look"; }

    @Override public String label() { return "Ruins works: the vault's locked door and hardened walls, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        long seed = Long.getLong("snapshot.ruinsVaultLock.seed", 31L);
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = BattleSetup.createRuins(seed, BattleSetup.defaultManifest(), false,
                OperationTier.FIRST_CONTRACT, RiskLevel.MEDIUM, profile(), FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                RuinsGrade.SCATTERED, RuinsOccupant.NOBODY)) {
            for (int t = 0; t < Math.round(5f / BattleSimulation.TICK_DT); t++) sim.advance(BattleSimulation.TICK_DT);
            RuinsWorks works = sim.getRuinsWorks();
            SealDoor seal = null;
            for (SealDoor door : works.doors()) if (door.id().equals(RuinsWorks.VAULT_SEAL)) seal = door;
            if (seal == null) throw new IllegalStateException("the site's works have no vault seal");
            float cx = seal.middle()[0] + .5f, cy = seal.middle()[1] + .5f;
            BufferedImage locked = frame(context, sim, cx, cy);
            out.add(new SnapshotArtifact("01-vault-door-locked.png", locked));
            out.add(new SnapshotArtifact("02-hardened-walls-tinted.png", tinted(context, locked, sim, cx, cy)));
            works.graph().restart();
            if (works.access() == VaultAccess.CROSSED_FEED) {
                works.graph().throwJunction(works.junctions().size() - 1, PowerGraph.Branch.STORE);
            }
            sim.advance(BattleSimulation.TICK_DT);
            out.add(new SnapshotArtifact("03-vault-door-powered.png", frame(context, sim, cx, cy)));
            // Meet whatever access the site drew: keys turned and code entered (each a no-op where unneeded).
            works.keys.tick(true, true, true, VaultKeys.TURN_SECONDS);
            works.code.tick(true, true, true, VaultCode.ENTER_SECONDS);
            sim.advance(BattleSimulation.TICK_DT);
            out.add(new SnapshotArtifact("04-vault-door-open.png", frame(context, sim, cx, cy)));
        }
        return out;
    }

    private static BufferedImage frame(SnapshotContext context, BattleSimulation sim, float cx, float cy)
            throws Exception {
        return BattleReviewFrameRenderer.look(context.modRoot(), W, H, cx, cy, ZOOM).withoutRoofs().render(sim, "");
    }

    /** A copy of the frame with every hardened cell in view washed red, placed through the frame's own camera. */
    private static BufferedImage tinted(SnapshotContext context, BufferedImage frame, BattleSimulation sim, float cx,
                                        float cy) throws Exception {
        BattleCamera camera = BattleReviewFrameRenderer.look(context.modRoot(), W, H, cx, cy, ZOOM).withoutRoofs()
                .cameraOf(sim);
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(frame, 0, 0, null);
        g.setColor(new Color(230, 40, 40, 110));
        NavigationGrid grid = sim.getGrid();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!HardenedWalls.hardened(grid, x, y)) continue;
                int left = Math.round(camera.cellToScreenX(x)), right = Math.round(camera.cellToScreenX(x + 1));
                int top = Math.round(H - camera.cellToScreenY(y + 1)), bottom = Math.round(H - camera.cellToScreenY(y));
                if (right < 0 || left > W || bottom < 0 || top > H) continue;
                g.fillRect(left, top, right - left, bottom - top);
            }
        }
        g.dispose();
        return out;
    }

    private static TargetProfile profile() {
        return new TargetProfile(0, 0, 0, 0, "", EnumSet.noneOf(EconomicFunction.class), SurfacePalette.ARID,
                SettlementLink.NONE, 0f, 0f, false, false, false, GroundDefences.NONE);
    }
}
