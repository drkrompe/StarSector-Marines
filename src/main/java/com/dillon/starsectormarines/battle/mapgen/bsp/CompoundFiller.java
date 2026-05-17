package com.dillon.starsectormarines.battle.mapgen.bsp;

import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.PointOfInterest;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.mapgen.BlockKind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.List;
import java.util.Random;

/**
 * Paints a multi-leaf {@link Compound} as one parcel. Counterpart to the
 * per-leaf {@link com.dillon.starsectormarines.battle.mapgen.BlockFiller}
 * but operates on the union of member leaves plus the inter-leaf road
 * frames between them, which compound fillers typically claim as enclosed
 * courtyard / parade ground / paved plaza.
 *
 * <p>Each implementation is keyed by {@link #kind} — the orchestrator
 * looks up the filler for a compound's {@link Compound#kind} and dispatches
 * to it once per compound. Per-leaf filler dispatch must skip leaves whose
 * kind is {@link BlockKind#COMPOUND_MEMBER}.
 */
public interface CompoundFiller {

    /** The compound's seed {@link BlockKind} — also the kind stored on the seed leaf. */
    BlockKind kind();

    /**
     * Paint the entire compound. Compound fillers have access to the road
     * mask so they can compute which inter-leaf cells are bridged inside
     * the compound vs which surrounding cells stay road.
     */
    void fill(Compound compound,
              NavigationGrid grid,
              CellTopology topology,
              boolean[][] roadCells,
              List<PointOfInterest> pois,
              List<Doodad> doodads,
              Random rng);
}
