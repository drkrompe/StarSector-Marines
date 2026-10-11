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

public final class RubbleSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "wall-rubble"; }
    @Override public String label() { return "Material-matched wall collapse"; }
    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        NavigationGrid grid = new NavigationGrid(30, 14);
        CellTopology topology = new CellTopology(30, 14);
        String[] families = {"masonry", "ceramic", "olive"};
        String[] floors = {"limestone", "sage", "concrete"};
        for (int y = 0; y < 14; y++) for (int x = 0; x < 30; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, CellTopology.GroundKind.INDOOR);
            topology.setSurface(x, y, topology.surfaceIndex("colony." + floors[x / 10]));
        }
        for (int f = 0; f < 3; f++) {
            int left = f * 10;
            String material = "colony." + families[f] + "-wall";
            for (int x = 1; x <= 8; x++) wall(grid, topology, left + x, 11, material);
            for (int x = 1; x <= 4; x++) wall(grid, topology, left + x, 7, material);
            for (int y = 4; y <= 6; y++) wall(grid, topology, left + 1, y, material);
            wall(grid, topology, left + 7, 6, material);
            for (int x = 3; x <= 7; x++) wall(grid, topology, left + x, 2, material);
            wall(grid, topology, left + 5, 1, material);
            wall(grid, topology, left + 5, 3, material);
        }
        MapResult map = new MapResult(grid, topology, 0, 0, 29, 13, List.of(), List.of());
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        BufferedImage before = renderer.render(map, 42, 40);
        MapEditor editor = new MapEditor(new NavigationService(grid, topology));
        for (int f = 0; f < 3; f++) {
            int left = f * 10;
            for (int x = 3; x <= 6; x++) editor.damageWall(left + x, 11, 100);
            editor.damageWall(left + 1, 7, 100);
            editor.damageWall(left + 2, 7, 100);
            editor.damageWall(left + 1, 6, 100);
            editor.damageWall(left + 7, 6, 100);
            for (int x = 3; x <= 7; x++) editor.damageWall(left + x, 2, 100);
            editor.damageWall(left + 5, 1, 100);
            editor.damageWall(left + 5, 3, 100);
        }
        BufferedImage after = renderer.render(map, 42, 40);
        BufferedImage comparison = new BufferedImage(before.getWidth(), before.getHeight() * 2 + 72,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = comparison.createGraphics();
        g.setColor(new Color(0x191D20)); g.fillRect(0, 0, comparison.getWidth(), comparison.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18)); g.setColor(new Color(0xE9E1CE));
        g.drawString("INTACT     /     MASONRY                                    CERAMIC                                    OLIVE", 16, 25);
        g.drawImage(before, 0, 36, null);
        g.drawString("BREACHED     /     straight runs, corners, piers and junctions", 16, before.getHeight() + 61);
        g.drawImage(after, 0, before.getHeight() + 72, null); g.dispose();
        return List.of(new SnapshotArtifact("rubble-reskin-template.png", RubbleReskinTemplate.render()),
                new SnapshotArtifact("wall-collapse-comparison.png", comparison),
                new SnapshotArtifact("wall-collapse.png", after));
    }

    private static void wall(NavigationGrid grid, CellTopology topology, int x, int y, String material) {
        grid.setWalkable(x, y, false); grid.setWallHp(x, y, 100);
        topology.setWall(x, y, true); topology.setSurface(x, y, topology.surfaceIndex(material));
    }
}
