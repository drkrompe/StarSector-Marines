package com.dillon.starsectormarines.tools.tilesetauthoring;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.util.List;
import java.util.ArrayList;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;

public final class RockDamageSnapshotSuite implements SnapshotSuite {
    public String id() { return "rock-damage"; }
    public String label() { return "Cracked rocks and shattered rubble"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        List<SnapshotArtifact> frames = new ArrayList<>(List.of(
                new SnapshotArtifact("rock-damage-template.png", RockDamageReskinTemplate.render(context.projectRoot(), false)),
                new SnapshotArtifact("rock-reference.png", RockDamageReskinTemplate.render(context.projectRoot(), true))));
        NavigationGrid grid = new NavigationGrid(23, 11);
        CellTopology topology = new CellTopology(23, 11);
        for (int y = 0; y < 11; y++) for (int x = 0; x < 23; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
        }
        String[] ids = {"small-1", "small-2", "medium-1", "medium-2", "large-1", "large-2", "large-3"};
        for (int i = 0; i < ids.length; i++) for (int state = 0; state < 3; state++) {
            int x = 2 + i * 3, y = 8 - state * 3;
            var def = TileRegistry.installed().tile("nature.rock-" + ids[i]);
            topology.setNatureOverlayIndex(x, y, def.index);
            if (!def.passable) {
                grid.setWalkable(x, y, false); grid.setSeeThrough(x, y, true);
                topology.setFixture(x, y, true);
            }
        }
        DoodadService service = new DoodadService(grid);
        service.addNatureOverlayCover(topology, TileRegistry.installed());
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            MapEditor editor = new MapEditor(navigation); editor.setDoodads(service);
            for (int i = 0; i < ids.length; i++) {
                editor.damageWall(2 + i * 3, 5, 1);
                editor.damageWall(2 + i * 3, 2, 999);
            }
            var renderer = new HeadlessBattleMapRenderer(context.modRoot());
            var map = new MapResult(grid, topology, 0, 0, 22, 10, List.of(), service.getDoodads());
            frames.add(new SnapshotArtifact("rock-states-32px.png", renderer.render(map, 42, 32)));
            frames.add(new SnapshotArtifact("rock-states-64px.png", renderer.render(map, 42, 64)));
        }
        return frames;
    }
}
