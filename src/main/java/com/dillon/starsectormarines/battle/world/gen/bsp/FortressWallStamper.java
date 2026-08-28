package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.TacticalNode.StandPosition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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
 * Wall is an axis-aligned rectangle whose nominal position is inset
 * {@link #SETBACK_CELLS} into the fortress biome. The actual envelope expands
 * toward the map edge and attacker as needed to wrap the generated fortress
 * compound with {@link #KEEP_COMPOUND_CLEARANCE} cells of outer ward. This
 * makes the keep's claimed footprint authoritative instead of letting an
 * unrelated fixed biome inset strand it outside the wall. Only three sides
 * are drawn — the back side abuts the map edge, which is impassable already. For
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
 *   <li><b>Forward bunkers</b> — 2–4 free-standing, attacker-facing fighting
 *       positions in the kill-zone buffer. Each has two firing windows, two
 *       authored infantry stand cells, a center heavy-turret mount, and an
 *       open rear entrance.</li>
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
 * {@link GenContext}. Conquest-recipe-only — {@link #run} requires
 * {@link BspKeys#BIOME_MAP} and throws if it is unbound.
 */
public final class FortressWallStamper implements GenStage {

    /** Pull the wall this many cells back from the fortress biome's bounding box. Larger = bigger kill-zone buffer; smaller = wall hugs the biome edge. */
    private static final int SETBACK_CELLS = 12;
    /** Minimum courtyard depth between the fortress compound bbox and every closed side of the outer wall. The compound already owns its own perimeter, so this gap creates a distinct outer ward between two defensive layers. */
    private static final int KEEP_COMPOUND_CLEARANCE = 6;
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
    /** Forward bunker frontage and depth. Five cells fit two windows around a center turret; three cells retain a compact open-backed footprint. */
    private static final int BUNKER_FRONTAGE = 5;
    private static final int BUNKER_DEPTH = 3;
    private static final int BUNKER_HALF_FRONTAGE = BUNKER_FRONTAGE / 2;
    private static final int BUNKER_HALF_DEPTH = BUNKER_DEPTH / 2;
    private static final int BUNKER_GARRISON_SIZE = 2;
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
     * Stamp the wall + towers + gates + forward bunkers. Conquest-recipe-only:
     * requires {@link BspKeys#BIOME_MAP} and throws if invoked without it
     * (recipe membership keeps it off the legacy path). Still a clean no-op when
     * the fortress biome's bounding box is too small to fit a meaningful wall
     * (degenerate biome layouts on very small maps) — that's real geometry, not
     * a missing overlay.
     *
     * <p>Mutates {@code ctx.doodads} — strips any entries that fall inside a
     * demolished building footprint, so debris doesn't float in mid-air on
     * cleared parade ground.
     */
    @Override
    public void run(GenContext ctx) {
        BiomeMap biomeMap = ctx.get(BspKeys.BIOME_MAP);
        if (biomeMap == null) {
            throw new IllegalStateException(
                    "FortressWallStamper requires BIOME_MAP — conquest recipe only");
        }
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        TraversalAxis axis = ctx.get(BspKeys.AXIS);
        Random rng = ctx.rng;
        int w = grid.getWidth();
        int h = grid.getHeight();
        int[] bbox = fortressBbox(biomeMap, w, h);
        if (bbox == null) return;
        List<Compound> compounds = ctx.get(BspKeys.COMPOUNDS);
        Compound keepCompound = findKeepCompound(compounds);
        boolean[][] compoundExclusion = buildCompoundExclusion(compounds, w, h);
        markWard(compoundExclusion, ctx.get(BspKeys.FORTRESS_WARD), w, h);
        boolean[][] skip = mergeExclusions(ctx.get(BspKeys.ROAD_RESERVATION), compoundExclusion, w, h);
        boolean[][] wallMask = new boolean[w][h];
        if (axis == TraversalAxis.SOUTH_TO_NORTH) {
            stampSouthToNorth(grid, topology, bbox, keepCompound,
                    wallMask, skip, ctx.tactical, w, h, rng);
        } else {
            stampWestToEast(grid, topology, bbox, keepCompound,
                    wallMask, skip, ctx.tactical, w, h, rng);
        }
        demolishIntersectedBuildings(grid, topology, ctx.doodads, wallMask, w, h);
        sealOrphanedPockets(grid, topology, ctx.tactical, w, h);
        dropBunkersWithoutWindows(grid, ctx.tactical, axis);
    }

    /**
     * Retire any forward bunker whose firing slits did not survive the seal.
     *
     * <p>Sealing removes barriers beside ground nothing can reach, which is
     * right — a window onto a stranded pocket is a window onto nowhere. What it
     * cannot know is that some of those barriers are the slits of a bunker
     * published moments earlier, and a bunker without them is two fighting
     * cells staring at a wall.
     *
     * <p>Dropping the node is the standing answer rather than a repair: a
     * bunker is omitted when it cannot be built usable, and re-opening the
     * ground in front of one to justify it would be the wall deciding where the
     * map's dead ends are.
     */
    private static void dropBunkersWithoutWindows(NavigationGrid grid,
                                                  List<TacticalNode> tactical,
                                                  TraversalAxis axis) {
        Direction slit = axis == TraversalAxis.SOUTH_TO_NORTH
                ? Direction.S : Direction.W;
        tactical.removeIf(node -> {
            if (node.kind != TacticalNode.Kind.FORWARD_BUNKER) return false;
            for (StandPosition stand : node.standPositions()) {
                if (grid.getEdgeBarrier(stand.x(), stand.y(), slit) == null) return true;
            }
            return false;
        });
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

    /**
     * Exclude the packed fortress ward the same way a compound is excluded.
     *
     * <p>The ward was laid out before this stage precisely so the wall would
     * have something to enclose; a wall run through the middle of it would
     * demolish the sheds and magazines it exists to protect. Absent on a map
     * with no fortress band, where this is a no-op.
     */
    private static void markWard(boolean[][] exclusion, int[] ward, int w, int h) {
        if (ward == null) return;
        for (int x = Math.max(0, ward[0]); x <= Math.min(w - 1, ward[2]); x++) {
            for (int y = Math.max(0, ward[1]); y <= Math.min(h - 1, ward[3]); y++) {
                exclusion[x][y] = true;
            }
        }
    }

    /** The one conquest fortress base is the inner keep compound the outer ward must enclose. */
    private static Compound findKeepCompound(List<Compound> compounds) {
        if (compounds == null) return null;
        for (Compound compound : compounds) {
            if (compound.kind == BlockKind.MILITARY_BASE
                    && compound.biome == BiomeKind.FORTRESS_DISTRICT) return compound;
        }
        return null;
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
                                          int[] bbox, Compound keepCompound,
                                          boolean[][] wallMask,
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

        // The biome inset supplies the default silhouette; the generated keep
        // compound supplies the hard containment constraint. Expanding only
        // outward preserves the broad fortress scale while guaranteeing an
        // inner compound -> open ward -> outer curtain-wall sequence.
        if (keepCompound != null) {
            wLeft = Math.min(wLeft, keepCompound.left - KEEP_COMPOUND_CLEARANCE);
            wRight = Math.max(wRight, keepCompound.right + KEEP_COMPOUND_CLEARANCE);
            wBot = Math.min(wBot, keepCompound.top - KEEP_COMPOUND_CLEARANCE);
        }
        wLeft = Math.max(2, wLeft);
        wRight = Math.min(w - 3, wRight);
        wBot = Math.max(2, wBot);

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
        if (killZoneTop >= killZoneBot) {
            int bxAttempts = bunkerCount * 50;
            for (int a = 0; a < bxAttempts && bunkerCenters.size() < bunkerCount; a++) {
                int bx = wLeft + 4 + rng.nextInt(Math.max(1, span - 8));
                int by = killZoneBot + rng.nextInt(Math.max(1, killZoneTop - killZoneBot + 1));
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
                if (!hasValidBunkerSite(grid,
                        roadReservation, bx, by, TraversalAxis.SOUTH_TO_NORTH)) continue;
                List<StandPosition> standPositions = stampForwardBunker(
                        grid, topology, bx, by, TraversalAxis.SOUTH_TO_NORTH,
                        wallMask, roadReservation);
                emitForwardBunker(tactical, bx, by,
                        TraversalAxis.SOUTH_TO_NORTH, standPositions);
                bunkerCenters.add(new int[]{bx, by});
            }
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
                                        int[] bbox, Compound keepCompound,
                                        boolean[][] wallMask,
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

        if (keepCompound != null) {
            wBot = Math.min(wBot, keepCompound.top - KEEP_COMPOUND_CLEARANCE);
            wTop = Math.max(wTop, keepCompound.bottom + KEEP_COMPOUND_CLEARANCE);
            wLeft = Math.min(wLeft, keepCompound.left - KEEP_COMPOUND_CLEARANCE);
        }
        wBot = Math.max(2, wBot);
        wTop = Math.min(h - 3, wTop);
        wLeft = Math.max(2, wLeft);

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
        if (killZoneRight >= killZoneLeft) {
            int bxAttempts = bunkerCount * 50;
            for (int a = 0; a < bxAttempts && bunkerCenters.size() < bunkerCount; a++) {
                int bx = killZoneLeft + rng.nextInt(Math.max(1, killZoneRight - killZoneLeft + 1));
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
                if (!hasValidBunkerSite(grid,
                        roadReservation, bx, by, TraversalAxis.WEST_TO_EAST)) continue;
                List<StandPosition> standPositions = stampForwardBunker(
                        grid, topology, bx, by, TraversalAxis.WEST_TO_EAST,
                        wallMask, roadReservation);
                emitForwardBunker(tactical, bx, by,
                        TraversalAxis.WEST_TO_EAST, standPositions);
                bunkerCenters.add(new int[]{bx, by});
            }
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

    /** FORWARD_BUNKER node — anchor at the turret mount, with authored cells immediately behind its firing windows. */
    private static void emitForwardBunker(List<TacticalNode> tactical,
                                          int cx, int cy, TraversalAxis axis,
                                          List<StandPosition> standPositions) {
        int halfX = axis == TraversalAxis.SOUTH_TO_NORTH
                ? BUNKER_HALF_FRONTAGE : BUNKER_HALF_DEPTH;
        int halfY = axis == TraversalAxis.SOUTH_TO_NORTH
                ? BUNKER_HALF_DEPTH : BUNKER_HALF_FRONTAGE;
        tactical.add(new TacticalNode(TacticalNode.Kind.FORWARD_BUNKER,
                cx, cy, cx - halfX, cy - halfY, cx + halfX, cy + halfY,
                Faction.DEFENDER, 65, BUNKER_GARRISON_SIZE, standPositions));
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
     * Stamps a compact open-backed bunker facing the attacker. Coordinates
     * are expressed as frontage ({@code along=-2..2}) and depth
     * ({@code -1=front, 1=rear}), then rotated for the traversal axis:
     *
     * <pre>
     *   # W # W #    W = authored stand cell at a shared-edge window
     *   # . T . #    T = turret mount
     *   # . . . #    . = open rear access
     * </pre>
     */
    private static List<StandPosition> stampForwardBunker(
            NavigationGrid grid, CellTopology topology,
            int cx, int cy, TraversalAxis axis, boolean[][] wallMask,
            boolean[][] reservation) {
        List<StandPosition> standPositions = new ArrayList<>(BUNKER_GARRISON_SIZE);
        for (int depth = -BUNKER_HALF_DEPTH; depth <= BUNKER_HALF_DEPTH; depth++) {
            for (int along = -BUNKER_HALF_FRONTAGE;
                 along <= BUNKER_HALF_FRONTAGE; along++) {
                int x = cx + (axis == TraversalAxis.SOUTH_TO_NORTH ? along : depth);
                int y = cy + (axis == TraversalAxis.SOUTH_TO_NORTH ? depth : along);
                boolean frontWindow = depth == -BUNKER_HALF_DEPTH
                        && Math.abs(along) == 1;
                boolean perimeterWall = depth == -BUNKER_HALF_DEPTH
                        || Math.abs(along) == BUNKER_HALF_FRONTAGE;

                if (frontWindow) {
                    clearBunkerFloor(grid, topology, x, y);
                    Direction front = axis == TraversalAxis.SOUTH_TO_NORTH
                            ? Direction.S : Direction.W;
                    // The site gate has already proved this edge is free, so
                    // the bunker authors its own window here rather than
                    // discovering someone else's.
                    grid.placeEdgeBarrier(x, y, front,
                            SharedEdgeBarrier.Kind.WINDOW);
                    standPositions.add(new StandPosition(x, y));
                } else if (perimeterWall) {
                    paintBunkerWall(grid, topology, x, y, wallMask);
                } else if (depth == 0 && along == 0) {
                    stampBunkerTurret(grid, topology, x, y, wallMask);
                } else {
                    clearBunkerFloor(grid, topology, x, y);
                }
            }
        }
        carveBunkerRearApproach(grid, topology, cx, cy, axis, reservation);
        return standPositions;
    }

    /** Carves a three-cell apron behind the open back unless protected space already owns it. */
    private static void carveBunkerRearApproach(NavigationGrid grid, CellTopology topology,
                                                 int cx, int cy, TraversalAxis axis,
                                                 boolean[][] reservation) {
        int rearDepth = BUNKER_HALF_DEPTH + 1;
        for (int along = -1; along <= 1; along++) {
            int x = cx + (axis == TraversalAxis.SOUTH_TO_NORTH ? along : rearDepth);
            int y = cy + (axis == TraversalAxis.SOUTH_TO_NORTH ? rearDepth : along);
            if (reservation != null && reservation[x][y]) continue;
            clearBunkerFloor(grid, topology, x, y);
        }
    }

    private static void paintBunkerWall(NavigationGrid grid, CellTopology topology,
                                         int x, int y, boolean[][] wallMask) {
        paintWall(grid, topology, x, y, wallMask, null);
        topology.setFixture(x, y, false);
        topology.setVehicle(x, y, false);
        topology.setWindow(x, y, false);
        topology.setNatureOverlayIndex(x, y, -1);
        grid.setSeeThrough(x, y, false);
    }

    private static void stampBunkerTurret(NavigationGrid grid, CellTopology topology,
                                           int x, int y, boolean[][] wallMask) {
        grid.setWalkable(x, y, false);
        grid.setSeeThrough(x, y, false);
        grid.setWallHp(x, y, WALL_HP_FORTIFIED);
        topology.setGroundKind(x, y, TURRET_PAD);
        topology.setFixture(x, y, false);
        topology.setWindow(x, y, false);
        topology.setVehicle(x, y, true);
        topology.setNatureOverlayIndex(x, y, -1);
        wallMask[x][y] = true;
    }

    private static void clearBunkerFloor(NavigationGrid grid, CellTopology topology,
                                          int x, int y) {
        grid.setWalkableFloor(x, y);
        grid.setSeeThrough(x, y, false);
        grid.setWallHp(x, y, 0);
        grid.setDoorway(x, y, false);
        topology.setGroundKind(x, y, WALL_GROUND);
        topology.setWall(x, y, false);
        topology.setFixture(x, y, false);
        topology.setWindow(x, y, false);
        topology.setVehicle(x, y, false);
        topology.setNatureOverlayIndex(x, y, -1);
        topology.setBuildingKindHint(x, y, null);
        topology.setRoomPurpose(x, y, null);
    }

    /** Bunkers never punch through reserved space or seal their own rear access. */
    private static boolean hasValidBunkerSite(NavigationGrid grid,
                                              boolean[][] roadReservation,
                                              int cx, int cy,
                                              TraversalAxis axis) {
        for (int depth = -BUNKER_HALF_DEPTH; depth <= BUNKER_HALF_DEPTH; depth++) {
            for (int along = -BUNKER_HALF_FRONTAGE;
                 along <= BUNKER_HALF_FRONTAGE; along++) {
                int x = cx + (axis == TraversalAxis.SOUTH_TO_NORTH ? along : depth);
                int y = cy + (axis == TraversalAxis.SOUTH_TO_NORTH ? depth : along);
                if (!grid.inBounds(x, y)) return false;
                if (roadReservation != null && roadReservation[x][y]) return false;
            }
        }
        // A one-cell walkable halo across the front and both flanks proves
        // that each window's exterior side belongs to real circulation and
        // that the intact pane still has a route around the free-standing
        // bunker. This rejects a visually plausible stamp inside a stranded
        // one-cell pocket.
        // Both firing slits have to be cuttable, which is not a given: this
        // bunker is stamped over ground the fill already used, and a building
        // demolished under it can have left its own window on the very edge a
        // slit wants. An edge carries one authored identity, so a site whose
        // slits are already spoken for is not a site — rejecting it here is the
        // standing rule that a bunker is omitted rather than published with
        // fighting cells that do not work.
        Direction slit = axis == TraversalAxis.SOUTH_TO_NORTH
                ? Direction.S : Direction.W;
        for (int along = -1; along <= 1; along += 2) {
            int x = cx + (axis == TraversalAxis.SOUTH_TO_NORTH
                    ? along : -BUNKER_HALF_DEPTH);
            int y = cy + (axis == TraversalAxis.SOUTH_TO_NORTH
                    ? -BUNKER_HALF_DEPTH : along);
            if (!grid.inBounds(x, y)) return false;
            if (grid.getEdgeBarrier(x, y, slit) != null) return false;
            if (!grid.isSharedEdgePassable(x, y, slit)) return false;
        }

        int frontDepth = -BUNKER_HALF_DEPTH - 1;
        List<int[]> frontExits = new ArrayList<>(BUNKER_FRONTAGE);
        for (int along = -BUNKER_HALF_FRONTAGE;
             along <= BUNKER_HALF_FRONTAGE; along++) {
            int x = cx + (axis == TraversalAxis.SOUTH_TO_NORTH
                    ? along : frontDepth);
            int y = cy + (axis == TraversalAxis.SOUTH_TO_NORTH
                    ? frontDepth : along);
            if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) return false;
            frontExits.add(new int[]{x, y});
        }
        for (int depth = -BUNKER_HALF_DEPTH;
             depth <= BUNKER_HALF_DEPTH; depth++) {
            for (int flank = -1; flank <= 1; flank += 2) {
                int along = flank * (BUNKER_HALF_FRONTAGE + 1);
                int x = cx + (axis == TraversalAxis.SOUTH_TO_NORTH
                        ? along : depth);
                int y = cy + (axis == TraversalAxis.SOUTH_TO_NORTH
                        ? depth : along);
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) return false;
            }
        }
        int rearDepth = BUNKER_HALF_DEPTH + 1;
        for (int along = -1; along <= 1; along++) {
            int x = cx + (axis == TraversalAxis.SOUTH_TO_NORTH ? along : rearDepth);
            int y = cy + (axis == TraversalAxis.SOUTH_TO_NORTH ? rearDepth : along);
            if (!grid.inBounds(x, y)) return false;
            if (roadReservation != null && roadReservation[x][y]
                    && !grid.isWalkable(x, y)) return false;
        }
        int exitDepth = rearDepth + 1;
        List<int[]> exits = new ArrayList<>(3);
        for (int along = -1; along <= 1; along++) {
            int x = cx + (axis == TraversalAxis.SOUTH_TO_NORTH ? along : exitDepth);
            int y = cy + (axis == TraversalAxis.SOUTH_TO_NORTH ? exitDepth : along);
            if (grid.inBounds(x, y) && grid.isWalkable(x, y)) {
                exits.add(new int[]{x, y});
            }
        }
        // The bunker footprint itself can be the only bridge between two
        // otherwise open areas. Prove both sides remain connected after that
        // footprint becomes solid so the orphan-pocket cleanup cannot later
        // consume either side of an authored firing window.
        return exitsReachMapEdge(grid, frontExits, cx, cy, axis)
                && exitsReachMapEdge(grid, exits, cx, cy, axis);
    }

    private static boolean exitsReachMapEdge(NavigationGrid grid, List<int[]> exits,
                                              int cx, int cy, TraversalAxis axis) {
        if (exits.isEmpty()) return false;
        boolean[][] seen = new boolean[grid.getWidth()][grid.getHeight()];
        Deque<int[]> queue = new ArrayDeque<>();
        for (int[] exit : exits) {
            seen[exit[0]][exit[1]] = true;
            queue.addLast(exit);
        }
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] cell = queue.removeFirst();
            if (cell[0] == 0 || cell[0] == grid.getWidth() - 1
                    || cell[1] == 0 || cell[1] == grid.getHeight() - 1) return true;
            for (int[] direction : directions) {
                int nx = cell[0] + direction[0];
                int ny = cell[1] + direction[1];
                if (!grid.inBounds(nx, ny) || seen[nx][ny]
                        || !grid.isWalkable(nx, ny)
                        || insideBunkerFootprint(nx, ny, cx, cy, axis)) continue;
                seen[nx][ny] = true;
                queue.addLast(new int[]{nx, ny});
            }
        }
        return false;
    }

    private static boolean insideBunkerFootprint(int x, int y, int cx, int cy,
                                                  TraversalAxis axis) {
        int along = axis == TraversalAxis.SOUTH_TO_NORTH ? x - cx : y - cy;
        int depth = axis == TraversalAxis.SOUTH_TO_NORTH ? y - cy : x - cx;
        return Math.abs(along) <= BUNKER_HALF_FRONTAGE
                && Math.abs(depth) <= BUNKER_HALF_DEPTH;
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
            for (int fy = 0; fy < d.footprintCellsY; fy++) {
                for (int fx = 0; fx < d.footprintCellsX; fx++) {
                    int x = d.cellX + fx;
                    int y = d.cellY + fy;
                    if (x < 0 || x >= w || y < 0 || y >= h) continue;
                    if (toClear[x][y] || wallsCleared[x][y]) return true;
                }
            }
            return false;
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
     * Resolve every walkable region the wall cut off from the rest of the map,
     * so the finished map stays a single connected component.
     *
     * <p>Two outcomes, because sealed pockets are not all worth the same. An
     * ordinary sealed building interior is filled in solid: it was going to be
     * unusable anyway, and cutting it a new doorway would scatter unintended
     * gates through the wall. A pocket holding a <b>compound</b> is breached
     * open instead — a compound is a Conquest win condition, so walling one off
     * does not cost a room, it makes the mission unwinnable. One narrow breach
     * into a supply hub the wall happened to swallow is a far smaller price,
     * and it reads honestly: there is a way in.
     *
     * <p>Note that merely declining to seal such a pocket would not be enough.
     * An unsealed pocket nobody can walk into is exactly as uncapturable as a
     * filled one, so the connection has to actually be cut.
     */
    private static void sealOrphanedPockets(NavigationGrid grid, CellTopology topology,
                                            List<TacticalNode> tactical, int w, int h) {
        boolean[][] reachable = floodFromMapEdge(grid, w, h);

        boolean[][] visited = new boolean[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (visited[x][y] || reachable[x][y] || !grid.isWalkable(x, y)) continue;
                List<int[]> pocket = collectPocket(grid, visited, x, y, w, h);
                if (holdsCompound(pocket, tactical)) {
                    breachToReachable(grid, topology, pocket, reachable, w, h);
                }
            }
        }

        // Re-flood rather than patching reachability in place: a breach can
        // reconnect more than the pocket it was cut for, and sealing a region
        // the breach just opened would undo the repair.
        reachable = floodFromMapEdge(grid, w, h);
        removeBarriersTouchingSealedCells(grid, reachable);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!grid.isWalkable(x, y)) continue;
                if (reachable[x][y]) continue;
                grid.setWalkable(x, y, false);
                grid.setWallHp(x, y, WALL_HP_FORTIFIED);
            }
        }
    }

    /**
     * Every walkable cell reachable from the map perimeter. Seeding from all
     * four edges floods BOTH the attacker side (south rows) and the fortress
     * interior (north rows past the wall), since each abuts the map edge.
     */
    private static boolean[][] floodFromMapEdge(NavigationGrid grid, int w, int h) {
        boolean[][] reachable = new boolean[w][h];
        Deque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            seedFlood(grid, reachable, queue, x, 0);
            seedFlood(grid, reachable, queue, x, h - 1);
        }
        for (int y = 0; y < h; y++) {
            seedFlood(grid, reachable, queue, 0, y);
            seedFlood(grid, reachable, queue, w - 1, y);
        }
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (Direction direction : Direction.CARDINALS) {
                int nx = p[0] + direction.dx;
                int ny = p[1] + direction.dy;
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                if (reachable[nx][ny]) continue;
                if (!grid.isWalkable(nx, ny)) continue;
                if (!grid.isSharedEdgePassable(p[0], p[1], direction)) continue;
                reachable[nx][ny] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return reachable;
    }

    /** One maximal connected run of cut-off walkable cells, flood-filled from {@code (startX, startY)}. */
    private static List<int[]> collectPocket(NavigationGrid grid, boolean[][] visited,
                                             int startX, int startY, int w, int h) {
        List<int[]> pocket = new ArrayList<>();
        Deque<int[]> queue = new ArrayDeque<>();
        visited[startX][startY] = true;
        queue.add(new int[]{startX, startY});
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            pocket.add(p);
            for (Direction direction : Direction.CARDINALS) {
                int nx = p[0] + direction.dx;
                int ny = p[1] + direction.dy;
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                if (visited[nx][ny]) continue;
                if (!grid.isWalkable(nx, ny)) continue;
                if (!grid.isSharedEdgePassable(p[0], p[1], direction)) continue;
                visited[nx][ny] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return pocket;
    }

    /** True when a compound node's footprint covers any cell of this pocket. */
    private static boolean holdsCompound(List<int[]> pocket, List<TacticalNode> tactical) {
        if (tactical == null || tactical.isEmpty()) return false;
        for (int[] cell : pocket) {
            for (TacticalNode node : tactical) {
                if (!isCompoundKind(node.kind)) continue;
                if (cell[0] >= node.left && cell[0] <= node.right
                        && cell[1] >= node.top && cell[1] <= node.bottom) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Cut the shortest corridor from {@code pocket} to reachable ground, opening
     * every wall cell along it. Breadth-first from the whole pocket at once and
     * through walls, so the corridor crosses the thinnest structure available
     * rather than whatever happens to sit beside an arbitrary starting cell.
     *
     * <p>Opened as plain military floor, not as a gate: a gate carries a doorway
     * flag, and doorway cells belong to no zone — which is the very condition
     * that would leave the compound unable to resolve a capture room.
     */
    private static void breachToReachable(NavigationGrid grid, CellTopology topology,
                                          List<int[]> pocket, boolean[][] reachable,
                                          int w, int h) {
        int[] cameFrom = new int[w * h];
        Arrays.fill(cameFrom, UNVISITED);
        Deque<int[]> queue = new ArrayDeque<>();
        for (int[] cell : pocket) {
            cameFrom[cell[1] * w + cell[0]] = PATH_START;
            queue.add(cell);
        }
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            if (reachable[p[0]][p[1]]) {
                openBreachPath(grid, topology, cameFrom, p[0], p[1], w);
                return;
            }
            for (int[] d : CARDINALS) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                int index = ny * w + nx;
                if (cameFrom[index] != UNVISITED) continue;
                cameFrom[index] = p[1] * w + p[0];
                queue.add(new int[]{nx, ny});
            }
        }
        // Nothing reachable anywhere on the map to connect to — degenerate
        // geometry. Leave the pocket to the seal pass.
    }

    /** Walks the breach predecessors back to the pocket, opening each wall cell it crosses. */
    private static void openBreachPath(NavigationGrid grid, CellTopology topology,
                                       int[] cameFrom, int endX, int endY, int w) {
        int index = endY * w + endX;
        while (index != PATH_START) {
            int x = index % w;
            int y = index / w;
            int previous = cameFrom[index];
            if (previous >= 0) {
                int previousX = previous % w;
                int previousY = previous / w;
                Direction direction = cardinalDirection(
                        previousX - x, previousY - y);
                SharedEdgeBarrier barrier = grid.getEdgeBarrier(x, y, direction);
                if (barrier != null) {
                    grid.damageEdgeBarrier(x, y, direction, barrier.maxStructure());
                    grid.openSharedEdge(x, y, direction);
                }
            }
            if (!grid.isWalkable(x, y)) {
                grid.setWalkableFloor(x, y);
                topology.setWall(x, y, false);
                topology.setGroundKind(x, y, WALL_GROUND);
            }
            index = previous;
        }
    }

    /** Removes sparse identities that would otherwise survive beside a sealed cell. */
    private static void removeBarriersTouchingSealedCells(
            NavigationGrid grid, boolean[][] reachable) {
        List<SharedEdgeBarrier> toRemove = new ArrayList<>();
        for (SharedEdgeBarrier barrier : grid.getEdgeBarriers()) {
            int otherX = barrier.cellX() + barrier.direction().dx;
            int otherY = barrier.cellY() + barrier.direction().dy;
            if (!reachable[barrier.cellX()][barrier.cellY()]
                    || !reachable[otherX][otherY]) toRemove.add(barrier);
        }
        for (SharedEdgeBarrier barrier : toRemove) {
            grid.damageEdgeBarrier(barrier.cellX(), barrier.cellY(),
                    barrier.direction(), barrier.maxStructure());
        }
    }

    private static Direction cardinalDirection(int dx, int dy) {
        for (Direction direction : Direction.CARDINALS) {
            if (direction.dx == dx && direction.dy == dy) return direction;
        }
        throw new IllegalArgumentException("cells must be cardinal neighbors");
    }

    private static boolean isCompoundKind(TacticalNode.Kind kind) {
        return kind == TacticalNode.Kind.COMMAND_POST
                || kind == TacticalNode.Kind.BARRACKS
                || kind == TacticalNode.Kind.ARMORY;
    }

    /** Breach-search sentinels: no predecessor recorded yet, and "this cell is the pocket itself". */
    private static final int UNVISITED = -2;
    private static final int PATH_START = -1;

    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static void seedFlood(NavigationGrid grid, boolean[][] reachable,
                                   Deque<int[]> queue, int x, int y) {
        if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) return;
        if (reachable[x][y]) return;
        reachable[x][y] = true;
        queue.add(new int[]{x, y});
    }
}
