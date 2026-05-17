package com.dillon.starsectormarines.battle.mapgen;

import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.PointOfInterest;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.List;
import java.util.Random;

/**
 * Strategy interface for per-{@link BlockKind} fill. The orchestrator
 * ({@code BspCityGenerator}) hands each leaf to the matching filler, which
 * paints ground kinds, carves walls / doorways, and emits POIs + doodads
 * into the accumulator lists.
 *
 * <p>The interface is intentionally narrow: a filler MUST NOT touch the
 * shared road frame around the leaf (that's the orchestrator's job) and
 * MUST NOT mutate other leaves. Within its own rect it has free rein.
 *
 * <p>Contract for each call:
 * <ul>
 *   <li>The leaf's cells start as {@link CellTopology.GroundKind#STREET}
 *       (the orchestrator initializes that for the whole map; the road
 *       frame keeps it, leaves overwrite it). The filler should explicitly
 *       set the cells it owns to the appropriate ground kind — leaving a
 *       cell at STREET would visually disconnect from the surrounding road
 *       in a way that reads wrong.</li>
 *   <li>The filler may append to {@code pois} and {@code doodads}; both
 *       lists are mutable and shared across all fillers for one run.</li>
 *   <li>The filler must respect the {@link MapResult} invariants
 *       (doorways have walkable cells on both sides, POI perimeters are
 *       closed, etc.) inside its own rect.</li>
 * </ul>
 */
public interface BlockFiller {

    /** The kind this filler handles. The orchestrator looks fillers up by this. */
    BlockKind kind();

    /**
     * Paint {@code leaf}'s rect into the grid + topology, optionally emitting
     * POIs / doodads.
     *
     * @param leaf      the rect this filler owns
     * @param grid      nav grid — set walkability, doorways, cover
     * @param topology  per-cell ground kind + flags (wall, etc.)
     * @param pois      accumulator for landmark buildings
     * @param doodads   accumulator for placed decorative tiles
     * @param rng       deterministic source, seeded by the orchestrator
     */
    void fill(BlockLeaf leaf,
              NavigationGrid grid,
              CellTopology topology,
              List<PointOfInterest> pois,
              List<Doodad> doodads,
              Random rng);
}
