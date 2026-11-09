package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.VoidBackdrop;

/**
 * A hand-laid cluster of sky port towers for the look suite (`voids.md`): three tower roofs, every cell between
 * them void, joined by walkways of different widths. Tower A, where a mission would start, reaches tower B by a
 * two-cell catwalk and tower C by a catwalk that turns a corner over the drop; B and C are joined by a six-cell
 * skybridge a vehicle could cross. Each roof carries a landing pad or plant.
 */
final class SkyPortCluster {

    static final int W = 240, H = 170;
    /** The towers' roofs, inclusive: {@code {x0, y0, x1, y1}}. */
    static final int[] A = {30, 60, 80, 105}, B = {120, 95, 165, 145}, C = {115, 10, 160, 50};
    /** The walkways, inclusive: A to B, B to C, and the dogleg from A to C in its two legs. */
    static final int[][] WALKWAYS = {
            {81, 99, 119, 100},
            {136, 51, 141, 94},
            {70, 30, 71, 59}, {72, 30, 114, 31}};
    /** The landing pads, inclusive. */
    static final int[][] PADS = {{54, 70, 74, 90}, {138, 26, 154, 44}};

    private SkyPortCluster() { }

    static BattleSimulation build(VoidBackdrop backdrop) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                String finish = finish(x, y);
                if (finish != null) {
                    grid.setWalkableFloor(x, y);
                    topology.setGroundKind(x, y, GroundKind.STRIPED);
                    topology.setSurface(x, y, topology.surfaceIndex(finish));
                } else {
                    grid.setWalkable(x, y, false);
                    grid.setTag(x, y, CellTag.SEE_THROUGH, true);
                    grid.setTag(x, y, CellTag.NO_EDGE_COVER, true);
                    grid.setTag(x, y, CellTag.NATURAL_SOLID, true);
                    topology.setGroundKind(x, y, GroundKind.VOID);
                }
            }
        }
        block(grid, topology, 36, 66, 48, 78);
        block(grid, topology, 38, 88, 46, 98);
        block(grid, topology, 128, 118, 144, 134);
        block(grid, topology, 148, 100, 160, 110);
        block(grid, topology, 150, 126, 158, 138);
        block(grid, topology, 121, 15, 133, 27);
        topology.setVoidBackdrop(backdrop);
        SceneBuilder builder = SceneBuilder.over(grid, topology).missionCompletion(false);
        builder.squad("rifles").faction(Faction.MARINE).size(6).primary("weapon.field-rifle")
                .inFile(92, 99, 1, 0)
                .assigned(id -> ObjectiveAssignment.defendArea(id, 130, 100))
                .done();
        return builder.build().sim();
    }

    /** What a cell is laid with, or null where it is void. */
    private static String finish(int x, int y) {
        for (int[] w : WALKWAYS) if (inside(x, y, w)) return "ship-bay.grate-run";
        for (int[] t : new int[][]{A, B, C}) {
            if (!inside(x, y, t)) continue;
            for (int[] pad : PADS) {
                if (!inside(x, y, pad)) continue;
                boolean rim = x == pad[0] || x == pad[2] || y == pad[1] || y == pad[3];
                return rim ? "apron.keep-clear" : "sky-port.pad-plate";
            }
            return "sky-port.deck-plate";
        }
        return null;
    }

    /** A walled block of roof plant: closed walls with one door. */
    private static void block(NavigationGrid grid, CellTopology topology, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                topology.setGroundKind(x, y, GroundKind.INDOOR);
                topology.setSurface(x, y, topology.surfaceIndex("loading-bay.staging-deck"));
                if (x != x0 && x != x1 && y != y0 && y != y1) continue;
                grid.setWalkable(x, y, false);
                grid.setWallHp(x, y, 400);
                topology.setTag(x, y, CellTopology.Tag.WALL, true);
                topology.setSurface(x, y, topology.surfaceIndex("installation.dark-concrete-wall"));
            }
        }
        int doorX = (x0 + x1) / 2;
        grid.setWalkableFloor(doorX, y0);
        grid.setDoorway(doorX, y0, true);
        topology.setTag(doorX, y0, CellTopology.Tag.WALL, false);
        topology.setSurface(doorX, y0, topology.surfaceIndex("loading-bay.staging-deck"));
    }

    private static boolean inside(int x, int y, int[] r) {
        return x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3];
    }
}
