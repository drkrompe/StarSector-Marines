package com.dillon.starsectormarines.tools.forestauthoring;

import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.tools.tilesetauthoring.TreeDamageReskinTemplate;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainBrush;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import java.util.ArrayList;
import java.util.List;

public final class TreeDamageSnapshotSuite implements SnapshotSuite {
    public String id() { return "tree-damage"; }
    public String label() { return "Damaged trees and fallen branches"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        List<SnapshotArtifact> frames = new ArrayList<>(List.of(new SnapshotArtifact("tree-damaged-template.png", TreeDamageReskinTemplate.render(false)),
                new SnapshotArtifact("tree-destroyed-template.png", TreeDamageReskinTemplate.render(true))));
        NavigationGrid grid = new NavigationGrid(28, 50);
        CellTopology topology = new CellTopology(28, 50);
        ground(grid, topology);
        List<ForestField.Tree> trees = new ArrayList<>();
        for (TreeCanopy tree : TreeCanopy.values()) for (int state = 0; state < 3; state++)
            trees.add(new ForestField.Tree(2 + state * 9, 43 - tree.ordinal() * 8, tree, tree.ordinal() % 4));
        List<Doodad> props = new ArrayList<>();
        ForestBrush.stampTrees(trees, grid, topology, props, null, false);
        DoodadService service = new DoodadService(grid);
        props.forEach(service::addDoodad);
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            MapEditor editor = new MapEditor(navigation); editor.setDoodads(service);
            for (int i = 0; i < trees.size(); i++) {
                var tree = trees.get(i);
                if (i % 3 > 0) editor.damageWall(tree.x() + tree.width() / 2, tree.y() + tree.height() / 2, i % 3 == 1 ? 50 : 200);
            }
            frames.add(new SnapshotArtifact("tree-states.png", renderer.render(
                    new MapResult(grid, topology, 0, 0, 27, 49, List.of(), service.getDoodads()), 42, 32)));
        }
        grove(frames, renderer);
        return frames;
    }

    private static void ground(NavigationGrid grid, CellTopology topology) {
        for (int y = 0; y < grid.getHeight(); y++) for (int x = 0; x < grid.getWidth(); x++) {
            grid.setWalkableFloor(x, y); topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
        }
    }
    private static void grove(List<SnapshotArtifact> frames, HeadlessBattleMapRenderer renderer) throws Exception {
        NavigationGrid grid = new NavigationGrid(34, 22); CellTopology topology = new CellTopology(34, 22);
        ground(grid, topology);
        TerrainBrush.stamp(new TerrainPlan(34, 22, TerrainPlan.Finish.GRASS_TOP, 1, 0, 7,
                List.of(new TerrainPlan.Stroke(TerrainPlan.Mode.RAISE, 7, List.of(new TerrainPlan.Point(25, 11))))), grid, topology);
        List<Doodad> props = new ArrayList<>();
        ForestBrush.stampTrees(List.of(new ForestField.Tree(3, 8, TreeCanopy.OAK, 0),
                new ForestField.Tree(6, 9, TreeCanopy.BIRCH, 0), new ForestField.Tree(8, 6, TreeCanopy.OAK_SMALL, 1),
                new ForestField.Tree(23, 9, TreeCanopy.PINE_LARGE, 0)), grid, topology, props, null, true);
        DoodadService service = new DoodadService(grid); props.forEach(service::addDoodad);
        MapResult map = new MapResult(grid, topology, 0, 0, 33, 21, List.of(), service.getDoodads());
        frames.add(new SnapshotArtifact("grove-intact.png", renderer.render(map, 42, 32)));
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            MapEditor editor = new MapEditor(navigation); editor.setDoodads(service);
            editor.damageWallsInRadius(5, 10, 2, 50, null);
            editor.damageWall(24, 10, 50);
            frames.add(new SnapshotArtifact("grove-damaged.png", renderer.render(map, 42, 32)));
            editor.damageWallsInRadius(5, 10, 2, 200, null);
            editor.damageWall(24, 10, 200);
            if (!grid.isWalkable(4, 10) || grid.isWalkable(24, 10) || grid.isWalkable(9, 7))
                throw new AssertionError("Tree breach lost its overlap or terrain ownership");
            frames.add(new SnapshotArtifact("grove-destroyed.png", renderer.render(map, 42, 32)));
        }
    }
}
