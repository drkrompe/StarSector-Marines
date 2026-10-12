package com.dillon.starsectormarines.tools.tilesetauthoring;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import java.util.ArrayList;
import java.util.List;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
public final class FurnishingDamageSnapshotSuite implements SnapshotSuite {
    public String id() { return "furnishing-damage"; }
    public String label() { return "Compound yard stock and furniture damage"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        List<SnapshotArtifact> frames = new ArrayList<>();
        for (int group = 0; group < 2; group++) for (boolean reference : new boolean[]{false,true})
            frames.add(new SnapshotArtifact("furnishing-"+group+(reference ? "-reference.png" : "-template.png"),
                    FurnishingDamageTemplate.render(context.projectRoot(),group,reference)));
        for (int turn = 0; turn < 4; turn++) {
            NavigationGrid grid = new NavigationGrid(32, 11);
            CellTopology topology = new CellTopology(32, 11);
            for (int y = 0; y < 11; y++) for (int x = 0; x < 32; x++) {
                grid.setWalkableFloor(x,y);
                topology.setGroundKind(x,y,y < 5 ? CellTopology.GroundKind.GRASS : CellTopology.GroundKind.INDOOR);
            }
            try (BattleSimulation sim = new BattleSimulation(grid,topology,42)) {
                for (int col = 0; col < FurnishingDamageTemplate.IDS.length; col++) for (int state = 0; state < 3; state++) {
                    int x = 1 + col * 3, y = 8 - state * 3;
                    if (col % 2 == 0) { grid.setWalkable(x,y,false); topology.setFixture(x,y,true); }
                    sim.addDoodad(new Doodad(x,y,TileRegistry.installed().doodad(FurnishingDamageTemplate.IDS[col]),turn,turn >= 2));
                    if (state > 0) sim.damageCell(x,y,state == 1 ? 20 : 100);
                }
                // Neighboring shelf bays prove the original full-cell registration as well as isolated props.
                for (int x = 10; x < 14; x++) {
                    sim.addDoodad(new Doodad(x,0,TileRegistry.installed().doodad("doodad.shelf-2"),turn,turn >= 2));
                    if (x == 11) sim.damageCell(x,0,20);
                    if (x == 12) sim.damageCell(x,0,100);
                }
                var map = new MapResult(grid,topology,0,0,31,10,List.of(),sim.getDoodads());
                var renderer = new HeadlessBattleMapRenderer(context.modRoot());
                frames.add(new SnapshotArtifact("states-turn-"+turn+"-32px.png",renderer.render(map,42,32)));
                if (turn == 0) frames.add(new SnapshotArtifact("states-64px.png",renderer.render(map,42,64)));
            }
        }
        return frames;
    }
}
