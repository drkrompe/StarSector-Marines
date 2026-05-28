package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;

import java.util.List;
import java.util.Random;

/**
 * Paints a multi-leaf {@link Compound} as one parcel. Counterpart to the
 * per-leaf {@link com.dillon.starsectormarines.battle.world.gen.BlockFiller}
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
     *
     * <p>{@code roadReservation} is the cell mask produced by
     * {@link com.dillon.starsectormarines.battle.world.gen.road.RoadReservation}
     * — every {@link com.dillon.starsectormarines.battle.world.gen.road.RoadGraph}
     * node / edge cell. Compound fillers MUST skip absorbing reserved cells
     * (so a centerline running between members stays drivable) and MUST
     * skip painting walls on reserved cells (so the public road keeps its
     * implicit gate through the compound's perimeter). Always non-null;
     * generators without a road graph pass an all-false mask.
     *
     * <p>Fillers may emit {@link TacticalNode}s into {@code tactical} for any
     * leaves that map to a tactical role (barracks, armory, command post).
     * Compound fillers that don't produce tactical-relevant features can
     * leave the list untouched.
     */
    void fill(Compound compound,
              NavigationGrid grid,
              CellTopology topology,
              boolean[][] roadCells,
              boolean[][] roadReservation,
              List<PointOfInterest> pois,
              List<Doodad> doodads,
              List<TacticalNode> tactical,
              Random rng);
}
