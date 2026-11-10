package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.road.RoadSurface;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * The storm's ground banked against a storm world's buildings ({@code mapgen-nouns.md}, Climate): on the windward face
 * of every wall, a drift one to {@link #MAX} cells deep, its depth a slow coordinate noise so the drifts swell and thin
 * along a wall. Sand on a desert or a dust-blown rock, snow on a frozen world; a living world's storms pile up nothing.
 *
 * <p>A drift is a surface skin ({@code floors.sand}, {@code floors.snow}), not ground: the cell keeps its ground kind,
 * so walkability, cover, fire and every placement that reads pavement are exactly as they were. It is a picture of the
 * weather, never terrain. It lies on open ground, yards and paving with no authored surface of their own; a street
 * drifts only on its apron against the wall, never across the carriageway. Coordinate-based: no random draw.
 *
 * <p>The weather is the last skin laid ({@code StormDriftStage}, after the streets are finished): a finished road's
 * pavement is never drifted, since the carriageway finish is laid only on street with no surface of its own and a
 * drift laid before it would strip the asphalt off the street beside a wall.
 */
public final class StormDrift {

    /** The deepest drift, cells out from the wall. */
    public static final int MAX = 4;
    /** How far a drift reaches over a street: its apron against the wall. */
    static final int STREET_APRON = 2;
    /** The drift noise's scale, cells: how long a stretch of wall shares a depth. */
    static final float SCALE = 5f;

    private StormDrift() { }

    /** The surface a world's storms drift, or null where they drift nothing. */
    public static String driftSurface(SurfacePalette world) {
        return switch (world) {
            case ARID, ROCK -> "floors.sand";
            case FROZEN -> "floors.snow";
            case VOLCANIC -> "volcanic.ash";
            case TOXIC -> "toxic-ground.sulphur";
            case VERDANT -> null;
        };
    }

    /** Banks drifts against every wall's face toward {@code wind}. Returns how many cells were drifted. */
    public static int paint(CellTopology topology, SurfaceField field, StormWind.Side wind) {
        int dx = wind == StormWind.Side.WEST ? -1 : wind == StormWind.Side.EAST ? 1 : 0;
        int dy = wind == StormWind.Side.NORTH ? -1 : wind == StormWind.Side.SOUTH ? 1 : 0;
        int w = topology.getWidth(), h = topology.getHeight(), drifted = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!topology.isWall(x, y)) continue;
                int depth = 1 + (int) (SurfaceField.noise(x / SCALE, y / SCALE, field.seed() ^ 0x4452494654L) * MAX);
                for (int k = 1; k <= Math.min(depth, MAX); k++) {
                    int px = x + dx * k, py = y + dy * k;
                    if (px < 0 || py < 0 || px >= w || py >= h || !drifts(topology, px, py, k)) break;
                    SurfacePalette world = field.at(px, py);
                    String surface = driftSurface(world);
                    if (surface == null) break;
                    // Drifted sand on the desert's own sand is the same ground: nothing to draw.
                    if (topology.getGroundKind(px, py) == world.inlandGround()) continue;
                    topology.setSurface(px, py, topology.surfaceIndex(surface));
                    drifted++;
                }
            }
        }
        return drifted;
    }

    /** Whether a drift may lie on this cell, {@code k} cells out from the wall. */
    static boolean drifts(CellTopology topology, int x, int y, int k) {
        if (topology.isWall(x, y) || topology.getSurface(x, y) != 0 || paved(topology.roadSurface(), x, y)) return false;
        return switch (topology.getGroundKind(x, y)) {
            case DIRT, STONE, SAND, SNOW, GRASS, COURTYARD, BRICK -> true;
            case STREET, SIDEWALK -> k <= STREET_APRON;
            default -> false;
        };
    }

    /** Whether the finished road draws pavement or its underlay on this cell. */
    static boolean paved(RoadSurface road, int x, int y) {
        if (road == null) return false;
        if (road.underlay(x, y) != null) return true;
        for (int m = 0; m < RoadSurface.LAYERS; m++) {
            RoadSurface.Patch patch = road.patch(x, y, m);
            if (patch != null && patch.area() > 0) return true;
        }
        return false;
    }
}
