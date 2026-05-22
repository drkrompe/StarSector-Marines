package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.map.Buildings;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.List;

/**
 * Owns the fog-of-war state: the {@link Buildings} registry the current battle
 * is using and the {@link PlayerVisionState} faction-contributor set. Runs the
 * periodic {@link BuildingVisibilityPass} that decides which building interiors
 * are revealed to the player on a ~10 Hz cadence.
 *
 * <p>Sibling to {@link com.dillon.starsectormarines.battle.fx.EffectsService}
 * in the services refactor — owned by
 * {@link com.dillon.starsectormarines.battle.BattleSimulation}, which delegates
 * {@code getBuildings} / {@code setBuildings} / {@code getVisionState} and the
 * VISION-phase tick call here.
 */
public final class VisionService {

    /** Fog-of-war contributor set + per-building roof alpha targets. Constructed empty; {@link com.dillon.starsectormarines.battle.BattleSetup} swaps in the real {@link Buildings} when it hands the sim a map. */
    private Buildings buildings = Buildings.EMPTY;
    private final PlayerVisionState visionState = new PlayerVisionState();

    /** Building registry for the roof-render + fog-of-war passes. Never null. */
    public Buildings getBuildings() { return buildings; }

    /** Faction-contributor set for the fog-of-war reveal. */
    public PlayerVisionState getVisionState() { return visionState; }

    /** Hands the service the map's building registry. Called by {@code BattleSetup} after generation. Subsequent {@link #tick} calls will reveal/hide these buildings as contributor units move. Null collapses to {@link Buildings#EMPTY}. */
    public void setBuildings(Buildings buildings) {
        this.buildings = buildings != null ? buildings : Buildings.EMPTY;
    }

    /**
     * VISION-phase tick. Runs the {@link BuildingVisibilityPass} every third
     * sim-tick (~10 Hz at the 30 Hz tick rate). The render path lerps
     * current → target roof alpha per frame so the cadence stays invisible.
     * Skipped entirely when no buildings are registered.
     */
    public void tick(int simTickIndex, List<Unit> units, NavigationGrid grid) {
        if (simTickIndex % 3 != 0) return;
        if (buildings.isEmpty()) return;
        BuildingVisibilityPass.update(buildings, units, grid, visionState);
    }
}
