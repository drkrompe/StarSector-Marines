package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.IndustrialYardCoverFixture;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import java.util.ArrayList;
import java.util.List;

final class VisibleBlockerScene {
    /** Same two actors and obstacle's far edge; only cover versus opaque foliage changes. */
    static BattleSimulation coverComparison(boolean tree) {
        NavigationGrid grid = new NavigationGrid(22, 14);
        CellTopology topology = new CellTopology(22, 14);
        for (int y = 0; y < 14; y++) for (int x = 0; x < 22; x++) {
            grid.setWalkableFloor(x, y); topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
        }
        List<Doodad> props = new ArrayList<>();
        if (tree) ForestBrush.stampTrees(List.of(new ForestField.Tree(9, 5, TreeCanopy.OAK, 0)),
                grid, topology, props, null, false);
        else props.add(IndustrialYardCoverFixture.placeCrates(grid, topology, 13, 7));
        BattleSimulation sim = new BattleSimulation(grid, topology);
        props.forEach(sim::addDoodad);
        sim.spawn(new EntitySpec("observer", Faction.MARINE, UnitType.MARINE, 5, 7).moveSpeed(0));
        sim.spawn(new EntitySpec("defender", Faction.DEFENDER, UnitType.MARINE, 14, 7).moveSpeed(0));
        settle(sim);
        return sim;
    }

    static BattleSimulation create(TreeCanopy canopy, int turns) {
        NavigationGrid grid = new NavigationGrid(26, 18);
        CellTopology topology = new CellTopology(26, 18);
        for (int y = 0; y < 18; y++) for (int x = 0; x < 26; x++) {
            grid.setWalkableFloor(x, y); topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
        }
        List<Doodad> props = new ArrayList<>();
        ForestBrush.stampTrees(List.of(new ForestField.Tree(8, 6, canopy, turns),
                new ForestField.Tree(18, 6, canopy, turns)), grid, topology, props, null, false);
        BattleSimulation sim = new BattleSimulation(grid, topology);
        props.forEach(sim::addDoodad);
        int row = 6 + canopy.height(turns) / 2;
        sim.spawn(new EntitySpec("observer", Faction.MARINE, UnitType.MARINE, 4, row).moveSpeed(0));
        sim.spawn(new EntitySpec("hidden", Faction.DEFENDER, UnitType.MARINE, 16, row).moveSpeed(0));
        settle(sim);
        return sim;
    }
    static void settle(BattleSimulation sim) {
        for (int i = 0; i < 9; i++) sim.advance(BattleSimulation.TICK_DT);
    }
}
