package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiPredicate;

/**
 * Hand-built works on a finished Cordon map, for {@link CordonStructureLookSuite}: ground cleared back to a sampled
 * floor, production building stamps, the redoubt's own perimeter wall cell copied from the one the generator laid,
 * and the generator's own prop helpers ({@link PreparedGround}: sandbags, fences, obstacles, worked earth). Everything
 * goes through the same cell writes generation makes, so a picture of it is a picture of what the pieces can build.
 *
 * <p>Edits are made before the battle is stood up, so the simulation reads them as generated ground. The roof
 * registry is rebuilt over the touched cells ({@link #buildings}): the generator's buildings that were cleared lose
 * their roofs, the stamped ones get theirs.
 */
final class CordonStructureWorks {

    /** What a floor cell looks like: its ground kind, surface block and nature overlay. */
    record Ground(GroundKind kind, int surface, int nature) {
        static Ground of(CellTopology topology, int x, int y) {
            return new Ground(topology.getGroundKind(x, y), topology.getSurface(x, y),
                    topology.getNatureOverlayIndex(x, y));
        }
    }

    final MapResult map;
    final NavigationGrid grid;
    final CellTopology topology;
    final GenContext ctx;
    private final int perimeterHp;
    private final GroundKind perimeterGround;
    private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

    CordonStructureWorks(MapResult map, long seed) {
        this.map = map;
        this.grid = map.grid;
        this.topology = map.topology;
        this.ctx = new GenContext(grid, topology, new Random(seed), grid.getWidth(), grid.getHeight(), seed);
        // The redoubts' own wall cell is the prototype: its hit points and the ground drawn under it.
        int hp = CordonPlan.REDOUBT_WALL_HP;
        GroundKind ground = GroundKind.STRIPED;
        outer:
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (topology.isWall(x, y) && InstallationWalls.PERIMETER.equals(topology.getSurfaceId(x, y))) {
                    if (grid.getWallHp(x, y) > 0) hp = grid.getWallHp(x, y);
                    ground = topology.getGroundKind(x, y);
                    break outer;
                }
            }
        }
        perimeterHp = hp;
        perimeterGround = ground;
    }

    /** The open, unbuilt cell nearest {@code (x, y)} that {@code wanted} accepts, as a floor to copy. */
    Ground sample(int x, int y, BiPredicate<Integer, Integer> wanted) {
        for (int r = 0; r < 80; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int cx = x + dx, cy = y + dy;
                    if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy) || topology.isWall(cx, cy)
                            || topology.isFixture(cx, cy) || topology.getBuildingId(cx, cy) != 0) continue;
                    if (wanted.test(cx, cy)) return Ground.of(topology, cx, cy);
                }
            }
        }
        return new Ground(GroundKind.GRASS, 0, -1);
    }

    /**
     * A sampled floor of this kind near {@code (x, y)}: the kind's own plain ground where there is some (no surface
     * block over it, as the country's grass is), else the nearest cell of the kind whatever it is paved with.
     */
    Ground sample(int x, int y, GroundKind kind) {
        Ground plain = sample(x, y, (cx, cy) -> topology.getGroundKind(cx, cy) == kind && topology.getSurface(cx, cy) == 0);
        if (plain.kind() == kind) return plain;
        return sample(x, y, (cx, cy) -> topology.getGroundKind(cx, cy) == kind);
    }

    /**
     * Clears the rectangle back to open ground: walls, buildings, windows, props and roofs go. A cell {@code keep}
     * accepts keeps its ground (a street the works are built round) and loses only what stood on it; every other
     * cell gets {@code ground}.
     */
    void clear(int x0, int y0, int x1, int y1, Ground ground, BiPredicate<Integer, Integer> keep) {
        int lx = Math.min(x0, x1), hx = Math.max(x0, x1), ly = Math.min(y0, y1), hy = Math.max(y0, y1);
        map.doodads.removeIf(d -> d.cellX <= hx && d.cellX + d.footprintCellsX - 1 >= lx
                && d.cellY <= hy && d.cellY + d.footprintCellsY - 1 >= ly);
        for (int y = ly; y <= hy; y++) {
            for (int x = lx; x <= hx; x++) {
                if (!grid.inBounds(x, y)) continue;
                for (Direction side : Direction.CARDINALS) grid.removeEdgeBarrier(x, y, side);
                boolean kept = keep != null && keep.test(x, y) && !topology.isWall(x, y);
                grid.setWalkableFloor(x, y);
                grid.setSeeThrough(x, y, true);
                grid.setDoorway(x, y, false);
                grid.setWallHp(x, y, 0);
                topology.setWall(x, y, false);
                topology.setWindow(x, y, false);
                topology.setFixture(x, y, false);
                topology.setVehicle(x, y, false);
                topology.setBuildingKindHint(x, y, null);
                topology.setBuildingId(x, y, 0);
                topology.setRoomPurpose(x, y, null);
                topology.setWallDirMask(x, y, 0);
                topology.setWallGroundSurface(x, y, null);
                topology.setWallRemnant(x, y, null);
                topology.setPlinthTiers(x, y, 0);
                topology.setRoofDestroyed(x, y, false);
                if (!kept) {
                    topology.setGroundKind(x, y, ground.kind());
                    topology.setSurface(x, y, ground.surface());
                    topology.setNatureOverlayIndex(x, y, ground.nature());
                    topology.setCrosswalk(x, y, false);
                }
            }
        }
        touch(lx, ly, hx, hy);
    }

    /** Lays {@code ground} over every open cell of the rectangle: a yard's floor, a track. */
    void floor(int x0, int y0, int x1, int y1, Ground ground) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                if (!grid.inBounds(x, y) || topology.isWall(x, y) || topology.getBuildingId(x, y) != 0
                        || topology.hasBuildingKindHint(x, y)) continue;
                topology.setGroundKind(x, y, ground.kind());
                topology.setSurface(x, y, ground.surface());
                topology.setNatureOverlayIndex(x, y, ground.nature());
            }
        }
    }

    /**
     * One cell of standing wall in {@code material} (a surface block id: the redoubt's perimeter, a building's
     * masonry), with the hit points and under-floor of the perimeter the generator laid.
     */
    void wall(int x, int y, String material) {
        if (!grid.inBounds(x, y)) return;
        grid.setWalkable(x, y, false);
        grid.setSeeThrough(x, y, false);
        grid.setDoorway(x, y, false);
        grid.setWallHp(x, y, perimeterHp);
        topology.setWall(x, y, true);
        topology.setGroundKind(x, y, perimeterGround);
        topology.setSurface(x, y, topology.surfaceIndex(material));
        topology.setWallGroundSurface(x, y, "installation.concrete-floor");
        touch(x, y, x, y);
    }

    /** A straight run of perimeter wall, ends inclusive; {@code gaps} are {lo, hi} spans along it left open. */
    void wallLine(String material, int x0, int y0, int x1, int y1, int[]... gaps) {
        boolean horizontal = y0 == y1;
        int a = horizontal ? Math.min(x0, x1) : Math.min(y0, y1), b = horizontal ? Math.max(x0, x1) : Math.max(y0, y1);
        for (int i = a; i <= b; i++) {
            boolean open = false;
            for (int[] gap : gaps) open |= i >= gap[0] && i <= gap[1];
            if (!open) wall(horizontal ? i : x0, horizontal ? y0 : i, material);
        }
    }

    /** A production building stamp, its footprint's bottom-left at {@code (left, bottom)}. */
    void stamp(BuildingStamp stamp, int left, int bottom, int turns) {
        stamp.place(ctx, left, bottom, turns);
        int[] size = size(stamp, turns);
        touch(left - 1, bottom - 1, left + size[0], bottom + size[1]);
    }

    /** A sandbag piece facing {@code toward} (a corner also faces {@code corner}), through the generator's own helper. */
    boolean sandbag(int x, int y, int[] toward, int[] corner) {
        touch(x, y, x, y);
        return PreparedGround.sandbag(ctx, x, y, toward, corner);
    }

    /** A one-cell obstacle prop: a hedgehog, a crate stack. */
    boolean obstacle(int x, int y, String id) {
        touch(x, y, x, y);
        return PreparedGround.obstacle(ctx, x, y, id);
    }

    /** One piece of industrial fence. */
    boolean fence(int x, int y, boolean horizontal) {
        touch(x, y, x, y);
        return PreparedGround.fence(ctx, x, y, horizontal);
    }

    /** Bare worked earth. */
    void worked(int x, int y) {
        PreparedGround.workedGround(ctx, x, y);
    }

    /**
     * A registry prop at {@code (x, y)}; a {@code blocking} prop closes its footprint to movement as a stamp's
     * fixtures do. Refused (false) where the art is missing or the footprint is not open floor.
     */
    boolean prop(int x, int y, String id, int turns, boolean blocking) {
        DoodadDef def = TileRegistry.installed().doodad(id);
        if (def == null) {
            System.out.printf("    [cordon-structure-look] no art for %s%n", id);
            return false;
        }
        boolean odd = (turns & 1) == 1;
        int w = odd ? def.footprintCellsY : def.footprintCellsX, h = odd ? def.footprintCellsX : def.footprintCellsY;
        for (int cy = y; cy < y + h; cy++) {
            for (int cx = x; cx < x + w; cx++) {
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy) || topology.isWall(cx, cy)
                        || topology.isFixture(cx, cy) || grid.isDoorway(cx, cy)) return false;
            }
        }
        ctx.doodads.add(new Doodad(x, y, def, turns));
        for (int cy = y; cy < y + h; cy++) {
            for (int cx = x; cx < x + w; cx++) {
                if (blocking) {
                    grid.setWalkable(cx, cy, false);
                    grid.setSeeThrough(cx, cy, true);
                    topology.setFixture(cx, cy, true);
                }
            }
        }
        touch(x, y, x + w - 1, y + h - 1);
        return true;
    }

    /** A prop's footprint {@code {width, height}} at {@code turns}; one cell where the art is missing. */
    int[] propSize(String id, int turns) {
        DoodadDef def = TileRegistry.installed().doodad(id);
        if (def == null) return new int[]{1, 1};
        return (turns & 1) == 0 ? new int[]{def.footprintCellsX, def.footprintCellsY}
                : new int[]{def.footprintCellsY, def.footprintCellsX};
    }

    /**
     * Hands the new props to the map and squares the touched cells: wall faces drawn where a wall meets open
     * ground, and cover recomputed round them.
     */
    void finish() {
        map.doodads.addAll(ctx.doodads);
        ctx.doodads.clear();
        if (minX > maxX) return;
        for (int y = minY - 1; y <= maxY + 1; y++) {
            for (int x = minX - 1; x <= maxX + 1; x++) {
                if (!grid.inBounds(x, y)) continue;
                if (topology.isWall(x, y)) {
                    int mask = 0;
                    if (!wallAt(x, y + 1)) mask |= CellTopology.WALL_DIR_N;
                    if (!wallAt(x, y - 1)) mask |= CellTopology.WALL_DIR_S;
                    if (!wallAt(x + 1, y)) mask |= CellTopology.WALL_DIR_E;
                    if (!wallAt(x - 1, y)) mask |= CellTopology.WALL_DIR_W;
                    topology.setWallDirMask(x, y, mask);
                }
                grid.recomputeCoverAt(x, y);
            }
        }
    }

    /**
     * The map's roof registry with the works in it: a generated building keeps its roof over the cells it still
     * has, and every room the stamps built inside the touched cells is a new building with a roof of its own.
     */
    Buildings buildings() {
        Buildings out = new Buildings();
        int next = 1;
        List<Building> kept = new ArrayList<>(map.buildings.all());
        for (Building b : kept) {
            next = Math.max(next, b.id + 1);
            int n = 0;
            for (int i = 0; i < b.cellCount(); i++) if (topology.getBuildingId(b.cellsX[i], b.cellsY[i]) == b.id) n++;
            if (n == b.cellCount()) {
                out.add(b);
                continue;
            }
            if (n < 4) continue;
            int[] xs = new int[n], ys = new int[n];
            int k = 0;
            for (int i = 0; i < b.cellCount(); i++) {
                if (topology.getBuildingId(b.cellsX[i], b.cellsY[i]) != b.id) continue;
                xs[k] = b.cellsX[i];
                ys[k++] = b.cellsY[i];
            }
            out.add(building(b.id, b.kind, xs, ys, b.tintR, b.tintG, b.tintB));
        }
        // New rooms: connected open cells a stamp hinted, not yet in any building.
        List<Building> palette = kept.isEmpty() ? List.of() : kept;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (!seed(x, y) || topology.getBuildingId(x, y) != 0) continue;
                List<int[]> cells = new ArrayList<>();
                Map<BuildingKind, Integer> votes = new EnumMap<>(BuildingKind.class);
                ArrayList<int[]> queue = new ArrayList<>();
                queue.add(new int[]{x, y});
                topology.setBuildingId(x, y, next);
                for (int head = 0; head < queue.size(); head++) {
                    int[] c = queue.get(head);
                    cells.add(c);
                    votes.merge(topology.getBuildingKindHint(c[0], c[1]), 1, Integer::sum);
                    for (Direction side : Direction.CARDINALS) {
                        int nx = c[0] + side.dx, ny = c[1] + side.dy;
                        if (!seed(nx, ny) || topology.getBuildingId(nx, ny) != 0) continue;
                        topology.setBuildingId(nx, ny, next);
                        queue.add(new int[]{nx, ny});
                    }
                }
                if (cells.size() < 4) {
                    for (int[] c : cells) topology.setBuildingId(c[0], c[1], 0);
                    continue;
                }
                BuildingKind kind = BuildingKind.RESIDENTIAL;
                int best = -1;
                for (Map.Entry<BuildingKind, Integer> e : votes.entrySet()) {
                    if (e.getValue() > best) {
                        best = e.getValue();
                        kind = e.getKey();
                    }
                }
                int[] xs = new int[cells.size()], ys = new int[cells.size()];
                for (int i = 0; i < cells.size(); i++) {
                    xs[i] = cells.get(i)[0];
                    ys[i] = cells.get(i)[1];
                }
                // The tint of a generated building, so a stamped roof sits in the map's own roof palette.
                Building like = palette.isEmpty() ? null : palette.get(next % palette.size());
                out.add(building(next, kind, xs, ys, like == null ? 0.78f : like.tintR, like == null ? 0.7f : like.tintG,
                        like == null ? 0.6f : like.tintB));
                next++;
            }
        }
        return out;
    }

    /** The bounds the works have touched, {@code [minX, minY, maxX, maxY]}. */
    int[] touched() {
        return new int[]{minX, minY, maxX, maxY};
    }

    private boolean seed(int x, int y) {
        return grid.inBounds(x, y) && x >= minX && x <= maxX && y >= minY && y <= maxY
                && topology.hasBuildingKindHint(x, y) && !topology.isWall(x, y);
    }

    private static Building building(int id, BuildingKind kind, int[] xs, int[] ys, float r, float g, float b) {
        int lx = Integer.MAX_VALUE, ly = Integer.MAX_VALUE, hx = Integer.MIN_VALUE, hy = Integer.MIN_VALUE;
        for (int i = 0; i < xs.length; i++) {
            lx = Math.min(lx, xs[i]);
            hx = Math.max(hx, xs[i]);
            ly = Math.min(ly, ys[i]);
            hy = Math.max(hy, ys[i]);
        }
        return new Building(id, kind, lx, hx, ly, hy, xs, ys, r, g, b);
    }

    private boolean wallAt(int x, int y) {
        return grid.inBounds(x, y) && topology.isWall(x, y);
    }

    private void touch(int x0, int y0, int x1, int y1) {
        minX = Math.min(minX, Math.min(x0, x1));
        minY = Math.min(minY, Math.min(y0, y1));
        maxX = Math.max(maxX, Math.max(x0, x1));
        maxY = Math.max(maxY, Math.max(y0, y1));
    }

    /** A stamp's footprint {@code {width, height}} at {@code turns}: the authored plans' sizes. */
    static int[] size(BuildingStamp stamp, int turns) {
        int[] s = switch (stamp) {
            case CORNER_HOME -> new int[]{11, 11};
            case COURT_HOUSE -> new int[]{13, 10};
            case CORNER_SHOP -> new int[]{11, 10};
            case MARKET_HALL -> new int[]{13, 8};
            case T_WORKSHOP -> new int[]{13, 11};
            case OFFSET_DEPOT -> new int[]{11, 10};
            case FREIGHT_L -> new int[]{20, 17};
            case FREIGHT_T -> new int[]{19, 19};
            case FARMHOUSE -> new int[]{9, 7};
            case BARN -> new int[]{13, 8};
            case PUMP_HOUSE -> new int[]{23, 14};
            case PILLBOX -> new int[]{7, 5};
            case PREFAB_MODULE -> new int[]{11, 5};
            case CONTROL_HUT -> new int[]{7, 5};
            case PREFAB_BUNKHOUSE -> new int[]{15, 5};
            case PREFAB_LOCK_MODULE -> new int[]{13, 5};
            case PREFAB_SHORT_MODULE -> new int[]{9, 5};
            case PREFAB_TUBE -> new int[]{5, 3};
            case PREFAB_NARROW_TUBE -> new int[]{3, 3};
        };
        return (turns & 1) == 0 ? s : new int[]{s[1], s[0]};
    }
}
