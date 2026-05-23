package com.dillon.starsectormarines.battle.mapgen.bsp.fill;

import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.Random;

/**
 * Carves interior partition wall(s) inside a building shell. The result
 * is a {@link PartitionLayout} that downstream passes (perimeter
 * doorways, room labeling) read to align with the partition geometry.
 *
 * <p>Implementations own the partition probability, wall placement, and
 * interior doorway punching. {@link BinaryPartitionStrategy} is the
 * current production implementation (single wall, 0-or-1 partition).
 */
interface PartitionStrategy {

    PartitionLayout partition(NavigationGrid grid, CellTopology topology,
                              int bl, int bt, int br, int bb,
                              Random rng, GroundKind interiorGround);
}
