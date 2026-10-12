package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import java.util.ArrayList;
import java.util.List;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;

public final class CompoundPropDamageSnapshotSuite implements SnapshotSuite {
    public String id() { return "compound-prop-damage"; }
    public String label() { return "Compound equipment damage and wrecks"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        List<SnapshotArtifact> frames = new ArrayList<>();
        for (int group = 0; group < 2; group++) {
            frames.add(new SnapshotArtifact("compound-props-" + group + "-template.png",
                    CompoundPropDamageTemplate.render(context.projectRoot(), group, false)));
            frames.add(new SnapshotArtifact("compound-props-" + group + "-reference.png",
                    CompoundPropDamageTemplate.render(context.projectRoot(), group, true)));
        }
        for (int turn = 0; turn < 4; turn++) {
            try (BattleSimulation sim = ground(34, 14)) {
                for (int column = 0; column < 8; column++) for (int state = 0; state < 3; state++) {
                    int x = 2 + column * 4, y = 10 - state * 4;
                    place(sim, column, x, y, turn, turn >= 2, column % 2 == 0);
                    if (state > 0) sim.damageCell(x, y, state == 1 ? 20 : 100);
                }
                var map = new MapResult(sim.getGrid(), sim.getTopology(), 0, 0, 33, 13,
                        List.of(), sim.getDoodads());
                var renderer = new HeadlessBattleMapRenderer(context.modRoot());
                frames.add(new SnapshotArtifact("states-turn-" + turn + "-32px.png", renderer.render(map, 42, 32)));
                if (turn == 0) frames.add(new SnapshotArtifact("states-64px.png", renderer.render(map, 42, 64)));
            }
        }
        SnapshotArtifact effects = effectSequence(context);
        frames.add(effects);
        frames.add(new SnapshotArtifact("damaged-smoke.png", effects.frames().get(8)));
        frames.add(new SnapshotArtifact("destroyed-bursts.png", effects.frames().get(19)));
        frames.add(new SnapshotArtifact("settled-wrecks.png", effects.frames().get(50)));
        return frames;
    }

    private static BattleSimulation ground(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, y < height / 2 ? CellTopology.GroundKind.GRASS : CellTopology.GroundKind.INDOOR);
        }
        BattleSimulation sim = new BattleSimulation(grid, topology, 42);
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    private static void place(BattleSimulation sim, int index, int x, int y, int turn, boolean mirror, boolean blocks) {
        if (blocks) {
            sim.getGrid().setWalkable(x, y, false); sim.getGrid().setSeeThrough(x, y, true);
            sim.getTopology().setFixture(x, y, true);
        }
        sim.addDoodad(new Doodad(x, y, TileRegistry.installed().doodad(CompoundPropDamageTemplate.IDS[index]), turn, mirror));
    }

    /** State cues pass through production simulation wiring and the game's particle recipes. */
    private static SnapshotArtifact effectSequence(SnapshotContext context) throws Exception {
        List<BufferedImage> frames = new ArrayList<>();
        try (BattleSimulation sim = ground(22, 14)) {
            for (int i = 0; i < 8; i++) place(sim, i, 3 + i % 4 * 5, i < 4 ? 9 : 4, 0, false, true);
            BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(context.modRoot(), 1056, 672);
            ReviewImpactFx impacts = new ReviewImpactFx(context.modRoot());
            int smoke = 0, fire = 0, dust = 0;
            for (int tick = 0; tick <= 300; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                if (tick == 30 || tick == 105) for (int i = 0; i < 8; i++)
                    sim.damageCell(3 + i % 4 * 5, i < 4 ? 9 : 4, tick == 30 ? 20 : 80);
                smoke += sim.getSmokePuffsThisFrame().size();
                fire += sim.getFireBurstsThisFrame().size();
                dust += sim.getWallDustsThisFrame().size();
                impacts.feed(sim);
                if (tick % 6 == 0) {
                    BufferedImage rendered = renderer.render(sim,
                            tick < 30 ? "Intact compound equipment" : tick < 105 ? "Damaged: venting smoke; radar remains silent"
                                    : "Destroyed: electrical fire, ruptured-tank smoke and inert-metal dust",
                            ReviewAnnotations.NONE, impacts);
                    // An opaque caption band prevents old text showing through successive GIF frames.
                    BufferedImage opaque = new BufferedImage(rendered.getWidth(), rendered.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = opaque.createGraphics();
                    graphics.drawImage(rendered, 0, 0, null);
                    graphics.dispose();
                    frames.add(opaque);
                }
            }
            if (smoke == 0 || fire == 0 || dust == 0) throw new IllegalStateException("Missing production prop effect channel");
        }
        return SnapshotArtifact.colorAnimation("damage-effects.gif", frames, 200);
    }
}
