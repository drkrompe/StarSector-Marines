package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

public final class GroundDamageSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "ground-damage"; }
    @Override public String label() { return "Material-matched ground damage"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        NavigationGrid grid = new NavigationGrid(18, 15);
        CellTopology topology = new CellTopology(18, 15);
        for (int y = 0; y < 15; y++) for (int x = 0; x < 18; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, CellTopology.GroundKind.INDOOR);
            int family = (2 - y / 5) * 3 + x / 6;
            topology.setSurface(x, y, topology.surfaceIndex("colony." + GroundDamageReskinTemplate.MATERIALS[family]));
        }
        MapResult map = new MapResult(grid, topology, 0, 0, 17, 14, List.of(), List.of());
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        BufferedImage before = renderer.render(map, 42, 40);
        MapEditor editor = new MapEditor(new NavigationService(grid, topology));
        for (int y = 0; y < 15; y++) for (int x = 0; x < 18; x++) {
            // A compact patch within each 6x5 material field, with intact neighbors on all sides.
            if (x % 6 >= 2 && x % 6 <= 3 && y % 5 >= 1 && y % 5 <= 3) editor.damageGround(x, y);
        }
        BufferedImage after = renderer.render(map, 42, 40);
        BufferedImage comparison = new BufferedImage(before.getWidth() * 2 + 16, before.getHeight() + 62,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = comparison.createGraphics();
        g.setColor(new Color(0x191D20)); g.fillRect(0, 0, comparison.getWidth(), comparison.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18)); g.setColor(new Color(0xE9E1CE));
        g.drawString("INTACT", 16, 25); g.drawString("DAMAGED / same floor, localized fractures", before.getWidth() + 32, 25);
        g.drawImage(before, 0, 40, null); g.drawImage(after, before.getWidth() + 16, 40, null);
        g.dispose();
        return List.of(new SnapshotArtifact("ground-damage-template.png", GroundDamageReskinTemplate.render()),
                new SnapshotArtifact("ground-damage-comparison.png", comparison),
                new SnapshotArtifact("ground-damage.png", after));
    }
}
