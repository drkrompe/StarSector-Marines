package com.dillon.starsectormarines.battle.mapgen.bsp.fill;

import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.PointOfInterest;
import com.dillon.starsectormarines.battle.TileManifest;
import com.dillon.starsectormarines.battle.map.BuildingKind;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.mapgen.BlockKind;
import com.dillon.starsectormarines.battle.mapgen.BlockLeaf;
import com.dillon.starsectormarines.battle.mapgen.bsp.Compound;
import com.dillon.starsectormarines.battle.mapgen.bsp.CompoundFiller;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Multi-leaf compound filler for {@link com.dillon.starsectormarines.battle.mapgen.BlockKind#MILITARY_BASE}.
 * Paints the entire {@link Compound} as one parcel:
 *
 * <ol>
 *   <li>Computes the cells "inside" the compound — every member leaf plus
 *       the inter-leaf road frames bridged by members.</li>
 *   <li>Paints a 1-cell perimeter wall ring around that union ({@link
 *       GroundKind#STRIPED} ground so breaches read as military floor).</li>
 *   <li>Repaints the bridged roads as {@link GroundKind#STONE} parade ground —
 *       the visual hinge that converts "adjacent buildings" into "one base".</li>
 *   <li>Insets each member leaf by one cell and carves a sub-building inside
 *       via {@link BuildingShellCore#carve}; role drives interior floor +
 *       doodad pool (COMMAND = SKYPORT, BARRACKS = RESIDENTIAL, ARMORY /
 *       VEHICLE_BAY = WAREHOUSE).</li>
 *   <li>Punches 1-2 gates on the wall ring at cells facing road on the
 *       outside (so the base is reachable from the street network).</li>
 *   <li>Stamps {@link CellTopology#setVehicle vehicle}-flagged gun-emplacement
 *       cells on the compound's outer corners — reads as turreted hardpoints.</li>
 * </ol>
 *
 * <p>Not a {@link com.dillon.starsectormarines.battle.mapgen.BlockFiller} —
 * the per-leaf filler dispatch can't represent a multi-leaf operation. The
 * orchestrator ({@link com.dillon.starsectormarines.battle.mapgen.bsp.BspCityGenerator})
 * iterates {@link Compound}s directly and calls {@link #fill} for each one,
 * skipping the per-leaf dispatch for any leaf whose kind is
 * {@link com.dillon.starsectormarines.battle.mapgen.BlockKind#COMPOUND_MEMBER}.
 */
public final class MilitaryBaseFiller implements CompoundFiller {

    @Override public BlockKind kind() { return BlockKind.MILITARY_BASE; }


    /** Underlying ground for the perimeter wall — STRIPED so breached walls expose military safety floor. */
    private static final GroundKind WALL_GROUND = GroundKind.STRIPED;
    /** Parade-ground material inside the wall (former inter-leaf roads + leaf outer rims). */
    private static final GroundKind PARADE_GROUND = GroundKind.STONE;
    /** Floor under gun emplacement cells — reads as paved turret pad. */
    private static final GroundKind EMPLACEMENT_GROUND = GroundKind.STONE;
    /** Max road-strip depth searched when looking for member-bridged road cells. Matches {@link com.dillon.starsectormarines.battle.mapgen.bsp.LeafAdjacency}'s scan depth so the same gap counts as "inside the compound". */
    private static final int BRIDGE_SCAN_DEPTH = 5;
    /** Default wall HP — matches the legacy seed used elsewhere. Higher than building walls because the compound wall is meant to read as armor. */
    private static final int WALL_HP_FORTIFIED = 150;

    private static final BuildingShellCore.BuildingConfig COMMAND_CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.INDOOR, TileManifest.SKYPORT_DOODADS, PointOfInterest.Kind.COMMS,
            BuildingLayouts.LayoutRecipe.SHOP, BuildingKind.FORTIFIED);
    private static final BuildingShellCore.BuildingConfig BARRACKS_CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.INDOOR, TileManifest.RESIDENTIAL_DOODADS, PointOfInterest.Kind.RESIDENTIAL,
            BuildingLayouts.LayoutRecipe.HOME, BuildingKind.FORTIFIED);
    private static final BuildingShellCore.BuildingConfig ARMORY_CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.STRIPED, TileManifest.WAREHOUSE_DOODADS, PointOfInterest.Kind.DEPOT,
            BuildingLayouts.LayoutRecipe.WAREHOUSE, BuildingKind.FORTIFIED);
    private static final BuildingShellCore.BuildingConfig VEHICLE_BAY_CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.STRIPED, TileManifest.WAREHOUSE_DOODADS, PointOfInterest.Kind.DEPOT,
            BuildingLayouts.LayoutRecipe.WAREHOUSE, BuildingKind.FORTIFIED);

    @Override
    public void fill(Compound compound,
                     NavigationGrid grid,
                     CellTopology topology,
                     boolean[][] roadCells,
                     List<PointOfInterest> pois,
                     List<Doodad> doodads,
                     List<TacticalNode> tactical,
                     Random rng) {
        int w = grid.getWidth();
        int h = grid.getHeight();

        boolean[][] memberCells = new boolean[w][h];
        markMemberCells(compound, memberCells);

        boolean[][] inCompound = new boolean[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) inCompound[x][y] = memberCells[x][y];
        }
        markBridgedRoads(compound, roadCells, memberCells, inCompound);
        absorbConcaveNotches(compound, inCompound);

        repaintParadeGround(compound, inCompound, memberCells, grid, topology);
        Map<BlockLeaf, PointOfInterest> leafPois = carveSubBuildings(
                compound, inCompound, grid, topology, doodads, pois, rng);
        paintWallRing(inCompound, grid, topology);
        punchGates(compound, inCompound, roadCells, grid, topology, rng);
        stampGunEmplacements(compound, inCompound, grid, topology, pois);
        emitTacticalNodes(compound, leafPois, tactical);
    }

    /**
     * Emit a {@link TacticalNode} per role-tagged member leaf. The role map
     * ({@link Compound#roles}) was populated when the compound was claimed;
     * here we just translate roles to node kinds with kind-specific priority
     * and garrison settings. Sub-buildings without an assigned role are
     * skipped — they're generic outbuildings the AI doesn't need to target
     * specifically.
     *
     * <p>The anchor is taken from the sub-building's POI interior anchor —
     * a walkable INDOOR cell inside the carved shell — so garrison spawns
     * land inside their building rather than on the parade ground outside.
     * Leaves too small to carve (no POI) fall back to leaf-center; their
     * tactical-node BFS in {@code BattleSetup.pickCellsNear} still resolves
     * to a walkable cell on the parade ground in that case.
     */
    private void emitTacticalNodes(Compound compound,
                                   Map<BlockLeaf, PointOfInterest> leafPois,
                                   List<TacticalNode> tactical) {
        for (BlockLeaf m : compound.members) {
            Compound.Role role = compound.roles.get(m);
            if (role == null) continue;
            PointOfInterest poi = leafPois.get(m);
            int anchorX = (poi != null) ? poi.interiorAnchorX : (m.left + m.right) / 2;
            int anchorY = (poi != null) ? poi.interiorAnchorY : (m.top + m.bottom) / 2;
            switch (role) {
                case COMMAND:
                    tactical.add(new TacticalNode(TacticalNode.Kind.COMMAND_POST,
                            anchorX, anchorY, m.left, m.top, m.right, m.bottom,
                            Faction.DEFENDER, 95, 4));
                    break;
                case BARRACKS:
                    tactical.add(new TacticalNode(TacticalNode.Kind.BARRACKS,
                            anchorX, anchorY, m.left, m.top, m.right, m.bottom,
                            Faction.DEFENDER, 60, 4));
                    break;
                case ARMORY:
                    tactical.add(new TacticalNode(TacticalNode.Kind.ARMORY,
                            anchorX, anchorY, m.left, m.top, m.right, m.bottom,
                            Faction.DEFENDER, 70, 3));
                    break;
                case VEHICLE_BAY:
                    // Treat as ARMORY for now — same supply-line role; could split when
                    // vehicle-spawn AI lands. No dedicated kind yet to keep the enum lean.
                    tactical.add(new TacticalNode(TacticalNode.Kind.ARMORY,
                            anchorX, anchorY, m.left, m.top, m.right, m.bottom,
                            Faction.DEFENDER, 55, 3));
                    break;
                default:
                    // Roles added later should default to no emission until classified.
                    break;
            }
        }
    }

    private void markMemberCells(Compound compound, boolean[][] memberCells) {
        for (BlockLeaf m : compound.members) {
            for (int y = m.top; y <= m.bottom; y++) {
                for (int x = m.left; x <= m.right; x++) {
                    memberCells[x][y] = true;
                }
            }
        }
    }

    /**
     * Walk the bounding box of the compound; for each cell that's a road but
     * not yet a member, check whether members exist within
     * {@link #BRIDGE_SCAN_DEPTH} cells in both N/S or both E/W directions.
     * If so the cell is "bridged" — the compound encloses it. This is what
     * makes a 3-cell road frame between two stacked member leaves register
     * as a single connected interior parade ground.
     */
    private void markBridgedRoads(Compound compound, boolean[][] roadCells,
                                  boolean[][] memberCells, boolean[][] inCompound) {
        int w = inCompound.length;
        int h = inCompound[0].length;
        int lo = Math.max(0, compound.left - 1);
        int hi = Math.min(w - 1, compound.right + 1);
        int top = Math.max(0, compound.top - 1);
        int bot = Math.min(h - 1, compound.bottom + 1);
        for (int y = top; y <= bot; y++) {
            for (int x = lo; x <= hi; x++) {
                if (inCompound[x][y]) continue;
                if (!roadCells[x][y]) continue;
                boolean north = scanForMember(memberCells, x, y, 0, -1, w, h);
                boolean south = scanForMember(memberCells, x, y, 0,  1, w, h);
                boolean east  = scanForMember(memberCells, x, y, 1,  0, w, h);
                boolean west  = scanForMember(memberCells, x, y, -1, 0, w, h);
                if ((north && south) || (east && west)) {
                    inCompound[x][y] = true;
                }
            }
        }
    }

    /**
     * Absorb concave-notch cells into the compound. An L-shaped or U-shaped
     * compound can have road cells inside its bounding box that aren't
     * reachable from the outside playfield but also aren't members or
     * bridged — surrounded by compound cells on enough sides that
     * {@link #paintWallRing} seals them in. Without this pass those become
     * walkable-but-isolated pockets that fail the map's connectivity check.
     *
     * <p>Flood-fill from cells just outside the bounding box; any cell inside
     * the box not reached by the outside flood and not already in the
     * compound is a notch and gets pulled in.
     */
    private void absorbConcaveNotches(Compound compound, boolean[][] inCompound) {
        int w = inCompound.length;
        int h = inCompound[0].length;
        boolean[][] outside = new boolean[w][h];
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        int[][] seeds = {
                {compound.left  - 1, compound.top    - 1},
                {compound.right + 1, compound.top    - 1},
                {compound.left  - 1, compound.bottom + 1},
                {compound.right + 1, compound.bottom + 1},
                {compound.left  - 1, (compound.top + compound.bottom) / 2},
                {compound.right + 1, (compound.top + compound.bottom) / 2},
                {(compound.left + compound.right) / 2, compound.top    - 1},
                {(compound.left + compound.right) / 2, compound.bottom + 1},
        };
        for (int[] s : seeds) {
            int sx = Math.max(0, Math.min(w - 1, s[0]));
            int sy = Math.max(0, Math.min(h - 1, s[1]));
            if (!inCompound[sx][sy] && !outside[sx][sy]) {
                outside[sx][sy] = true;
                q.add(new int[]{sx, sy});
            }
        }
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int px = p[0], py = p[1];
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : dirs) {
                int nx = px + d[0];
                int ny = py + d[1];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                if (outside[nx][ny] || inCompound[nx][ny]) continue;
                outside[nx][ny] = true;
                q.add(new int[]{nx, ny});
            }
        }
        for (int y = Math.max(0, compound.top - 1); y <= Math.min(h - 1, compound.bottom + 1); y++) {
            for (int x = Math.max(0, compound.left - 1); x <= Math.min(w - 1, compound.right + 1); x++) {
                if (inCompound[x][y]) continue;
                if (outside[x][y]) continue;
                inCompound[x][y] = true; // concave notch — absorb into compound
            }
        }
    }

    private boolean scanForMember(boolean[][] memberCells, int x, int y, int dx, int dy, int w, int h) {
        int cx = x + dx;
        int cy = y + dy;
        for (int i = 0; i < BRIDGE_SCAN_DEPTH; i++) {
            if (cx < 0 || cx >= w || cy < 0 || cy >= h) return false;
            if (memberCells[cx][cy]) return true;
            cx += dx;
            cy += dy;
        }
        return false;
    }

    /**
     * Paint the perimeter wall ring — every cell outside the compound that
     * has at least one 4-neighbor inside. The wall consumes 1 cell of road
     * frame on each side, leaving the rest of the BSP road frame walkable
     * for the surrounding street network.
     */
    private void paintWallRing(boolean[][] inCompound, NavigationGrid grid, CellTopology topology) {
        int w = inCompound.length;
        int h = inCompound[0].length;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (inCompound[x][y]) continue;
                // Map perimeter stays walkable — the outer road ring is shared
                // infrastructure that the rest of the map depends on. A wall
                // painted here can isolate a corner of the perimeter from the
                // road network when the compound abuts the map edge.
                if (x == 0 || x == w - 1 || y == 0 || y == h - 1) continue;
                if (!touchesCompound(inCompound, x, y, w, h)) continue;
                grid.setWalkable(x, y, false);
                grid.setWallHp(x, y, WALL_HP_FORTIFIED);
                topology.setGroundKind(x, y, WALL_GROUND);
            }
        }
    }

    private boolean touchesCompound(boolean[][] inCompound, int x, int y, int w, int h) {
        if (x + 1 < w  && inCompound[x + 1][y]) return true;
        if (x - 1 >= 0 && inCompound[x - 1][y]) return true;
        if (y + 1 < h  && inCompound[x][y + 1]) return true;
        if (y - 1 >= 0 && inCompound[x][y - 1]) return true;
        return false;
    }

    /**
     * Repaint bridged roads and the outer rim of every member leaf as STONE
     * parade ground. The leaf rim repaint gives the sub-building a 1-cell
     * "yard" between its wall and the compound's outer wall, which avoids a
     * double-wall artifact when a leaf sits against the compound perimeter.
     */
    private void repaintParadeGround(Compound compound, boolean[][] inCompound,
                                     boolean[][] memberCells, NavigationGrid grid, CellTopology topology) {
        int w = inCompound.length;
        int h = inCompound[0].length;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!inCompound[x][y]) continue;
                if (memberCells[x][y]) continue; // member-leaf cells get repainted via carve below
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, PARADE_GROUND);
            }
        }
        // Leaf rim repaint — outer perimeter cells of each member leaf become
        // parade ground; the actual sub-building carves inset by 1.
        for (BlockLeaf m : compound.members) {
            for (int x = m.left; x <= m.right; x++) {
                grid.setWalkableFloor(x, m.top);
                grid.setWalkableFloor(x, m.bottom);
                topology.setGroundKind(x, m.top,    PARADE_GROUND);
                topology.setGroundKind(x, m.bottom, PARADE_GROUND);
            }
            for (int y = m.top + 1; y <= m.bottom - 1; y++) {
                grid.setWalkableFloor(m.left,  y);
                grid.setWalkableFloor(m.right, y);
                topology.setGroundKind(m.left,  y, PARADE_GROUND);
                topology.setGroundKind(m.right, y, PARADE_GROUND);
            }
        }
    }

    /**
     * Carve one sub-building per member leaf, inset by 1 cell from the leaf
     * edge so the building wall stands inside the parade-ground rim painted
     * above. Reuses {@link BuildingShellCore#carve} with role-specific
     * config — same code path as standalone buildings.
     *
     * <p>Returns a leaf→POI map so {@link #emitTacticalNodes} can anchor
     * tactical nodes at the carved building's interior anchor (a walkable
     * INDOOR cell), giving garrison defenders an inside-the-building spawn.
     */
    private Map<BlockLeaf, PointOfInterest> carveSubBuildings(Compound compound, boolean[][] inCompound,
                                                              NavigationGrid grid, CellTopology topology,
                                                              List<Doodad> doodads, List<PointOfInterest> pois, Random rng) {
        Map<BlockLeaf, PointOfInterest> leafPois = new IdentityHashMap<>();
        for (BlockLeaf m : compound.members) {
            int subL = m.left   + 1;
            int subT = m.top    + 1;
            int subR = m.right  - 1;
            int subB = m.bottom - 1;
            if (subR - subL < 1 || subB - subT < 1) continue; // leaf too small to inset

            BlockLeaf inset = new BlockLeaf(subL, subT, subR, subB, false);
            inset.kind = m.kind;
            BuildingShellCore.BuildingConfig config = configFor(compound.roles.get(m));
            PointOfInterest poi = BuildingShellCore.carve(inset, grid, topology, doodads, rng, config);
            if (poi != null) {
                pois.add(poi);
                leafPois.put(m, poi);
            }
        }
        return leafPois;
    }

    private BuildingShellCore.BuildingConfig configFor(Compound.Role role) {
        if (role == null) return BARRACKS_CONFIG;
        switch (role) {
            case COMMAND:     return COMMAND_CONFIG;
            case BARRACKS:    return BARRACKS_CONFIG;
            case ARMORY:      return ARMORY_CONFIG;
            case VEHICLE_BAY: return VEHICLE_BAY_CONFIG;
            default:          return BARRACKS_CONFIG;
        }
    }

    /**
     * Punch up to 2 gates in the wall ring. A gate-eligible wall cell has
     * an outside neighbor that's walkable road (not perimeter/trunk wall
     * cell). The two gates are picked to be maximally separated so they
     * don't cluster on one side.
     */
    private void punchGates(Compound compound, boolean[][] inCompound, boolean[][] roadCells,
                            NavigationGrid grid, CellTopology topology, Random rng) {
        int w = inCompound.length;
        int h = inCompound[0].length;
        List<int[]> candidates = new ArrayList<>();
        for (int y = compound.top - 1; y <= compound.bottom + 1; y++) {
            for (int x = compound.left - 1; x <= compound.right + 1; x++) {
                if (x < 0 || x >= w || y < 0 || y >= h) continue;
                if (inCompound[x][y]) continue;
                if (grid.isWalkable(x, y)) continue; // already punched as gate or not actually a wall cell
                int[] inside = compoundNeighbor(inCompound, x, y, w, h);
                if (inside == null) continue;
                int[] outside = oppositeNeighbor(x, y, inside);
                if (outside[0] < 0 || outside[0] >= w || outside[1] < 0 || outside[1] >= h) continue;
                if (inCompound[outside[0]][outside[1]]) continue;
                if (!roadCells[outside[0]][outside[1]]) continue;
                candidates.add(new int[]{x, y, outside[0] - x, outside[1] - y});
            }
        }
        if (candidates.isEmpty()) return;
        Collections.shuffle(candidates, rng);

        int[] first = candidates.get(0);
        openGate(first, grid, topology);
        // Pair gate cell for width-2 opening, when the perpendicular neighbor
        // is also a gate-eligible wall cell.
        tryWiden(candidates, first, grid, topology);

        // Second gate — farthest from first.
        int[] second = null;
        int bestDist = -1;
        for (int[] c : candidates) {
            if (c == first) continue;
            int dx = c[0] - first[0];
            int dy = c[1] - first[1];
            int dist = Math.abs(dx) + Math.abs(dy);
            if (dist < 6) continue;
            if (dist > bestDist) { bestDist = dist; second = c; }
        }
        if (second != null) {
            openGate(second, grid, topology);
            tryWiden(candidates, second, grid, topology);
        }
    }

    private void tryWiden(List<int[]> candidates, int[] gate, NavigationGrid grid, CellTopology topology) {
        // Perpendicular to gate's outward direction: (-dy, dx) and (dy, -dx).
        int px1 = gate[0] - gate[3];
        int py1 = gate[1] + gate[2];
        int px2 = gate[0] + gate[3];
        int py2 = gate[1] - gate[2];
        for (int[] c : candidates) {
            if ((c[0] == px1 && c[1] == py1) || (c[0] == px2 && c[1] == py2)) {
                openGate(c, grid, topology);
                return;
            }
        }
    }

    private void openGate(int[] gate, NavigationGrid grid, CellTopology topology) {
        int x = gate[0];
        int y = gate[1];
        grid.setWalkable(x, y, true);
        grid.setDoorway(x, y, true);
        grid.openAllEdges(x, y);
        topology.setGroundKind(x, y, GroundKind.STRIPED);
    }

    private int[] compoundNeighbor(boolean[][] inCompound, int x, int y, int w, int h) {
        if (x + 1 < w  && inCompound[x + 1][y]) return new int[]{x + 1, y};
        if (x - 1 >= 0 && inCompound[x - 1][y]) return new int[]{x - 1, y};
        if (y + 1 < h  && inCompound[x][y + 1]) return new int[]{x, y + 1};
        if (y - 1 >= 0 && inCompound[x][y - 1]) return new int[]{x, y - 1};
        return null;
    }

    private int[] oppositeNeighbor(int x, int y, int[] inside) {
        return new int[]{x - (inside[0] - x), y - (inside[1] - y)};
    }

    /**
     * Stamp the four bounding-box corners of the compound — when they're
     * actually wall cells — as VEHICLE-flagged hardpoints. Reads as turreted
     * corner emplacements in the renderer; functionally the cell is still
     * non-walkable wall so units can't path through it.
     */
    private void stampGunEmplacements(Compound compound, boolean[][] inCompound,
                                      NavigationGrid grid, CellTopology topology,
                                      List<PointOfInterest> pois) {
        int w = inCompound.length;
        int h = inCompound[0].length;
        int[][] corners = {
                {compound.left  - 1, compound.top    - 1},
                {compound.right + 1, compound.top    - 1},
                {compound.left  - 1, compound.bottom + 1},
                {compound.right + 1, compound.bottom + 1},
        };
        for (int[] c : corners) {
            int x = c[0];
            int y = c[1];
            if (x < 0 || x >= w || y < 0 || y >= h) continue;
            // Skip map perimeter for the same reason paintWallRing does —
            // perimeter is shared road infrastructure.
            if (x == 0 || x == w - 1 || y == 0 || y == h - 1) continue;
            if (inCompound[x][y]) continue;
            if (!touchesCompound(inCompound, x, y, w, h)) continue;
            grid.setWalkable(x, y, false);
            grid.setWallHp(x, y, WALL_HP_FORTIFIED);
            topology.setGroundKind(x, y, EMPLACEMENT_GROUND);
            topology.setVehicle(x, y, true);
        }
    }
}
