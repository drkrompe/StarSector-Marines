package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.turret.EmplacementStyle;
import com.dillon.starsectormarines.battle.turret.FortWorks;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

/**
 * What a building's roof is made of, which is the row of the roof sheet it is drawn from (`battle-render-nouns.md`,
 * Roofs). Presentation only: no rule reads it.
 *
 * <p>A roof follows the building under it. A fort lot's building is roofed in its works' family, so a prepared post
 * pours a slab, a League or high-tech one plates it, and field works throw sheet steel over it. A town building is
 * roofed for its walls: masonry carries clay tile, ceramic panel a flat slab, olive sheet a corrugated roof. A
 * building with neither falls back on its kind.
 */
public enum RoofStyle {
    /** Terracotta pantiles in courses over timber battens; burns to charred rafters. */
    CLAY,
    /** A flat poured slab, tarred seams, gravel and vents; holes show rebar, a fire leaves it scorched and caved. */
    SLAB,
    /** Ribbed steel sheet on purlins; holes tear and curl, a fire warps the sheets off bare purlins. */
    CORRUGATED,
    /** Riveted armour plate with hatches, for prepared works; holes are punched craters in the plate. */
    PLATE;

    /** Rows of the roof sheet, in this order. */
    public static final int COUNT = values().length;

    /** The style a building's roof is drawn in. */
    public static RoofStyle of(Building building, CellTopology topology) {
        EmplacementStyle works = worksOf(building, topology);
        if (works != null) return ofWorks(works);
        RoofStyle walls = ofWalls(building, topology);
        if (walls != null) return walls;
        switch (building.kind) {
            case RESIDENTIAL: return CLAY;
            case COMMERCIAL:
            case CIVIC:
            case MEDICAL: return SLAB;
            case INDUSTRIAL: return CORRUGATED;
            case FORTIFIED: return PLATE;
            default: return (building.id & 1) == 0 ? CLAY : SLAB;
        }
    }

    /** A fort lot's roof, in its works' family. */
    static RoofStyle ofWorks(EmplacementStyle works) {
        if (works.militia) return CORRUGATED;
        switch (works) {
            case BRUTALIST: return SLAB;
            case DERELICT:
            case PATCHWORK: return CORRUGATED;
            default: return PLATE;
        }
    }

    /** A town building's roof, for the wall material it is built of; null for walls no style is paired with. */
    static RoofStyle ofWallSurface(String surfaceId) {
        if (surfaceId == null) return null;
        if (surfaceId.endsWith("masonry-wall")) return CLAY;
        if (surfaceId.endsWith("ceramic-wall") || surfaceId.contains("concrete-wall")) return SLAB;
        if (surfaceId.endsWith("olive-wall")) return CORRUGATED;
        return null;
    }

    /**
     * The quarter turns a building's roof runs at: its courses, ribs and plates lie along its long side, so a
     * building deeper than it is wide turns them a quarter.
     */
    public static int grainTurns(Building building) {
        return building.maxY - building.minY > building.maxX - building.minX ? 1 : 0;
    }

    private static EmplacementStyle worksOf(Building building, CellTopology topology) {
        for (int i = 0, n = building.cellCount(); i < n; i++) {
            EmplacementStyle style = FortWorks.styleAt(topology, building.cellsX[i], building.cellsY[i]);
            if (style != null) return style;
        }
        return null;
    }

    /** The material of the first wall found across one of the building's sides. */
    private static RoofStyle ofWalls(Building building, CellTopology topology) {
        for (int i = 0, n = building.cellCount(); i < n; i++) {
            int x = building.cellsX[i];
            int y = building.cellsY[i];
            for (int side = 0; side < 4; side++) {
                int nx = x + (side == 1 ? 1 : side == 3 ? -1 : 0);
                int ny = y + (side == 0 ? 1 : side == 2 ? -1 : 0);
                if (!topology.isWall(nx, ny)) continue;
                RoofStyle style = ofWallSurface(topology.getSurfaceId(nx, ny));
                if (style != null) return style;
            }
        }
        return null;
    }
}
