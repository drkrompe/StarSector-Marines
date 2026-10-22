package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.evacuation.SwarmStrains;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.battleview.PlayerViewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The question {@link FogOfWarService#BODY_REVEAL_PROPERTY} asks, through the player's camera: two
 * Matriarchs stand past the end of a wall with their centres just out of the marines' sight, the
 * near one a cell behind the sight line and the far one two, and the same moment is drawn under each
 * rule. A Matriarch the rule hides is outlined and labelled, so the frame says what the player is
 * not being shown. One frame per rule, 1920x1080. The owner picked {@code footprint}, which is the
 * default; the other two frames are its controls.
 */
public final class FogBodyRevealSnapshotSuite implements SnapshotSuite {

    private static final long SEED = 20261022L;
    private static final int W = 40, H = 22;
    private static final int WALL_X0 = 19, WALL_X1 = 20, WALL_Y1 = 12;
    private static final int TICKS = 45;

    private static final String[][] RULES = {
            {"centre", "01-centre-cell"},
            {"footprint", "02-footprint"},
            {"length", "03-drawn-length"},
    };

    @Override public String id() { return "fog-body-reveal"; }

    @Override public String label() {
        return "Fog: which part of a large body must be in sight for the player to see it";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        PlayerViewFrameRenderer renderer = new PlayerViewFrameRenderer(context.modRoot(), 1920, 1080);
        List<SnapshotArtifact> out = new ArrayList<>();
        String before = System.getProperty(FogOfWarService.BODY_REVEAL_PROPERTY);
        try {
            for (String[] rule : RULES) {
                System.setProperty(FogOfWarService.BODY_REVEAL_PROPERTY, rule[0]);
                try (BattleSimulation sim = scene()) {
                    for (int tick = 0; tick < TICKS; tick++) {
                        sim.advance(BattleSimulation.TICK_DT);
                        sim.getFogOfWar().advanceFade(BattleSimulation.TICK_DT);
                    }
                    String caption = String.format(Locale.ROOT,
                            "fog rule '%s'  |  both Matriarchs' centres are out of the marines' sight"
                                    + "  |  Matriarchs shown to the player: %d of 2", rule[0], shown(sim));
                    BufferedImage frame = renderer.render(sim, caption);
                    outlineHidden(frame, renderer, sim);
                    out.add(new SnapshotArtifact(rule[1] + ".png", frame));
                }
            }
        } finally {
            if (before == null) System.clearProperty(FogOfWarService.BODY_REVEAL_PROPERTY);
            else System.setProperty(FogOfWarService.BODY_REVEAL_PROPERTY, before);
        }
        return out;
    }

    private static int shown(BattleSimulation sim) {
        int shown = 0;
        for (int i = 0; i < sim.getRoster().liveCount(); i++) {
            if (sim.identity().type(sim.getRoster().get(i)) == UnitType.SWARM_MATRIARCH
                    && sim.getFogOfWar().getUnitVisibility(i) != FogOfWarService.VIS_HIDDEN) shown++;
        }
        return shown;
    }

    /** A dashed ring the size of its drawn length round each Matriarch the player is not shown. */
    private static void outlineHidden(BufferedImage frame, PlayerViewFrameRenderer renderer, BattleSimulation sim) {
        Graphics2D g = frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        float ring = UnitType.SWARM_MATRIARCH.renderScale * 0.5f * renderer.cellPx(sim);
        for (int i = 0; i < sim.getRoster().liveCount(); i++) {
            long unit = sim.getRoster().get(i);
            if (sim.identity().type(unit) != UnitType.SWARM_MATRIARCH
                    || sim.getFogOfWar().getUnitVisibility(i) != FogOfWarService.VIS_HIDDEN) continue;
            float[] p = renderer.cellToImage(sim, sim.world().renderX(unit), sim.world().renderY(unit));
            g.setColor(new Color(255, 96, 96, 230));
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f,
                    new float[]{12f, 9f}, 0f));
            g.drawOval(Math.round(p[0] - ring), Math.round(p[1] - ring), Math.round(ring * 2), Math.round(ring * 2));
            g.fillOval(Math.round(p[0]) - 4, Math.round(p[1]) - 4, 8, 8);
            g.drawString("Matriarch, hidden", Math.round(p[0] - ring), Math.round(p[1] + ring + 22));
        }
        g.dispose();
    }

    /** A street, a wall standing out of the north edge, two marines west of it, the Matriarch east of its end. */
    private static BattleSimulation scene() {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.STREET);
            }
        }
        for (int x = WALL_X0; x <= WALL_X1; x++) {
            for (int y = 0; y <= WALL_Y1; y++) {
                grid.setWalkable(x, y, false);
                topology.setWall(x, y, true);
                topology.setGroundKind(x, y, CellTopology.GroundKind.INDOOR);
            }
        }
        BattleSimulation sim = new BattleSimulation(grid, topology, SEED);
        sim.setMissionCompletionEnabled(false);
        for (int[] cell : new int[][]{{9, 5}, {9, 8}}) {
            sim.spawn(new EntitySpec("marine-" + cell[1], Faction.MARINE, UnitType.MARINE, cell[0], cell[1])
                    .role(UnitRole.STRUCTURE).moveSpeed(0f));
        }
        sim.spawn(SwarmStrains.spec("Matriarch near", UnitType.SWARM_MATRIARCH, 31, 17).moveSpeed(0f));
        sim.spawn(SwarmStrains.spec("Matriarch deep", UnitType.SWARM_MATRIARCH, 24, 12).moveSpeed(0f));
        return sim;
    }
}
