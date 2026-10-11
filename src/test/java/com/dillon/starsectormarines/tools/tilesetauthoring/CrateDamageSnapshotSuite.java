package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.tools.snapshot.*;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import java.util.List;
import java.util.ArrayList;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;

public final class CrateDamageSnapshotSuite implements SnapshotSuite {
    public String id() { return "crate-damage"; }
    public String label() { return "Broken crate panels and scattered contents"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        List<SnapshotArtifact> frames = new ArrayList<>(List.of(
                new SnapshotArtifact("crate-damage-template.png", CrateDamageReskinTemplate.render(context.projectRoot(), false)),
                new SnapshotArtifact("crate-reference.png", CrateDamageReskinTemplate.render(context.projectRoot(), true))));
        NavigationGrid grid = new NavigationGrid(14, 11);
        CellTopology topology = new CellTopology(14, 11);
        for (int y = 0; y < 11; y++) for (int x = 0; x < 14; x++) {
            grid.setWalkableFloor(x, y); topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
        }
        DoodadService service = new DoodadService(grid);
        String[] ids = {"doodad.box", "doodad.crate", "doodad.industrial-crate-stack", "doodad.box-dam"};
        for (int i = 0; i < ids.length; i++) for (int state = 0; state < 3; state++) {
            int x = 2 + i * 3, y = 8 - state * 3;
            if (i == 2) {
                grid.setWalkable(x, y, false); grid.setSeeThrough(x, y, true);
                topology.setFixture(x, y, true);
            }
            service.addDoodad(new Doodad(x, y, TileRegistry.installed().doodad(ids[i]), i));
        }
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            MapEditor editor = new MapEditor(navigation); editor.setDoodads(service);
            for (int i = 0; i < ids.length; i++) {
                editor.damageWall(2 + i * 3, 5, 1);
                editor.damageWall(2 + i * 3, 2, 100);
            }
            var renderer = new HeadlessBattleMapRenderer(context.modRoot());
            var map = new MapResult(grid, topology, 0, 0, 13, 10, List.of(), service.getDoodads());
            frames.add(new SnapshotArtifact("crate-states-32px.png", renderer.render(map, 42, 32)));
            frames.add(new SnapshotArtifact("crate-states-64px.png", renderer.render(map, 42, 64)));
        }
        return frames;
    }
}
