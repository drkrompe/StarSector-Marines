package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.decision.TacticalNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Stamps the perimeter wall around the FORTRESS_DISTRICT biome on conquest
 * maps — the "Kremlin" super-wall the climax of the mission breaches. Runs
 * after BSP fill so the wall overrides whatever the leaf fillers put in its
 * path.
 *
 * <h2>Geometry</h2>
 * Wall is an axis-aligned rectangle inset {@link #SETBACK_CELLS} into the
 * fortress biome's bounding box, leaving a "kill zone" buffer between the
 * biome edge and the wall. Only three sides are drawn — the back side abuts
 * the map edge, which is impassable already. For
 * {@link TraversalAxis#SOUTH_TO_NORTH} the attacker-facing wall is the south
 * edge; the east/west walls are returns that meet the map edge at the north.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Corner towers</b> — 3×3 protrusions at the two attacker-side
 *       corners; turret mount at center (VEHICLE flag).</li>
 *   <li><b>Mid-line heavy towers</b> — 3×3 protrusions every
 *       {@link #HEAVY_TOWER_SPACING} cells along the attacker-facing wall,
 *       same mount pattern as corners.</li>
 *   <li><b>MG nests</b> — single VEHICLE-flagged cells on the wall itself
 *       (reads as crenellation MG), spaced every {@link #MG_NEST_SPACING}
 *       cells in the gaps between heavies.</li>
 *   <li><b>Gates</b> — 1–3 {@link #GATE_WIDTH}-cell openings on the
 *       attacker-facing wall, jittered, never overlapping a tower or MG, with
 *       {@link #MIN_GATE_SEPARATION} between gates.</li>
 *   <li><b>Forward bunkers</b> — 2–4 free-standing 3×3 towers in the
 *       kill-zone buffer, paired with heavy turret mounts. Forces attackers
 *       to clear forward positions before assaulting the wall.</li>
 * </ul>
 *
 * <h2>Connectivity</h2>
 * The wall divides the map into "attacker side" (outside) and "fortress
 * interior" (inside). Both are walkable; gates link them. {@link
 * #sealOrphanedPockets} sweeps the grid after stamping to fill any walkable
 * cell that ends up in neither region (e.g., a building interior whose only
 * doorway was painted over by the wall), preserving the map's single-component
 * walkability invariant that the preview test asserts.
 *
 * <p>Pipeline step 3c, run as a {@link GenStage}: {@link #run} pulls the biome
 * map, compound list, axis, road reservation, and output accumulators off the
 * {@link GenContext}. Conquest-only — a no-op when {@link BspKeys#BIOME_MAP} is
 * unbound.
 */
public final class FortressWallStamper implements GenStage {

    /** Pull the wall this many cells back from the fortress biome's bounding box. Larger = bigger kill-zone buffer; smaller = wall hugs the biome edge. */
    private static final int SETBACK_CELLS = 12;
    /** Wall HP. Higher than building walls (100) and military-base perimeter (150) — this is THE wall, breaching it is a mission objective. */
    private static final int WALL_HP_FORTIFIED = 240;
    /** Tower side length. 3×3 is large enough to read as a tower and small enough that two heavy towers don't fight for space at typical spacings. */
    private static final int TOWER_SIZE = 3;
    /** Nominal cells between heavy mid-towers along the attacker-facing wall. Actual count is wall span / spacing rounded; corners are always present in addition. */
    private static final int HEAVY_TOWER_SPACING = 36;
    /** Nominal cells between MG nests in the gaps between heavies. Smaller = more MGs. */
    private static final int MG_NEST_SPACING = 12;
    /** Gate gap width in cells. 3 lets squads + a vehicle pass abreast. */
    private static final int GATE_WIDTH = 3;
    /** Minimum cells between any two gates on the attacker-facing wall. Prevents two gates clumping at one end. */
    private static final int MIN_GATE_SEPARATION = 28;
    /** Min/max gate count rolled at gen time. */
    private static final int GATE_COUNT_MIN = 1;
    private static final int GATE_COUNT_MAX = 3;
    /** Min/max forward bunker count rolled at gen time. */
    private static final int BUNKER_COUNT_MIN = 2;
    private static final int BUNKER_COUNT_MAX = 4;
    /** Forward bunker side length. Matches tower size so the silhouettes read consistently across the fortress complex. */
    private static final int BUNKER_SIZE = 3;
    /** Minimum cells between forward bunkers — keeps them spread along the kill zone instead of clumping. */
    private static final int BUNKER_MIN_SEPARATION = 25;
    /** Floor under wall cells. STRIPED reads as "military safety floor" when breached. */
    private static final GroundKind WALL_GROUND = GroundKind.STRIPED;
    /** Floor under turret mounts. STONE reads as "paved turret pad" — same as MilitaryBaseFiller's gun emplacements. */
    private static final GroundKind TURRET_PAD = GroundKind.STONE;
    /** Cells within this radius of a wall cell get a building-demolition sweep. Catches bisected buildings whose interior straddles the wall line. */
    private static final int DEMOLISH_RADIUS = 2;
    /** Ground that demolished building footprints fall back to — STONE reads as cleared parade ground / fortified killing field. */
    private static final GroundKind DEMOLISHED_GROUND = GroundKind.STONE;

    public FortressWallStamper() {}

    /**
     * Stamp the wall + towers + gates + forward bunkers. No-op when the biome
     * map is unbound (legacy non-conquest path) or the fortress biome's
     * bounding box is too small to fit a meaningful wall (degenerate biome
     * layouts on very small maps).
     *
     * <p>Mutates {@code ctx.doodads} — strips any entries that fall inside a
     * demolished building footprint, so debris doesn't float in mid-air on
     * cleared parade ground.
     */
    @Override
    public void run(GenContext ctx) {
        BiomeMap biomeMap = ctx.get(BspKeys.BIOME_MAP);
        if (biomeMap == null) return;
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        TraversalAxis axis = ctx.get(BspKeys.AXIS);
        Random rng = ctx.rng;
        int w = grid.getWidth();
        int h = grid.getHeight();
        int[] bbox = fortressBbox(biomeMap, w, h);
        if (bbox == null) return;
        boolean[][] compoundExclusion = buildCompoundExclusion(ctx.get(BspKeys.COMPOUNDS), w, h);
        boolean[][] skip = mergeExclusions(ctx.get(BspKeys.ROAD_RESERVATION), compoundExclusion, w, h);
        boolean[][] wallMask = new boolean[w][h];
        if (axis == TraversalAxis.SOUTH_TO_NORTH) {
            stampSouthToNorth(grid, topology, bbox, wallMask, skip, ctx.tactical, w, h, rng);
        } else {
            stampWestToEast(grid, topology, bbox, wallMask, skip, ctx.tactical, w, h, rng);
        }
        demolishIntersectedBuildings(grid, topology, ctx.doodads, wallMask, w, h);
        sealOrphanedPockets(grid, w, h);
    }

    /**
     * Build an exclusion mask covering all compound member cells + a 1-cell
     * buffer (the compound perimeter wall ring). The wall stamper skips these
     * cells so it doesn't bisect compound sub-buildings. A {@code null} or empty
     * compound list yields an all-false mask (nothing excluded).
     */
    private static boolean[][] buildCompoundExclusion(List<Compound> compounds, int w, int h) {
        boolean[][] mask = new boolean[w][h];
        if (compounds == null) return mask;
        for (Compound c : compounds) {
            int bufL = Math.max(0, c.left - 2);
            int bufT = Math.max(0, c.top - 2);
            int bufR = Math.min(w - 1, c.right + 2);
            int bufB = Math.min(h - 1, c.bottom + 2);
            for (int y = bufT; y <= bufB; y++) {
                for (int x = bufL; x <= bufR; x++) {
                    mask[x][y] = true;
                }
            }
        }
        return mask;
    }

    private static boolean[][] mergeExclusions(boolean[][] road, boolean[][] compound, int w, int h) {
        if (compound == null) return road;
        if (road == null) return compound;
        boolean[][] merged = new boolean[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                merged[x][y] = road[x][y] || compound[x][y];
            }
        }
        return merged;
    }

    private static int[] fortressBbox(BiomeMap biomeMap, int w, int h) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        int top = Integer.MAX_VALUE, bot = Integer.MIN_VALUE;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (biomeMap.biomeAt(x, y) != BiomeKind.FORTRESS_DISTRICT) continue;
                if (x < lo) lo = x;
                if (x > hi) hi = x;
                if (y < top) top = y;
                if (y > bot) bot = y;
            }
        }
        if (lo == Integer.MAX_VALUE) return null;
        return new int[]{ lo, top, hi, bot };
    }

    /**
     * SOUTH_TO_NORTH layout. Attacker is at low y; fortress is at high y.
     * The attacker-facing wall is horizontal at {@code wBot}; east/west walls
     * are vertical returns up to the map edge.
     */
    private static void stampSouthToNorth(NavigationGrid grid, CellTopology topology,
                                          int[] bbox, boolean[][] wallMask,
                                          boolean[][] roadReservation,
                                          List<TacticalNode> tactical,
                                          int w, int h, Random rng) {
        int fLeft   = bbox[0];
        int fBot    = bbox[1];   // attacker-facing edge of fortress biome
        int fRight  = bbox[2];
        int fTop    = bbox[3];   // back of fortress (high y); usually map edge

        int wLeft   = Math.max(2, fLeft + SETBACK_CELLS);
        int wRight  = Math.min(w - 3, fRight - SETBACK_CELLS);
        int wBot    = fBot + SETBACK_CELLS;
        int wTop    = Math.min(h - 1, fTop);

        if (wRight - wLeft < 2 * HEAVY_TOWER_SPACING) return;
        if (wTop - wBot < 6) return;

        // 1. Stamp wall cells. South (attacker-facing) horizontal, plus the
        //    east + west vertical returns up to the map edge.
        for (int x = wLeft; x <= wRight; x++) {
            paintWall(grid, topology, x, wBot, wallMask, roadReservation);
        }
        for (int y = wBot; y <= wTop; y++) {
            paintWall(grid, topology, wLeft, y, wallMask, roadReservation);
            paintWall(grid, topology, wRight, y, wallMask, roadReservation);
        }

        // 2. Towers — corners + mid-line heavies. Each tower is a 3×3 block
        //    protruding south of the wall line. Center cell is the turret
        //    mount (VEHICLE flag); remaining cells are wall.
        List<Integer> towerCentersX = new ArrayList<>();
        // SW corner: center at (wLeft, wBot-1) — tower spans x in [wLeft-1, wLeft+1], y in [wBot-2, wBot].
        stampTower3x3(grid, topology, wLeft, wBot - 1, wallMask, roadReservation);
        emitHeavyTower(tactical, wLeft, wBot - 1);
        towerCentersX.add(wLeft);
        // SE corner
        stampTower3x3(grid, topology, wRight, wBot - 1, wallMask, roadReservation);
        emitHeavyTower(tactical, wRight, wBot - 1);
        towerCentersX.add(wRight);
        // Mid-line heavies — evenly spaced between corners along the south wall.
        int span = wRight - wLeft;
        int innerCount = Math.max(0, (span / HEAVY_TOWER_SPACING) - 1);
        for (int i = 1; i <= innerCount; i++) {
            int cx = wLeft + (span * i) / (innerCount + 1);
            stampTower3x3(grid, topology, cx, wBot - 1, wallMask, roadReservation);
            emitHeavyTower(tactical, cx, wBot - 1);
            towerCentersX.add(cx);
        }

        // 3. MG nests — single VEHICLE cells on the wall row, every
        //    MG_NEST_SPACING cells, skipping any column claimed by a tower
        //    (tower x-span is centerX ± 1).
        List<Integer> mgX = new ArrayList<>();
        for (int x = wLeft + MG_NEST_SPACING; x <= wRight - MG_NEST_SPACING; x += MG_NEST_SPACING) {
            if (isNearTower(x, towerCentersX, TOWER_SIZE)) continue;
            stampMgNest(grid, topology, x, wBot, wallMask, roadReservation);
            emitMgNest(tactical, x, wBot);
            mgX.add(x);
        }

        // 4. Gates — punch 1-3 GATE_WIDTH-cell openings in the south wall
        //    only. Avoid tower spans (already non-walkable as part of tower)
        //    and MG cells. Spacing: at least MIN_GATE_SEPARATION between gates.
        int gateCount = GATE_COUNT_MIN + rng.nextInt(GATE_COUNT_MAX - GATE_COUNT_MIN + 1);
        List<Integer> gates = new ArrayList<>();
        int maxAttempts = gateCount * 40;
        int attempts = 0;
        while (gates.size() < gateCount && attempts < maxAttempts) {
            attempts++;
            int gx = wLeft + 4 + rng.nextInt(Math.max(1, span - 8 - GATE_WIDTH));
            if (isNearTower(gx, towerCentersX, TOWER_SIZE + GATE_WIDTH / 2 + 1)) continue;
            if (isNearTower(gx + GATE_WIDTH - 1, towerCentersX, TOWER_SIZE + GATE_WIDTH / 2 + 1)) continue;
            boolean tooCloseToGate = false;
            for (int g : gates) {
                if (Math.abs(gx - g) < MIN_GATE_SEPARATION) { tooCloseToGate = true; break; }
            }
            if (tooCloseToGate) continue;
            openGate(grid, topology, gx, wBot, GATE_WIDTH, mgX);
            emitGate(tactical, gx, wBot, GATE_WIDTH, true);
            gates.add(gx);
        }

        // 5. Forward bunkers — 2-4 free-standing 3×3 towers in the kill zone
        //    (between fortress-biome south edge and the wall). Each is a
        //    full bunker tower with a center turret mount.
        int bunkerCount = BUNKER_COUNT_MIN + rng.nextInt(BUNKER_COUNT_MAX - BUNKER_COUNT_MIN + 1);
        List<int[]> bunkerCenters = new ArrayList<>();
        int killZoneTop = wBot - 3;   // leave 2-cell gap between bunker and wall
        int killZoneBot = fBot + 2;   // small buffer on the biome-edge side too
        if (killZoneTop - killZoneBot < BUNKER_SIZE) return;
        int bxAttempts = bunkerCount * 50;
        for (int a = 0; a < bxAttempts && bunkerCenters.size() < bunkerCount; a++) {
            int bx = wLeft + 4 + rng.nextInt(Math.max(1, span - 8));
            int by = killZoneBot + rng.nextInt(Math.max(1, killZoneTop - killZoneBot));
            boolean tooClose = false;
            for (int[] b : bunkerCenters) {
                int dx = b[0] - bx;
                int dy = b[1] - by;
                if (dx * dx + dy * dy < BUNKER_MIN_SEPARATION * BUNKER_MIN_SEPARATION) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) continue;
            stampTower3x3(grid, topology, bx, by, wallMask, roadReservation);
            emitForwardBunker(tactical, bx, by);
            bunkerCenters.add(new int[]{bx, by});
        }

        // Final pass — set wall direction masks for every painted wall cell.
        // Courtyard rect (kremlin interior) is the inside of the perimeter
        // walls; tower bulges and forward bunkers sit OUTSIDE the courtyard
        // so their walls get outward-facing bits set automatically by the
        // "neighbor not wall and not in courtyard → exterior" rule.
        applyFortressMasks(topology, wallMask,
                wLeft + 1, wRight - 1, wBot + 1, wTop,
                grid.getWidth(), grid.getHeight());
    }

    /**
     * WEST_TO_EAST layout — mirrors {@link #stampSouthToNorth} with x and y
     * swapped. The attacker-facing wall is vertical at {@code wLeft}; the
     * north/south walls are horizontal returns to the map edge at x=w-1.
     */
    private static void stampWestToEast(NavigationGrid grid, CellTopology topology,
                                        int[] bbox, boolean[][] wallMask,
                                        boolean[][] roadReservation,
                                        List<TacticalNode> tactical,
                                        int w, int h, Random rng) {
        int fLeft   = bbox[0];   // attacker-facing edge of fortress biome (low x)
        int fBot    = bbox[1];
        int fRight  = bbox[2];   // back of fortress (high x); usually map edge
        int fTop    = bbox[3];

        int wBot    = Math.max(2, fBot + SETBACK_CELLS);
        int wTop    = Math.min(h - 3, fTop - SETBACK_CELLS);
        int wLeft   = fLeft + SETBACK_CELLS;
        int wRight  = Math.min(w - 1, fRight);

        if (wTop - wBot < 2 * HEAVY_TOWER_SPACING) return;
        if (wRight - wLeft < 6) return;

        for (int y = wBot; y <= wTop; y++) paintWall(grid, topology, wLeft, y, wallMask, roadReservation);
        for (int x = wLeft; x <= wRight; x++) {
            paintWall(grid, topology, x, wBot, wallMask, roadReservation);
            paintWall(grid, topology, x, wTop, wallMask, roadReservation);
        }

        List<Integer> towerCentersY = new ArrayList<>();
        stampTower3x3(grid, topology, wLeft + 1, wBot, wallMask, roadReservation);
        emitHeavyTower(tactical, wLeft + 1, wBot);
        towerCentersY.add(wBot);
        stampTower3x3(grid, topology, wLeft + 1, wTop, wallMask, roadReservation);
        emitHeavyTower(tactical, wLeft + 1, wTop);
        towerCentersY.add(wTop);
        int span = wTop - wBot;
        int innerCount = Math.max(0, (span / HEAVY_TOWER_SPACING) - 1);
        for (int i = 1; i <= innerCount; i++) {
            int cy = wBot + (span * i) / (innerCount + 1);
            stampTower3x3(grid, topology, wLeft + 1, cy, wallMask, roadReservation);
            emitHeavyTower(tactical, wLeft + 1, cy);
            towerCentersY.add(cy);
        }

        List<Integer> mgY = new ArrayList<>();
        for (int y = wBot + MG_NEST_SPACING; y <= wTop - MG_NEST_SPACING; y += MG_NEST_SPACING) {
            if (isNearTower(y, towerCentersY, TOWER_SIZE)) continue;
            stampMgNest(grid, topology, wLeft, y, wallMask, roadReservation);
            emitMgNest(tactical, wLeft, y);
            mgY.add(y);
        }

        int gateCount = GATE_COUNT_MIN + rng.nextInt(GATE_COUNT_MAX - GATE_COUNT_MIN + 1);
        List<Integer> gates = new ArrayList<>();
        int maxAttempts = gateCount * 40;
        int attempts = 0;
        while (gates.size() < gateCount && attempts < maxAttempts) {
            attempts++;
            int gy = wBot + 4 + rng.nextInt(Math.max(1, span - 8 - GATE_WIDTH));
            if (isNearTower(gy, towerCentersY, TOWER_SIZE + GATE_WIDTH / 2 + 1)) continue;
            if (isNearTower(gy + GATE_WIDTH - 1, towerCentersY, TOWER_SIZE + GATE_WIDTH / 2 + 1)) continue;
            boolean tooClose = false;
            for (int g : gates) {
                if (Math.abs(gy - g) < MIN_GATE_SEPARATION) { tooClose = true; break; }
            }
            if (tooClose) continue;
            openGateVertical(grid, topology, wLeft, gy, GATE_WIDTH, mgY);
            emitGate(tactical, wLeft, gy, GATE_WIDTH, false);
            gates.add(gy);
        }

        int bunkerCount = BUNKER_COUNT_MIN + rng.nextInt(BUNKER_COUNT_MAX - BUNKER_COUNT_MIN + 1);
        List<int[]> bunkerCenters = new ArrayList<>();
        int killZoneLeft  = fLeft + 2;
        int killZoneRight = wLeft - 3;
        if (killZoneRight - killZoneLeft < BUNKER_SIZE) return;
        int bxAttempts = bunkerCount * 50;
        for (int a = 0; a < bxAttempts && bunkerCenters.size() < bunkerCount; a++) {
            int bx = killZoneLeft + rng.nextInt(Math.max(1, killZoneRight - killZoneLeft));
            int by = wBot + 4 + rng.nextInt(Math.max(1, span - 8));
            boolean tooClose = false;
            for (int[] b : bunkerCenters) {
                int dx = b[0] - bx;
                int dy = b[1] - by;
                if (dx * dx + dy * dy < BUNKER_MIN_SEPARATION * BUNKER_MIN_SEPARATION) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) continue;
            stampTower3x3(grid, topology, bx, by, wallMask, roadReservation);
            emitForwardBunker(tactical, bx, by);
            bunkerCenters.add(new int[]{bx, by});
        }

        // Final pass — courtyard rect spans (wLeft+1..wRight) × (wBot+1..wTop-1).
        // wRight = w-1 is the map-edge side (no east wall), so cells at
        // x == wRight that aren't on the north/south returns count as
        // courtyard (the structure is open to the map edge there).
        applyFortressMasks(topology, wallMask,
                wLeft + 1, wRight, wBot + 1, wTop - 1,
                grid.getWidth(), grid.getHeight());
    }

    /** HEAVY_TOWER node — anchor at the turret-mount center; bbox covers the 3×3 footprint. */
    private static void emitHeavyTower(List<TacticalNode> tactical, int cx, int cy) {
        tactical.add(new TacticalNode(TacticalNode.Kind.HEAVY_TOWER,
                cx, cy, cx - 1, cy - 1, cx + 1, cy + 1,
                Faction.DEFENDER, 80, 2));
    }

    /** MG_NEST node — single cell on the wall. */
    private static void emitMgNest(List<TacticalNode> tactical, int x, int y) {
        tactical.add(new TacticalNode(TacticalNode.Kind.MG_NEST,
                x, y, x, y, x, y,
                Faction.DEFENDER, 50, 1));
    }

    /** FORWARD_BUNKER node — anchor at the turret-mount center; same footprint as a heavy tower but freestanding in the kill zone. */
    private static void emitForwardBunker(List<TacticalNode> tactical, int cx, int cy) {
        tactical.add(new TacticalNode(TacticalNode.Kind.FORWARD_BUNKER,
                cx, cy, cx - 1, cy - 1, cx + 1, cy + 1,
                Faction.DEFENDER, 65, 2));
    }

    /**
     * GATE node — bbox spans the gap; anchor at the gate center cell.
     * {@code horizontal=true} means the gate runs along x (south wall);
     * false means along y (west wall on W→E maps).
     */
    private static void emitGate(List<TacticalNode> tactical, int x0, int y0, int width, boolean horizontal) {
        int cx, cy, l, t, r, b;
        if (horizontal) {
            cx = x0 + width / 2;
            cy = y0;
            l = x0;
            r = x0 + width - 1;
            t = b = y0;
        } else {
            cx = x0;
            cy = y0 + width / 2;
            t = y0;
            b = y0 + width - 1;
            l = r = x0;
        }
        tactical.add(new TacticalNode(TacticalNode.Kind.GATE,
                cx, cy, l, t, r, b,
                Faction.DEFENDER, 90, 3));
    }

    /**
     * Walks {@code wallMask} and sets each wall cell's direction mask based
     * on the geometry of the placed walls + the kremlin courtyard rect. A
     * neighbor counts as "exterior" (its side gets a cap bit set) when it's
     * out of bounds, or it's a non-wall cell that lies outside the
     * courtyard rect — i.e., kill zone, gate hole, or anywhere the map
     * extends past the wall structure. Neighbors that are themselves walls
     * (wall continuations, tower interiors, forward-bunker neighbors) and
     * neighbors inside the courtyard (the fortress's own interior) both
     * count as "not exterior" — no cap bit on that side.
     *
     * <p>This is the once-at-gen-time mask compute that mirrors what
     * {@link com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingShellCore#stampPerimeterMask}
     * does directly from leaf geometry. Fortress walls have more variety
     * (3-sided rect + bulging towers + freestanding bunkers in the kill
     * zone), so the mask is derived from {@code wallMask} + courtyard rect
     * rather than spelled out per-cell.
     */
    private static void applyFortressMasks(CellTopology topology, boolean[][] wallMask,
                                           int courtyardL, int courtyardR,
                                           int courtyardB, int courtyardT,
                                           int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!wallMask[x][y]) continue;
                int mask = 0;
                if (isExteriorNeighbor(x,     y + 1, wallMask, courtyardL, courtyardR, courtyardB, courtyardT, w, h)) mask |= CellTopology.WALL_DIR_N;
                if (isExteriorNeighbor(x,     y - 1, wallMask, courtyardL, courtyardR, courtyardB, courtyardT, w, h)) mask |= CellTopology.WALL_DIR_S;
                if (isExteriorNeighbor(x + 1, y,     wallMask, courtyardL, courtyardR, courtyardB, courtyardT, w, h)) mask |= CellTopology.WALL_DIR_E;
                if (isExteriorNeighbor(x - 1, y,     wallMask, courtyardL, courtyardR, courtyardB, courtyardT, w, h)) mask |= CellTopology.WALL_DIR_W;
                topology.setWallDirMask(x, y, mask);
            }
        }
    }

    /** True if {@code (x, y)} counts as the fortress exterior — OOB or non-wall and outside the courtyard rect. */
    private static boolean isExteriorNeighbor(int x, int y, boolean[][] wallMask,
                                              int courtyardL, int courtyardR,
                                              int courtyardB, int courtyardT,
                                              int w, int h) {
        if (x < 0 || x >= w || y < 0 || y >= h) return true;
        if (wallMask[x][y]) return false;
        boolean inCourtyard = x >= courtyardL && x <= courtyardR
                           && y >= courtyardB && y <= courtyardT;
        return !inCourtyard;
    }

    /**
     * Stamp one wall cell — non-walkable, HP'd, STRIPED ground so a breach reads as military floor.
     * No-op for road-graph reserved cells: the trunk that runs through the fortress
     * becomes an implicit gate where the wall would otherwise have blocked it.
     */
    private static void paintWall(NavigationGrid grid, CellTopology topology, int x, int y,
                                  boolean[][] wallMask, boolean[][] roadReservation) {
        if (!grid.inBounds(x, y)) return;
        if (roadReservation != null && roadReservation[x][y]) return;
        grid.setWalkable(x, y, false);
        grid.setWallHp(x, y, WALL_HP_FORTIFIED);
        topology.setGroundKind(x, y, WALL_GROUND);
        wallMask[x][y] = true;
    }

    /**
     * 3×3 tower centered at (cx, cy). Eight perimeter cells are walls; the
     * center cell is a turret mount (VEHICLE flag, STONE pad). Tower cells
     * outside the map bounds are silently skipped — corner towers on the very
     * edge of the map naturally have a clipped footprint.
     */
    private static void stampTower3x3(NavigationGrid grid, CellTopology topology, int cx, int cy,
                                      boolean[][] wallMask, boolean[][] roadReservation) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int x = cx + dx;
                int y = cy + dy;
                if (!grid.inBounds(x, y)) continue;
                if (roadReservation != null && roadReservation[x][y]) continue;
                if (dx == 0 && dy == 0) {
                    grid.setWalkable(x, y, false);
                    grid.setWallHp(x, y, WALL_HP_FORTIFIED);
                    topology.setGroundKind(x, y, TURRET_PAD);
                    topology.setVehicle(x, y, true);
                    wallMask[x][y] = true;
                } else {
                    paintWall(grid, topology, x, y, wallMask, roadReservation);
                }
            }
        }
    }

    /**
     * Single VEHICLE-flagged cell on the wall row. Reads as crenellation MG
     * in the renderer. The cell is still non-walkable wall — the turret mount
     * sits ON the wall, not beside it.
     */
    private static void stampMgNest(NavigationGrid grid, CellTopology topology, int x, int y,
                                    boolean[][] wallMask, boolean[][] roadReservation) {
        if (!grid.inBounds(x, y)) return;
        if (roadReservation != null && roadReservation[x][y]) return;
        grid.setWalkable(x, y, false);
        grid.setWallHp(x, y, WALL_HP_FORTIFIED);
        topology.setGroundKind(x, y, TURRET_PAD);
        topology.setVehicle(x, y, true);
        wallMask[x][y] = true;
    }

    /**
     * Punch a GATE_WIDTH-cell opening on the horizontal south wall, centered
     * (or skewed if it hits a tower span). MG cells in the gap are reverted
     * to walkable along with the wall cells.
     */
    private static void openGate(NavigationGrid grid, CellTopology topology,
                                 int gateLeftX, int wallY, int width, List<Integer> mgX) {
        for (int dx = 0; dx < width; dx++) {
            int x = gateLeftX + dx;
            if (!grid.inBounds(x, wallY)) continue;
            grid.setWalkableFloor(x, wallY);
            grid.setDoorway(x, wallY, true);
            grid.openAllEdges(x, wallY);
            topology.setVehicle(x, wallY, false);
            topology.setGroundKind(x, wallY, GroundKind.STRIPED);
        }
        // No need to walk the mgX list — we already cleared the VEHICLE flag
        // on every cell we painted. Listing it for future use (e.g., gate
        // gauntlets that re-mount MGs nearby).
    }

    private static void openGateVertical(NavigationGrid grid, CellTopology topology,
                                          int wallX, int gateBotY, int width, List<Integer> mgY) {
        for (int dy = 0; dy < width; dy++) {
            int y = gateBotY + dy;
            if (!grid.inBounds(wallX, y)) continue;
            grid.setWalkableFloor(wallX, y);
            grid.setDoorway(wallX, y, true);
            grid.openAllEdges(wallX, y);
            topology.setVehicle(wallX, y, false);
            topology.setGroundKind(wallX, y, GroundKind.STRIPED);
        }
    }

    private static boolean isNearTower(int coord, List<Integer> towerCenters, int radius) {
        for (int c : towerCenters) {
            if (Math.abs(coord - c) < radius) return true;
        }
        return false;
    }

    /**
     * Demolish any building whose footprint intersects the wall sweep zone
     * (cells within {@link #DEMOLISH_RADIUS} of a wall cell). Without this
     * pass the wall paints over half a building and leaves the rest of its
     * interior (INDOOR ground) visible on either side — reads as a bisected
     * structure rather than a clean fortification.
     *
     * <p>Implementation: flood-fill every connected INDOOR region that has
     * at least one cell in the sweep zone, then clear (a) all flooded cells,
     * (b) any adjacent non-walkable cells (the building's original walls),
     * and (c) any doodads that fall on cleared cells. Cleared cells become
     * walkable {@link #DEMOLISHED_GROUND} — reads as parade ground / fortified
     * killing field.
     *
     * <p>The flood reaches a whole building rather than just the cells in
     * the radius, so we never leave a partial building behind. If a building
     * extends well past the sweep zone, the entire building still gets
     * cleared — that's intentional: any structure touching the wall is part
     * of the fortification and shouldn't read as an independent block.
     */
    private static void demolishIntersectedBuildings(NavigationGrid grid, CellTopology topology,
                                                      List<Doodad> doodads,
                                                      boolean[][] wallMask, int w, int h) {
        boolean[][] sweepZone = dilateMask(wallMask, DEMOLISH_RADIUS, w, h);

        boolean[][] toClear = new boolean[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!sweepZone[x][y]) continue;
                if (wallMask[x][y]) continue;
                if (toClear[x][y]) continue;
                if (topology.getGroundKind(x, y) != GroundKind.INDOOR) continue;
                floodIndoor(x, y, topology, toClear, w, h);
            }
        }

        // Pass 1: clear flooded INDOOR interiors.
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!toClear[x][y]) continue;
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, DEMOLISHED_GROUND);
            }
        }

        // Pass 2: clear non-walkable cells adjacent to demolished interiors
        // (the building's original walls — orphans now that the interior is gone).
        boolean[][] wallsCleared = new boolean[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (wallMask[x][y]) continue;
                if (toClear[x][y]) continue;
                if (grid.isWalkable(x, y)) continue;
                if (!hasClearedNeighbor(toClear, x, y, w, h)) continue;
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, DEMOLISHED_GROUND);
                wallsCleared[x][y] = true;
            }
        }

        // Pass 3: strip doodads in demolished footprints — debris on cleared
        // parade ground looks wrong, and orphaned crates inside the kill zone
        // give attackers free cover the level designer didn't intend.
        doodads.removeIf(d -> {
            int dx = d.cellX;
            int dy = d.cellY;
            if (dx < 0 || dx >= w || dy < 0 || dy >= h) return false;
            return toClear[dx][dy] || wallsCleared[dx][dy];
        });
    }

    private static boolean[][] dilateMask(boolean[][] mask, int radius, int w, int h) {
        boolean[][] out = new boolean[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!mask[x][y]) continue;
                int x0 = Math.max(0, x - radius);
                int x1 = Math.min(w - 1, x + radius);
                int y0 = Math.max(0, y - radius);
                int y1 = Math.min(h - 1, y + radius);
                for (int yy = y0; yy <= y1; yy++) {
                    for (int xx = x0; xx <= x1; xx++) {
                        out[xx][yy] = true;
                    }
                }
            }
        }
        return out;
    }

    private static void floodIndoor(int startX, int startY, CellTopology topology,
                                     boolean[][] toClear, int w, int h) {
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY});
        toClear[startX][startY] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                if (toClear[nx][ny]) continue;
                if (topology.getGroundKind(nx, ny) != GroundKind.INDOOR) continue;
                toClear[nx][ny] = true;
                queue.add(new int[]{nx, ny});
            }
        }
    }

    private static boolean hasClearedNeighbor(boolean[][] toClear, int x, int y, int w, int h) {
        if (x + 1 < w  && toClear[x + 1][y]) return true;
        if (x - 1 >= 0 && toClear[x - 1][y]) return true;
        if (y + 1 < h  && toClear[x][y + 1]) return true;
        if (y - 1 >= 0 && toClear[x][y - 1]) return true;
        return false;
    }

    /**
     * Flood-fill from every map-edge walkable cell and collect every cell
     * those floods reach. Any walkable cell NOT reached is a sealed pocket —
     * a building interior whose only doorway was painted over by the wall.
     * Fill those pockets in as non-walkable so the preview test's
     * single-component connectivity assertion still holds.
     *
     * <p>This is intentionally aggressive: the alternative (cutting a new
     * doorway through the wall) would introduce extra unintended gates.
     * Losing a few sealed building interiors to the wall is the right
     * tradeoff — they were going to be unusable anyway.
     */
    private static void sealOrphanedPockets(NavigationGrid grid, int w, int h) {
        boolean[][] reachable = new boolean[w][h];
        Deque<int[]> queue = new ArrayDeque<>();
        // Seed from every walkable cell along the map perimeter — guarantees
        // we flood from BOTH attacker side (south rows) AND fortress interior
        // (north rows past the wall), since each abuts the map edge.
        for (int x = 0; x < w; x++) {
            seedFlood(grid, reachable, queue, x, 0);
            seedFlood(grid, reachable, queue, x, h - 1);
        }
        for (int y = 0; y < h; y++) {
            seedFlood(grid, reachable, queue, 0, y);
            seedFlood(grid, reachable, queue, w - 1, y);
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                if (reachable[nx][ny]) continue;
                if (!grid.isWalkable(nx, ny)) continue;
                reachable[nx][ny] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!grid.isWalkable(x, y)) continue;
                if (reachable[x][y]) continue;
                grid.setWalkable(x, y, false);
                grid.setWallHp(x, y, WALL_HP_FORTIFIED);
            }
        }
    }

    private static void seedFlood(NavigationGrid grid, boolean[][] reachable,
                                   Deque<int[]> queue, int x, int y) {
        if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) return;
        if (reachable[x][y]) return;
        reachable[x][y] = true;
        queue.add(new int[]{x, y});
    }
}
