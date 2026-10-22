package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.creep.CreepGrowth;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The Rav strains' shadows at play zoom (32 px per cell, 1920x1080): a Runner pack of every
 * growth stage, a Brute and the Matriarch roving over grass on the left and living creep
 * on the right, each casting the one ellipse laid along its body ({@code SwarmFootprint}).
 */
public final class RavShadowSnapshotSuite implements SnapshotSuite {

    private static final int W = 64, H = 36;
    private static final int TICKS = 120, FRAME_EVERY = 2, STILL_TICK = 60;

    @Override public String id() { return "rav-shadows"; }

    @Override public String label() { return "Rav Runner, Brute and Matriarch shadows at play zoom, on ground and creep"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
            }
        }
        SceneWorld world = SceneBuilder.over(grid, topology).seed(20261021L)
                .missionCompletion(false).build();
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = world.sim()) {
            CreepField creep = sim.creepField();
            creep.putSource(1L, 52, 18, new CreepGrowth(8f, 22f));
            for (int t = 0; t < 600; t++) creep.tick(t);
            for (int side = 0; side < 2; side++) {
                int x0 = side == 0 ? 8 : 40;
                SwarmStage[] stages = {SwarmStage.WHELP, SwarmStage.ADULT, SwarmStage.ADULT, SwarmStage.ELDER};
                for (int i = 0; i < stages.length; i++) {
                    SwarmStrains.spawn(sim, "Runner " + side + i, UnitType.SWARM_RUNNER, x0 + 2 * i, 8, stages[i]);
                }
                SwarmStrains.spawn(sim, "Brute " + side, UnitType.SWARM_BRUTE, x0 + 3, 15);
                SwarmStrains.spawn(sim, "Matriarch " + side, UnitType.SWARM_MATRIARCH, x0 + 8, 25);
            }
            // No Marines stand here to see the strains, so the frame's own sight reveals the ground.
            for (int x = 8; x < W; x += 16) sim.getFogOfWar().addEphemeralSource(x, H / 2, 28, 28f);
            BattleReviewFrameRenderer camera = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080,
                    W / 2f, H / 2f, 60f).withUnitShadows();
            List<BufferedImage> frames = new ArrayList<>();
            for (int tick = 0; tick <= TICKS; tick++) {
                if (tick > 0) sim.advance(BattleSimulation.TICK_DT);
                if (tick % FRAME_EVERY != 0) continue;
                BufferedImage frame = camera.render(sim, "RAV SHADOWS / one ellipse along each body / "
                        + "Runner pack (whelp, adults, elder), Brute, Matriarch / grass left, creep right / "
                        + "32 px per cell / tick " + tick);
                frames.add(frame);
                if (tick == STILL_TICK) out.add(new SnapshotArtifact("rav-shadows.png", frame));
            }
            out.add(SnapshotArtifact.colorAnimation("rav-shadows.gif", frames, Math.round(FRAME_EVERY * 1000f / 30f)));
        }
        return out;
    }
}
