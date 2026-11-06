package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.util.ArrayList;
import java.util.List;

/** The residential sofas a tank drives through, damaged through the ordinary simulation path. */
public final class ResidentialSofaDamageSnapshotSuite implements SnapshotSuite {
    public String id() { return "residential-sofa-damage"; }
    public String label() { return "Residential sofas: intact, slashed, flattened"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        List<SnapshotArtifact> frames = new ArrayList<>();
        List<String> ids = List.of("doodad.residential-sofa-h", "doodad.residential-sofa-back-s",
                "doodad.residential-sofa-v", "doodad.residential-sofa-back-e");
        for (int turn = 0; turn < 4; turn++) {
            NavigationGrid grid = new NavigationGrid(17, 13);
            CellTopology topology = new CellTopology(17, 13);
            for (int y = 0; y < 13; y++) for (int x = 0; x < 17; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.INDOOR);
            }
            try (BattleSimulation sim = new BattleSimulation(grid, topology, 42)) {
                for (int col = 0; col < ids.size(); col++) for (int state = 0; state < 3; state++) {
                    int x = 1 + col * 4, y = 9 - state * 4;
                    Doodad sofa = new Doodad(x, y, TileRegistry.installed().doodad(ids.get(col)), turn, false);
                    for (int dy = 0; dy < sofa.footprintCellsY; dy++) for (int dx = 0; dx < sofa.footprintCellsX; dx++) {
                        grid.setWalkable(x + dx, y + dy, false);
                        topology.setFixture(x + dx, y + dy, true);
                    }
                    sim.addDoodad(sofa);
                    if (state > 0) sim.damageCell(x, y, state == 1 ? 20 : 100);
                }
                var map = new MapResult(grid, topology, 0, 0, 16, 12, List.of(), sim.getDoodads());
                var renderer = new HeadlessBattleMapRenderer(context.modRoot());
                frames.add(new SnapshotArtifact("states-turn-" + turn + "-32px.png", renderer.render(map, 42, 32)));
                if (turn == 0) frames.add(new SnapshotArtifact("states-64px.png", renderer.render(map, 42, 64)));
            }
        }
        return frames;
    }
}
