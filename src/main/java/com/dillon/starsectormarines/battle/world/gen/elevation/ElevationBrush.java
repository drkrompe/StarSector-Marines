package com.dillon.starsectormarines.battle.world.gen.elevation;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.world.gen.CellContour;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainField;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainSurface;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/**
 * Writes an elevation plan into a map at generation time. Never call it on a
 * running battle: the elevation map is fixed for the battle.
 *
 * <p>Movement is expressed in the grid's own vocabulary and nothing else: face
 * cells become unwalkable natural solid, and every shared edge between walkable
 * cells on different tiers outside a ramp is closed. Every route authority —
 * infantry A*, the swarm's flow fields, vehicle clearance, the mech planner,
 * manual motion — therefore refuses a cliff without a rule of its own.
 */
public final class ElevationBrush {

    private ElevationBrush() {}

    public static ElevationMap stamp(ElevationPlan plan, NavigationGrid grid, CellTopology topology) {
        return stamp(plan, grid, topology, null);
    }

    /** Reserved cells are a host's circulation and objective ground; elevation never touches them. */
    public static ElevationMap stamp(ElevationPlan plan, NavigationGrid grid, CellTopology topology,
                                     boolean[] reserved) {
        int w = plan.width(), h = plan.height();
        if (grid.getWidth() != w || grid.getHeight() != h
                || topology.getWidth() != w || topology.getHeight() != h
                || (reserved != null && reserved.length != w * h)) {
            throw new IllegalArgumentException("Elevation and map dimensions differ");
        }
        if (grid.elevation() != null) {
            throw new IllegalArgumentException("Elevation already stamped; rebuild the map to edit it");
        }
        ElevationLayout layout = ElevationLayout.resolve(plan);
        // Preflight everything before writing anything: a refusal leaves the map untouched.
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!layout.touched(x, y)) continue;
                boolean natural = switch (topology.getGroundKind(x, y)) {
                    case GRASS, DIRT, STONE, SAND, SNOW -> true;
                    default -> false;
                };
                if (!natural || !grid.isWalkable(x, y) || grid.isDoorway(x, y) || topology.isWall(x, y)
                        || topology.isFixture(x, y) || topology.getBuildingId(x, y) != 0
                        || topology.getSurface(x, y) != 0
                        || (topology.roadSurface() != null && topology.roadSurface().underlay(x, y) != null)
                        || (reserved != null && reserved[y * w + x])) {
                    throw new IllegalArgumentException("Elevation overlaps protected ground at " + x + "," + y);
                }
            }
        }
        ElevationMap map = layout.toMap(ElevationMap.SightRule.configured());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!layout.isFace(x, y)) continue;
                grid.setWalkable(x, y, false);
                grid.setSeeThrough(x, y, false);
                grid.setTag(x, y, CellTag.NATURAL_SOLID, true);
                grid.setWallHp(x, y, 0);
                topology.setFixture(x, y, true);
            }
        }
        // Close every cliff edge left between walkable cells (a face band's
        // corners can leave two tiers touching).
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!grid.isWalkable(x, y)) continue;
                int i = y * w + x;
                if (x + 1 < w && grid.isWalkable(x + 1, y) && map.separates(i, i + 1)) {
                    grid.blockSharedEdge(x, y, Direction.E);
                }
                if (y + 1 < h && grid.isWalkable(x, y + 1) && map.separates(i, i + w)) {
                    grid.blockSharedEdge(x, y, Direction.N);
                }
            }
        }
        // Rim and foot cells take wall-like cover from the face beside them.
        int[] dx = {0, 1, 0, -1}, dy = {-1, 0, 1, 0};
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!grid.isWalkable(x, y)) continue;
                for (int facing = 0; facing < 4; facing++) {
                    int nx = x + dx[facing], ny = y + dy[facing];
                    if (layout.isFaceInBounds(nx, ny) && grid.getCoverAtFacing(x, y, facing) < 1) {
                        grid.setCoverAtFacing(x, y, facing, 1);
                    }
                }
            }
        }
        grid.setElevation(map);
        drawCliffs(layout, topology);
        return map;
    }

    /**
     * The cliffs' art: the natural terrain's face bands clipped from each
     * tier's field — the first tier in the raised layers, the second stacked
     * after it — and a floor over each ramp's corridor, laid over whatever brush
     * terrain the map already draws. Presentation only, and skipped where the
     * terrain materials are not installed (a bare unit test): the battle is the
     * same with or without it. A third tier stands and fights but draws as the
     * second's top.
     */
    private static void drawCliffs(ElevationLayout layout, CellTopology topology) {
        int w = layout.width(), h = layout.height();
        if (layout.highestTier() == 0) return;
        TerrainPlan.Finish finish = finishFor(layout, topology);
        String material = finish.materialPrefix();
        TileRegistry tiles = TileRegistry.installed();
        if (tiles == null || tiles.block(material + "talus") == null || tiles.block(material + "rock") == null
                || tiles.block(material + "face.0") == null) {
            return;
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (layout.isFace(x, y) || layout.isRamp(x, y)) topology.setNatureOverlayIndex(x, y, -1);
            }
        }
        TerrainSurface surface = tier(layout, 1, finish, topology);
        if (layout.highestTier() >= 2) surface = TerrainSurface.stacked(surface, tier(layout, 2, finish, topology));
        int floor = topology.surfaceIndex(material + "talus");
        for (ElevationLayout.RampShape ramp : layout.ramps()) {
            int layer = ramp.lowerTier() == 0 ? 6 : TerrainSurface.RAISED_LAYERS + 6;
            TerrainSurface.Patch[][] halves = rampFloor(layout, ramp, floor);
            surface = surface.withPatches(layer, halves[0]).withPatches(layer + 1, halves[1]);
        }
        topology.setTerrainSurface(TerrainSurface.compose(topology.terrainSurface(), surface));
    }

    private static TerrainSurface tier(ElevationLayout layout, int k, TerrainPlan.Finish finish,
                                       CellTopology topology) {
        TerrainField field = TerrainField.of(layout.width(), layout.height(), layout.artCorners(k), null);
        return TerrainSurface.build(finish, layout.faceWidth(), field, topology);
    }

    /**
     * A ramp's floor as two triangle patches per cell (the cap layers' pair),
     * clipped to the corridor's sides so the floor's edge runs along the ramp
     * rather than along the grid.
     */
    private static TerrainSurface.Patch[][] rampFloor(ElevationLayout layout, ElevationLayout.RampShape ramp,
                                                      int surface) {
        int w = layout.width(), h = layout.height();
        TerrainSurface.Patch[][] out = new TerrainSurface.Patch[2][w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!layout.isRamp(x, y)) continue;
                double along = ramp.along(x, y);
                if (along < 0 || along > ramp.length() || Math.abs(ramp.across(x, y)) > ramp.half()) continue;
                double side = Math.signum(ramp.across(x, y));
                // Inside distance at the four corners, local (0,0) (1,0) (1,1) (0,1).
                double a = inside(ramp, side, x - .5, y - .5), b = inside(ramp, side, x + .5, y - .5);
                double c = inside(ramp, side, x + .5, y + .5), d = inside(ramp, side, x - .5, y + .5);
                float[] first = CellContour.clip(new float[]{0, 0, 1, 0, 1, 1}, new double[]{a, b, c});
                float[] second = CellContour.clip(new float[]{0, 0, 1, 1, 0, 1}, new double[]{a, c, d});
                if (first != null) out[0][y * w + x] = new TerrainSurface.Patch(surface, first);
                if (second != null) out[1][y * w + x] = new TerrainSurface.Patch(surface, second);
            }
        }
        return out;
    }

    private static double inside(ElevationLayout.RampShape ramp, double side, double x, double y) {
        return side == 0 ? ramp.half() - Math.abs(ramp.across(x, y)) : ramp.half() - side * ramp.across(x, y);
    }

    /** Frozen ground gets ice, living ground grass-topped rock, anything else bare rock. */
    private static TerrainPlan.Finish finishFor(ElevationLayout layout, CellTopology topology) {
        for (int y = 0; y < layout.height(); y++) {
            for (int x = 0; x < layout.width(); x++) {
                if (layout.tier(x, y) == 0 || layout.isFace(x, y)) continue;
                return switch (topology.getGroundKind(x, y)) {
                    case SNOW -> TerrainPlan.Finish.ICE;
                    case GRASS -> TerrainPlan.Finish.GRASS_TOP;
                    default -> TerrainPlan.Finish.ROCK;
                };
            }
        }
        return TerrainPlan.Finish.GRASS_TOP;
    }
}
