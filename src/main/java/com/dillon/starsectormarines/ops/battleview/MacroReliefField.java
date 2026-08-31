package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

/**
 * How tall each cell stands, in metres above the ground datum.
 *
 * <p>One authority for a question several things ask. The height target's pass
 * writes it into the channel the composite reads; the sun-shadow snapshot suite
 * asks the same question on the CPU. Answering it twice is how a preview comes
 * to show shadows the game does not cast.
 *
 * <h2>What has a height</h2>
 * <ul>
 *   <li><b>A wall is a storey.</b></li>
 *   <li><b>A roofed building is solid.</b> Its interior floor is a floor, but
 *       what stands between the sun and the ground is the roof, so a roofed
 *       interior cell reports roof height and the building casts as a block
 *       rather than as a hollow outline of its own walls. A cell whose roof has
 *       caved in drops back to its floor, which punches daylight into a
 *       breached building and lays the intact roof's own shadow across the
 *       hole.</li>
 *   <li><b>A window cut into a wall is a low spot in it.</b> A
 *       {@link CellTopology.Tag#WINDOW} cell is an aperture through a thick
 *       structural wall, and in a height field the honest way to let light
 *       through one is to lower it to its sill: light passes over the sill and
 *       lands behind the opening while the full-height wall either side keeps
 *       its shadow. That reads as a shaft through the window, and it costs the
 *       composite nothing — no extra channel and no extra sample.</li>
 * </ul>
 *
 * <h2>What deliberately has no height</h2>
 * <p><b>A shared-edge window pane is not modelled here, and should not be.</b>
 * {@link SharedEdgeBarrier.Kind#WINDOW} is a pane standing on the boundary
 * between two cells, and {@code NavigationGrid.placeEdgeBarrier} requires both
 * of them walkable — so the facade's solid parts are wall cells and the
 * aperture is the gap between them. The gap is floor, at the datum, so light
 * already passes through it and the shaft is already there for free. Giving the
 * pane a height would mean raising one of those walkable floor cells to sill
 * height, which is a lie about a cell marines stand on and would show up as a
 * bump under the parallax as well as a shadow. An edge feature wants
 * edge-resolution relief; a cell-addressed field cannot express one without
 * claiming the whole cell.
 *
 * <p><b>Roof visibility is not roof presence.</b> {@code Building.currentAlpha}
 * fades a roof out so the player can see the fight inside; the roof is still
 * there. Reading that fade here would make a building's shadow pulse as units
 * walked in and out of sight of it — the shadow would report what the player
 * knows rather than what is standing. Only a destroyed roof changes the height.
 */
public final class MacroReliefField {

    private final CellTopology topology;
    private final GenMappingRegistry mapping;
    private final float wallMeters;
    private final float roofMeters;
    private final float windowSillMeters;

    /**
     * Cells standing under an intact roof.
     *
     * <p>Gathered from the {@link Buildings} registry rather than by sweeping
     * the grid, because roofs belong to buildings and a cell cannot be asked
     * whether it has one. The cost is the number of roofed cells, not the size
     * of the map.
     */
    private final boolean[] roofed;

    public MacroReliefField(CellTopology topology, Buildings buildings, GenMappingRegistry mapping) {
        this.topology = topology;
        this.mapping = mapping;
        this.wallMeters = mapping != null
                ? mapping.wallMacroHeightMeters()
                : GenMappingRegistry.DEFAULT_WALL_MACRO_HEIGHT_METERS;
        this.roofMeters = mapping != null ? mapping.roofMacroHeightMeters() : wallMeters;
        this.windowSillMeters = mapping != null
                ? mapping.windowSillMacroHeightMeters()
                : GenMappingRegistry.DEFAULT_WINDOW_SILL_MACRO_HEIGHT_METERS;

        this.roofed = new boolean[topology.getWidth() * topology.getHeight()];
        markRoofs(buildings);
    }

    /** Metres above the ground datum at {@code (x, y)}; the datum itself outside the map. */
    public float metersAt(int x, int y) {
        if (x < 0 || y < 0 || x >= topology.getWidth() || y >= topology.getHeight()) return 0f;
        if (topology.isWall(x, y)) {
            return topology.isWindow(x, y) ? windowSillMeters : wallMeters;
        }
        if (roofed[topology.index(x, y)]) return roofMeters;
        return mapping != null ? mapping.macroHeightMeters(topology.getGroundKind(x, y)) : 0f;
    }

    /** Metres at a continuous world position, which is the cell it falls in. */
    public float metersAt(float worldX, float worldY) {
        return metersAt((int) Math.floor(worldX), (int) Math.floor(worldY));
    }

    /** The tallest height this field can report, which is what sizes the sun's march. */
    public float tallestMeters() {
        float tallest = Math.max(wallMeters, roofMeters);
        return mapping != null ? Math.max(tallest, mapping.tallestMacroHeightMeters()) : tallest;
    }

    /** How many cells stand under an intact roof. */
    public int roofedCellCount() {
        int total = 0;
        for (boolean flag : roofed) if (flag) total++;
        return total;
    }

    /**
     * How many wall cells are apertures standing at their sill.
     *
     * <p>Reported so evidence can say whether a map exercised the rule at all.
     * A preview that happens to contain no windows would otherwise look like
     * proof that windows work.
     */
    public int windowCellCount() {
        int total = 0;
        for (int y = 0; y < topology.getHeight(); y++) {
            for (int x = 0; x < topology.getWidth(); x++) {
                if (topology.isWall(x, y) && topology.isWindow(x, y)) total++;
            }
        }
        return total;
    }

    private void markRoofs(Buildings buildings) {
        if (buildings == null || buildings.isEmpty()) return;
        for (Building building : buildings.all()) {
            for (int i = 0, n = building.cellCount(); i < n; i++) {
                int x = building.cellsX[i];
                int y = building.cellsY[i];
                if (topology.isRoofDestroyed(x, y)) continue;
                roofed[topology.index(x, y)] = true;
            }
        }
    }

    @Override
    public String toString() {
        return "MacroReliefField[wall=" + wallMeters + "m roof=" + roofMeters
                + "m sill=" + windowSillMeters + "m roofed=" + roofedCellCount()
                + " windows=" + windowCellCount() + "]";
    }
}
