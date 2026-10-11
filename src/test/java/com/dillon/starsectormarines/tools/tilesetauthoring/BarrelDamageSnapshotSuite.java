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

public final class BarrelDamageSnapshotSuite implements SnapshotSuite {
    public String id() { return "barrel-damage"; }
    public String label() { return "Dented barrels and scattered metal"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        List<SnapshotArtifact> frames = new ArrayList<>(List.of(
                new SnapshotArtifact("barrel-damage-template.png", BarrelDamageReskinTemplate.render(context.projectRoot(), false)),
                new SnapshotArtifact("barrel-reference.png", BarrelDamageReskinTemplate.render(context.projectRoot(), true))));
        NavigationGrid grid = new NavigationGrid(18, 14);
        CellTopology topology = new CellTopology(18, 14);
        for (int y = 0; y < 14; y++) for (int x = 0; x < 18; x++) {
            grid.setWalkableFloor(x, y); topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
        }
        DoodadService service = new DoodadService(grid);
        for (int column = 0; column < 4; column++) for (int state = 0; state < 3; state++) {
            int x = 2 + column * 4, y = 10 - state * 4;
            String id = BarrelDamageReskinTemplate.IDS[column % 2];
            Doodad prop = new Doodad(x, y, TileRegistry.installed().doodad(id), column, column >= 2);
            if (column >= 2) {
                for (int dy = 0; dy < prop.footprintCellsY; dy++) for (int dx = 0; dx < prop.footprintCellsX; dx++) {
                    grid.setWalkable(x + dx, y + dy, false); grid.setSeeThrough(x + dx, y + dy, true);
                    topology.setFixture(x + dx, y + dy, true);
                }
            }
            if (column == 3) service.addDoodad(new Doodad(x, y,
                    TileRegistry.installed().doodad("main-stores.pallet"), column, true));
            service.addDoodad(prop);
        }
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            MapEditor editor = new MapEditor(navigation); editor.setDoodads(service);
            for (int column = 0; column < 4; column++) {
                editor.damageWall(2 + column * 4, 6, 1);
                editor.damageWall(2 + column * 4, 2, 100);
            }
            var renderer = new HeadlessBattleMapRenderer(context.modRoot());
            var map = new MapResult(grid, topology, 0, 0, 17, 13, List.of(), service.getDoodads());
            frames.add(new SnapshotArtifact("barrel-states-32px.png", renderer.render(map, 42, 32)));
            frames.add(new SnapshotArtifact("barrel-states-64px.png", renderer.render(map, 42, 64)));
        }
        return frames;
    }
}
