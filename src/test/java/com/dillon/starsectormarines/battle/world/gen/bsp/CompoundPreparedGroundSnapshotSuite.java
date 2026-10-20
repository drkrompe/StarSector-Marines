package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.world.gen.CompoundPreparedGround;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
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

/**
 * Compound prepared ground on production-size Conquest maps (`compound-prepared-ground.md`): each sandbag apron a
 * compound's lookout stands in, at 1920x1080 and 100% scale, three ways at one framing — the same seed generated with
 * {@code battle.mapgen.compoundPreparedGround} off (the control), with it on, and with it on under the
 * fighting-position layer (the run the apron makes washed green, the lookout's stand cells outlined white). The
 * terrain comes from the ordinary battle renderer, so what is shown is what a player sees.
 */
public final class CompoundPreparedGroundSnapshotSuite implements SnapshotSuite {

    private static final long[] SEEDS = {1L, 2L, 3L};
    /** Aprons shown per seed. */
    private static final int PER_SEED = 2;
    private static final int CELL_PX = 40;
    private static final int VIEW_W = 1920 / CELL_PX;
    private static final int VIEW_H = 1080 / CELL_PX;
    private static final Color POSITION = new Color(80, 230, 110, 90);
    private static final Color POSITION_EDGE = new Color(80, 230, 110, 220);
    private static final Color PICKET = new Color(255, 255, 255, 235);

    @Override public String id() { return "compound-prepared-ground"; }
    @Override public String label() { return "Sandbag aprons at Conquest compounds, against the same seed without them"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        int number = 1;
        boolean was = CompoundPreparedGround.ENABLED;
        try {
            for (long seed : SEEDS) {
                CompoundPreparedGround.ENABLED = false;
                MapResult control = generate(seed);
                CompoundPreparedGround.ENABLED = true;
                MapResult laid = generate(seed);
                FightingPositionIndex index = FightingPositionIndex.build(laid.grid.getWidth(), laid.grid.getHeight(),
                        laid.doodads, laid.grid::isWalkable);
                int shown = 0;
                for (TacticalNode picket : pickets(laid)) {
                    if (shown++ >= PER_SEED) break;
                    float cx = (picket.left + picket.right + 1) * 0.5f;
                    float cy = (picket.top + picket.bottom + 1) * 0.5f;
                    String stem = String.format("%02d-seed%d-apron%d", number++, seed, shown);
                    out.add(new SnapshotArtifact(stem + "-a-control.png",
                            renderer.renderView(control, seed, cx, cy, VIEW_W, VIEW_H, CELL_PX)));
                    BufferedImage after = renderer.renderView(laid, seed, cx, cy, VIEW_W, VIEW_H, CELL_PX);
                    out.add(new SnapshotArtifact(stem + "-b-apron.png", after));
                    out.add(new SnapshotArtifact(stem + "-c-positions.png", overlay(after, index, picket, cx, cy)));
                }
            }
        } finally {
            CompoundPreparedGround.ENABLED = was;
        }
        return out;
    }

    private static MapResult generate(long seed) {
        return new BspCityGenerator().generate(BattleSetup.CONQUEST_GRID_W, BattleSetup.CONQUEST_GRID_H, seed,
                TraversalAxis.SOUTH_TO_NORTH);
    }

    /** The compound lookouts seated on an apron. */
    private static List<TacticalNode> pickets(MapResult map) {
        List<TacticalNode> out = new ArrayList<>();
        for (TacticalNode node : map.tacticalMap.ofKind(TacticalNode.Kind.GUARDPOST)) {
            if (!node.standPositions().isEmpty() && node.right - node.left + node.bottom - node.top >= 6) out.add(node);
        }
        return out;
    }

    private static BufferedImage overlay(BufferedImage base, FightingPositionIndex index, TacticalNode picket,
                                         float cx, float cy) {
        BufferedImage img = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.drawImage(base, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float left = cx - VIEW_W / 2f;
        float top = cy + VIEW_H / 2f;
        int cells = 0;
        for (int y = (int) Math.floor(top - VIEW_H); y <= (int) Math.ceil(top); y++) {
            for (int x = (int) Math.floor(left); x <= (int) Math.ceil(left + VIEW_W); x++) {
                if (index.runIdAt(x, y) < 0) continue;
                int px = Math.round((x - left) * CELL_PX);
                int py = Math.round((top - (y + 1)) * CELL_PX);
                g.setColor(POSITION);
                g.fillRect(px, py, CELL_PX, CELL_PX);
                g.setColor(POSITION_EDGE);
                g.drawRect(px, py, CELL_PX - 1, CELL_PX - 1);
                if (index.runIdAt(x, y) == index.runIdAt(picket.anchorX, picket.anchorY)) cells++;
            }
        }
        g.setStroke(new BasicStroke(3f));
        g.setColor(PICKET);
        for (TacticalNode.StandPosition stand : picket.standPositions()) {
            int px = Math.round((stand.x() - left) * CELL_PX);
            int py = Math.round((top - (stand.y() + 1)) * CELL_PX);
            g.drawRect(px + 4, py + 4, CELL_PX - 9, CELL_PX - 9);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        String label = "POSITION " + cells + " cells   LOOKOUT " + picket.garrisonSize + " on " + picket.standPositions().size()
                + " stand cells";
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRect(16, 16, g.getFontMetrics().stringWidth(label) + 24, 38);
        g.setColor(Color.WHITE);
        g.drawString(label, 28, 43);
        g.dispose();
        return img;
    }
}
