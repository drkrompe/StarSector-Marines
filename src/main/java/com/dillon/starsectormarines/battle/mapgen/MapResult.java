package com.dillon.starsectormarines.battle.mapgen;

import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.PointOfInterest;
import com.dillon.starsectormarines.battle.map.Buildings;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.tactical.TacticalMap;

import java.util.Collections;
import java.util.List;

/**
 * The product of a {@link MapGenerator}. Every implementation — legacy
 * striped-grid urban, BSP-based city, future wilderness/spacehulk gens —
 * returns this exact shape so {@link com.dillon.starsectormarines.battle.BattleSetup}
 * can swap implementations behind a single {@link MapGenerator} reference.
 *
 * <p>Invariants the generator must establish before returning (audited by
 * downstream consumers like the renderer, AI, zone graph):
 * <ul>
 *   <li>Spawn anchor cells are walkable on the grid.</li>
 *   <li>Doorway cells are walkable AND flagged on the grid; both perpendicular
 *       cells across the doorway are also walkable (so a portal connects two
 *       zones).</li>
 *   <li>Each {@link PointOfInterest} rect lies fully in the grid, the
 *       perimeter is non-walkable (closed building shell), at least one
 *       interior cell is walkable when {@code hollow}, the anchor cell is
 *       walkable AND outside the rect.</li>
 *   <li>Cover is baked on every walkable cell (cardinal-wall count) and any
 *       walls left after generation are flagged {@link CellTopology.Tag#WALL}.</li>
 *   <li>Doodads sit on walkable cells, never on doorways.</li>
 * </ul>
 */
public final class MapResult {

    public final NavigationGrid grid;
    public final CellTopology topology;
    public final int marineSpawnX;
    public final int marineSpawnY;
    public final int defenderSpawnX;
    public final int defenderSpawnY;
    public final List<PointOfInterest> pointsOfInterest;
    public final List<Doodad> doodads;
    /**
     * Authored tactical hint graph the battle AI uses for squad allocation and
     * fallback routing. Never null — generators with no tactical layer return
     * an empty {@link TacticalMap}. See {@link TacticalMap} for the queries
     * available to {@link com.dillon.starsectormarines.battle.BattleSetup}.
     */
    public final TacticalMap tacticalMap;
    /**
     * Building registry — closed INDOOR/TILE regions found by the flood-fill
     * pass after stamping. Drives the roof-render and fog-of-war visibility
     * systems. Never null; generators that don't run the flood-fill (or that
     * gen maps without buildings) return {@link Buildings#EMPTY}.
     */
    public final Buildings buildings;

    public MapResult(NavigationGrid grid, CellTopology topology,
                     int marineSpawnX, int marineSpawnY,
                     int defenderSpawnX, int defenderSpawnY,
                     List<PointOfInterest> pointsOfInterest,
                     List<Doodad> doodads) {
        this(grid, topology, marineSpawnX, marineSpawnY, defenderSpawnX, defenderSpawnY,
                pointsOfInterest, doodads, new TacticalMap(Collections.emptyList()), Buildings.EMPTY);
    }

    public MapResult(NavigationGrid grid, CellTopology topology,
                     int marineSpawnX, int marineSpawnY,
                     int defenderSpawnX, int defenderSpawnY,
                     List<PointOfInterest> pointsOfInterest,
                     List<Doodad> doodads,
                     TacticalMap tacticalMap) {
        this(grid, topology, marineSpawnX, marineSpawnY, defenderSpawnX, defenderSpawnY,
                pointsOfInterest, doodads, tacticalMap, Buildings.EMPTY);
    }

    public MapResult(NavigationGrid grid, CellTopology topology,
                     int marineSpawnX, int marineSpawnY,
                     int defenderSpawnX, int defenderSpawnY,
                     List<PointOfInterest> pointsOfInterest,
                     List<Doodad> doodads,
                     TacticalMap tacticalMap,
                     Buildings buildings) {
        this.grid = grid;
        this.topology = topology;
        this.marineSpawnX = marineSpawnX;
        this.marineSpawnY = marineSpawnY;
        this.defenderSpawnX = defenderSpawnX;
        this.defenderSpawnY = defenderSpawnY;
        this.pointsOfInterest = pointsOfInterest;
        this.doodads = doodads;
        this.tacticalMap = tacticalMap;
        this.buildings = buildings;
    }
}
