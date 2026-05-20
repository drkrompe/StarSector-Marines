package com.dillon.starsectormarines.battle.mapgen.bsp;

import com.dillon.starsectormarines.battle.DefensePost;
import com.dillon.starsectormarines.battle.DefensePostKind;
import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.TileManifest;
import com.dillon.starsectormarines.battle.TurretKind;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.mapgen.BiomeKind;
import com.dillon.starsectormarines.battle.mapgen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Stamps manned turret emplacements (defense posts) into conquest maps. Runs
 * after BSP fill but before {@link FortressWallStamper} so the wall pass can
 * still demolish anything caught under its sweep; the stamper's own footprint
 * validation keeps it away from buildings, water, and existing posts.
 *
 * <p>Per-biome budgets along the traversal axis:
 * <ul>
 *   <li>{@link BiomeKind#BEACH} — 1-2 {@link DefensePostKind#LIGHT} posts (single
 *       turret in a 4-cell vent ring, militia squad)</li>
 *   <li>{@link BiomeKind#PORT} — 2-3 {@link DefensePostKind#MEDIUM} posts (single
 *       turret in an 8-cell sandbag embankment, mostly militia + a few regulars)</li>
 *   <li>{@link BiomeKind#FORTRESS_DISTRICT} kill zone — 3-5
 *       {@link DefensePostKind#LARGE} posts (2 turrets in an extended embankment
 *       line, mixed regulars). Placed in the kill-zone buffer between the
 *       biome's attacker-facing edge and the fortress wall.</li>
 * </ul>
 *
 * <p>Each post stamps:
 * <ul>
 *   <li>Ring cells: non-walkable + {@link com.dillon.starsectormarines.battle.nav.NavigationGrid#setSeeThrough SEE_THROUGH}
 *       + embankment/vent doodads from urban-tileset-2. The cells grant cover
 *       to the turret in the center via the standard wall-adjacency cover bake
 *       ({@code !isWalkable(neighbor) ? 1 : 0}) while letting LoS and projectile
 *       raycasts pass through unchanged.</li>
 *   <li>Turret cells: walkable + {@link GroundKind#STONE} ground pad. Cover
 *       is recomputed post-stamp so the cardinal embankment neighbors register.
 *       The {@link com.dillon.starsectormarines.battle.MapTurret} unit spawned
 *       by {@link com.dillon.starsectormarines.battle.BattleSetup} occupies the
 *       cell; unit-stacking prevents other units from sharing it.</li>
 *   <li>{@link TacticalNode.Kind#GUARDPOST} tactical node at the post center,
 *       carrying the tier's garrison size and priority. The defender allocator's
 *       Pass 1 drops a squad on the node; the squad's {@code GUARDPOST_PATROL}
 *       role keeps it within {@link DefensePostKind#patrolRadius} of the post
 *       until every turret is destroyed.</li>
 * </ul>
 */
public final class DefensePostStamper {

    /** Per-biome stamp budgets — rolled inside {@link #stamp} as MIN + rng.nextInt(MAX - MIN + 1). */
    private static final int BEACH_LIGHT_MIN     = 1, BEACH_LIGHT_MAX     = 2;
    private static final int PORT_MEDIUM_MIN     = 2, PORT_MEDIUM_MAX     = 3;
    private static final int FORTRESS_LARGE_MIN  = 3, FORTRESS_LARGE_MAX  = 5;

    /** Minimum center-to-center distance between any two placed posts. Stops clumping. */
    private static final int POST_MIN_SEPARATION = 14;
    /** Anchor offset from the attacker-facing edge of FORTRESS_DISTRICT — 1-cell biome-edge gap + 1-cell footprint margin. Cells right at the biome boundary would half-spill into PORT. */
    private static final int FORTRESS_EDGE_BUFFER_NEAR = 2;
    /** Anchor max-offset from the attacker-facing biome edge — keeps posts in the kill-zone strip ahead of the wall (the wall sits ~12 cells in via {@code FortressWallStamper.SETBACK_CELLS}; post footprint reaches 1 cell past anchor). Cells inside the wall fail walkability validation anyway, but bounding the search keeps placement attempts efficient. */
    private static final int FORTRESS_EDGE_BUFFER_FAR = 10;
    /** Cells to look outward when sliding off an unsuitable anchor. */
    private static final int ANCHOR_SLIDE_RADIUS = 6;
    /** Attempts per post before giving up — large enough that tight maps still place SOMETHING per budget. */
    private static final int PLACEMENT_ATTEMPTS_PER_POST = 80;

    private DefensePostStamper() {}

    /**
     * Roll per-biome counts, place posts, mutate {@code grid} / {@code topology}
     * to stamp each ring + turret cell, append doodads, emit
     * {@link TacticalNode}s, and append the {@link DefensePost} records.
     *
     * <p>No-op if {@code biomeMap} is null (legacy non-conquest path).
     */
    public static void stamp(NavigationGrid grid, CellTopology topology,
                             TraversalAxis axis, BiomeMap biomeMap,
                             List<Doodad> doodads, List<TacticalNode> tactical,
                             List<DefensePost> defensePosts, Random rng) {
        if (biomeMap == null) return;
        int w = grid.getWidth();
        int h = grid.getHeight();

        int beachCount    = BEACH_LIGHT_MIN    + rng.nextInt(BEACH_LIGHT_MAX    - BEACH_LIGHT_MIN    + 1);
        int portCount     = PORT_MEDIUM_MIN    + rng.nextInt(PORT_MEDIUM_MAX    - PORT_MEDIUM_MIN    + 1);
        int fortressCount = FORTRESS_LARGE_MIN + rng.nextInt(FORTRESS_LARGE_MAX - FORTRESS_LARGE_MIN + 1);

        placePosts(grid, topology, biomeMap, axis, doodads, tactical, defensePosts, rng,
                BiomeKind.BEACH,             DefensePostKind.LIGHT,  beachCount,    w, h);
        placePosts(grid, topology, biomeMap, axis, doodads, tactical, defensePosts, rng,
                BiomeKind.PORT,              DefensePostKind.MEDIUM, portCount,     w, h);
        placePosts(grid, topology, biomeMap, axis, doodads, tactical, defensePosts, rng,
                BiomeKind.FORTRESS_DISTRICT, DefensePostKind.LARGE,  fortressCount, w, h);
    }

    /**
     * Place {@code count} posts of {@code tier} inside {@code biome}. Picks
     * random anchors in the biome's bounding box, validates each footprint,
     * stamps the ones that fit. Posts that fail validation after every attempt
     * silently drop — better a few missing posts than a hung generator.
     */
    private static void placePosts(NavigationGrid grid, CellTopology topology, BiomeMap biomeMap,
                                   TraversalAxis axis, List<Doodad> doodads, List<TacticalNode> tactical,
                                   List<DefensePost> defensePosts, Random rng,
                                   BiomeKind biome, DefensePostKind tier, int count,
                                   int w, int h) {
        int[] bbox = biomeBbox(biomeMap, biome, w, h);
        if (bbox == null) return;
        int bLeft = bbox[0], bTop = bbox[1], bRight = bbox[2], bBot = bbox[3];

        // Fortress kill zone — clamp anchors to the attacker-side strip of the
        // biome, between the biome edge and the fortress wall.
        if (biome == BiomeKind.FORTRESS_DISTRICT) {
            if (axis == TraversalAxis.SOUTH_TO_NORTH) {
                int killZoneTop = Math.min(bBot, bTop + FORTRESS_EDGE_BUFFER_FAR);
                bTop = bTop + FORTRESS_EDGE_BUFFER_NEAR;
                bBot = killZoneTop;
            } else {
                int killZoneRight = Math.min(bRight, bLeft + FORTRESS_EDGE_BUFFER_FAR);
                bLeft = bLeft + FORTRESS_EDGE_BUFFER_NEAR;
                bRight = killZoneRight;
            }
        }
        int minSpanX = (tier == DefensePostKind.LARGE) ? 5 : 3;
        if (bRight - bLeft < minSpanX) return;
        if (bBot - bTop < 3) return;

        int placed = 0;
        for (int attempt = 0; attempt < count * PLACEMENT_ATTEMPTS_PER_POST && placed < count; attempt++) {
            int cx = bLeft + rng.nextInt(bRight - bLeft + 1);
            int cy = bTop  + rng.nextInt(bBot  - bTop  + 1);
            if (biomeMap.biomeAt(cx, cy) != biome) continue;
            if (!hasValidFootprint(grid, topology, cx, cy, tier)) {
                int[] slid = slideToValid(grid, topology, biomeMap, biome, cx, cy, tier);
                if (slid == null) continue;
                cx = slid[0];
                cy = slid[1];
            }
            if (tooCloseToExistingPost(defensePosts, cx, cy)) continue;
            DefensePost post = stampPost(grid, topology, doodads, tier, cx, cy, rng);
            defensePosts.add(post);
            tactical.add(emitGuardpostNode(tier, post));
            placed++;
        }
    }

    /** Bounding box of every cell tagged with {@code biome}. Null if the biome has no cells. */
    private static int[] biomeBbox(BiomeMap biomeMap, BiomeKind biome, int w, int h) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        int top = Integer.MAX_VALUE, bot = Integer.MIN_VALUE;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (biomeMap.biomeAt(x, y) != biome) continue;
                if (x < lo)  lo  = x;
                if (x > hi)  hi  = x;
                if (y < top) top = y;
                if (y > bot) bot = y;
            }
        }
        if (lo == Integer.MAX_VALUE) return null;
        return new int[]{lo, top, hi, bot};
    }

    /**
     * True if the post's full footprint (3×3 for LIGHT/MEDIUM, 5×3 for LARGE)
     * lies on outdoor walkable ground with no buildings, walls, water, or
     * doorways intersecting. Doorway exclusion is critical — sealing a building
     * entrance under a defense post would orphan the interior.
     */
    private static boolean hasValidFootprint(NavigationGrid grid, CellTopology topology,
                                             int cx, int cy, DefensePostKind tier) {
        int halfX = (tier == DefensePostKind.LARGE) ? 2 : 1;
        int halfY = 1;
        for (int dy = -halfY; dy <= halfY; dy++) {
            for (int dx = -halfX; dx <= halfX; dx++) {
                int x = cx + dx;
                int y = cy + dy;
                if (!grid.inBounds(x, y)) return false;
                if (!grid.isWalkable(x, y)) return false;
                if (grid.isDoorway(x, y)) return false;
                if (topology.isWall(x, y)) return false;
                if (topology.isVehicle(x, y)) return false;
                if (!isStampableGround(topology.getGroundKind(x, y))) return false;
            }
        }
        return true;
    }

    /** True for outdoor ground kinds the stamper accepts. INDOOR/WATER/TILE are off-limits. */
    private static boolean isStampableGround(GroundKind g) {
        switch (g) {
            case STREET:
            case COURTYARD:
            case GRASS:
            case DIRT:
            case STONE:
            case SAND:
            case SIDEWALK:
            case BRICK:
                return true;
            default:
                return false;
        }
    }

    /**
     * Spiral search outward from {@code (cx, cy)} up to {@link #ANCHOR_SLIDE_RADIUS}
     * for a cell whose footprint validates. Returns the first valid center, or
     * null if none found in the search radius.
     */
    private static int[] slideToValid(NavigationGrid grid, CellTopology topology,
                                      BiomeMap biomeMap, BiomeKind biome,
                                      int cx, int cy, DefensePostKind tier) {
        for (int r = 1; r <= ANCHOR_SLIDE_RADIUS; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int nx = cx + dx;
                    int ny = cy + dy;
                    if (!grid.inBounds(nx, ny)) continue;
                    if (biomeMap.biomeAt(nx, ny) != biome) continue;
                    if (hasValidFootprint(grid, topology, nx, ny, tier)) return new int[]{nx, ny};
                }
            }
        }
        return null;
    }

    /** True if any placed post anchor is within {@link #POST_MIN_SEPARATION} of {@code (cx, cy)}. Manhattan-squared compare. */
    private static boolean tooCloseToExistingPost(List<DefensePost> posts, int cx, int cy) {
        int minSepSq = POST_MIN_SEPARATION * POST_MIN_SEPARATION;
        for (DefensePost p : posts) {
            int dx = p.anchorX - cx;
            int dy = p.anchorY - cy;
            if (dx * dx + dy * dy < minSepSq) return true;
        }
        return false;
    }

    /**
     * Dispatch to the per-tier stamper. Each returns a {@link DefensePost}
     * record with the anchor + turret specs for the battle setup to consume.
     */
    private static DefensePost stampPost(NavigationGrid grid, CellTopology topology,
                                         List<Doodad> doodads, DefensePostKind tier,
                                         int cx, int cy, Random rng) {
        switch (tier) {
            case LIGHT:  return stampLight(grid, topology, doodads, cx, cy);
            case MEDIUM: return stampMedium(grid, topology, doodads, cx, cy);
            case LARGE:  return stampLarge(grid, topology, doodads, cx, cy);
        }
        throw new IllegalStateException("Unhandled tier " + tier);
    }

    /**
     * LIGHT post: single turret centered at {@code (cx, cy)} with 4 cardinal
     * vent doodads (N/S/E/W). Corners stay open ground — reads as an industrial
     * stack rather than a fortified bunker.
     */
    private static DefensePost stampLight(NavigationGrid grid, CellTopology topology,
                                          List<Doodad> doodads, int cx, int cy) {
        stampRingCell(grid, topology, doodads, cx, cy - 1, TileManifest.LIGHT_POST_VENT);
        stampRingCell(grid, topology, doodads, cx, cy + 1, TileManifest.LIGHT_POST_VENT);
        stampRingCell(grid, topology, doodads, cx - 1, cy, TileManifest.LIGHT_POST_VENT);
        stampRingCell(grid, topology, doodads, cx + 1, cy, TileManifest.LIGHT_POST_VENT);
        stampTurretCenter(grid, topology, cx, cy);

        List<DefensePost.TurretSpec> turrets = new ArrayList<>(1);
        turrets.add(new DefensePost.TurretSpec(TurretKind.VULCAN, cx, cy));
        return new DefensePost(DefensePostKind.LIGHT, cx, cy, turrets);
    }

    /**
     * MEDIUM post: single turret centered at {@code (cx, cy)} with the full
     * 8-cell sandbag embankment ring (urban-2 cols 3-5 rows 0-2). Each ring
     * cell pulls its directional art from {@link TileManifest#turretEmbankment}
     * so the embankment caps face outward.
     */
    private static DefensePost stampMedium(NavigationGrid grid, CellTopology topology,
                                           List<Doodad> doodads, int cx, int cy) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                stampRingCell(grid, topology, doodads, cx + dx, cy + dy,
                        TileManifest.turretEmbankment(dx, dy));
            }
        }
        stampTurretCenter(grid, topology, cx, cy);

        List<DefensePost.TurretSpec> turrets = new ArrayList<>(1);
        turrets.add(new DefensePost.TurretSpec(TurretKind.ARBALEST, cx, cy));
        return new DefensePost(DefensePostKind.MEDIUM, cx, cy, turrets);
    }

    /**
     * LARGE post: 5×3 footprint with TWO turrets at {@code (cx-1, cy)} and
     * {@code (cx+1, cy)}, connected by a shared sandbag embankment. Inner top
     * + bottom cells repeat the N/S edge art so the long axis reads as a
     * continuous sandbag line; corners + outer caps frame the ends.
     *
     * <pre>
     *   NW  N  N  N  NE
     *   W   T1 .  T2 E
     *   SW  S  S  S  SE
     * </pre>
     *
     * The middle cell {@code (cx, cy)} stays walkable open ground — gives the
     * squad a slot to stand between the two guns.
     */
    private static DefensePost stampLarge(NavigationGrid grid, CellTopology topology,
                                          List<Doodad> doodads, int cx, int cy) {
        // North edge (relY=+1): cols dx in -2..2; cap art at the corners.
        stampRingCell(grid, topology, doodads, cx - 2, cy + 1, TileManifest.turretEmbankment(-1, 1));
        stampRingCell(grid, topology, doodads, cx - 1, cy + 1, TileManifest.turretEmbankment( 0, 1));
        stampRingCell(grid, topology, doodads, cx,     cy + 1, TileManifest.turretEmbankment( 0, 1));
        stampRingCell(grid, topology, doodads, cx + 1, cy + 1, TileManifest.turretEmbankment( 0, 1));
        stampRingCell(grid, topology, doodads, cx + 2, cy + 1, TileManifest.turretEmbankment( 1, 1));
        // South edge (relY=-1): same dx span; south-facing caps.
        stampRingCell(grid, topology, doodads, cx - 2, cy - 1, TileManifest.turretEmbankment(-1, -1));
        stampRingCell(grid, topology, doodads, cx - 1, cy - 1, TileManifest.turretEmbankment( 0, -1));
        stampRingCell(grid, topology, doodads, cx,     cy - 1, TileManifest.turretEmbankment( 0, -1));
        stampRingCell(grid, topology, doodads, cx + 1, cy - 1, TileManifest.turretEmbankment( 0, -1));
        stampRingCell(grid, topology, doodads, cx + 2, cy - 1, TileManifest.turretEmbankment( 1, -1));
        // West + east end caps (relY=0).
        stampRingCell(grid, topology, doodads, cx - 2, cy,     TileManifest.turretEmbankment(-1, 0));
        stampRingCell(grid, topology, doodads, cx + 2, cy,     TileManifest.turretEmbankment( 1, 0));

        // Two turret pads + a sealed middle cell between them. The middle stays
        // non-walkable so it can't form an unreachable walkable island between
        // the two non-walkable turret pads and the N/S embankment cells —
        // reads as a "weapon platform" filler, visually contiguous with the
        // turret pads on either side.
        stampTurretCenter(grid, topology, cx - 1, cy);
        stampTurretCenter(grid, topology, cx + 1, cy);
        sealInnerCell(grid, topology, cx, cy);

        List<DefensePost.TurretSpec> turrets = new ArrayList<>(2);
        turrets.add(new DefensePost.TurretSpec(TurretKind.HEPHAESTUS, cx - 1, cy));
        turrets.add(new DefensePost.TurretSpec(TurretKind.HEPHAESTUS, cx + 1, cy));
        return new DefensePost(DefensePostKind.LARGE, cx, cy, turrets);
    }

    /**
     * Stamp one ring cell: non-walkable, SEE_THROUGH, with the embankment/vent
     * doodad pinned on top. Marks the doodad as {@link Doodad#COVER_HEAVY} —
     * matches the +1 facing cover the wall-adjacency bake produces, and lets
     * the planner pick adjacent cells preferentially when scoring fire
     * positions.
     */
    private static void stampRingCell(NavigationGrid grid, CellTopology topology,
                                      List<Doodad> doodads, int x, int y,
                                      TileManifest.TileFrame tile) {
        if (!grid.inBounds(x, y)) return;
        grid.setWalkable(x, y, false);
        grid.setSeeThrough(x, y, true);
        // Ring cells are NOT topology.WALL — the wall renderer skips them; the
        // doodad pass paints the embankment art instead.
        doodads.add(new Doodad(x, y, tile, /*fromRoadSheet=*/true, Doodad.COVER_HEAVY));
        grid.recomputeCoverAt(x, y);
        // Refresh neighbors so their facing-cover picks up this cell.
        grid.recomputeCoverAt(x + 1, y);
        grid.recomputeCoverAt(x - 1, y);
        grid.recomputeCoverAt(x, y + 1);
        grid.recomputeCoverAt(x, y - 1);
    }

    /**
     * Stamp the turret center cell. Non-walkable + STONE pad — matches the
     * existing {@link com.dillon.starsectormarines.battle.MapTurret} stamp
     * pattern from {@code BattleSetup.stampOneTurret}, preserves the map's
     * single-component walkability invariant (a walkable cell surrounded by
     * non-walkable ring cells would form an unreachable island), and the
     * MapTurret unit spawned by the battle setup occupies it as its anchor.
     *
     * <p>Cover is baked manually rather than via {@link NavigationGrid#recomputeCoverAt}
     * because that method early-returns for non-walkable cells. We mirror its
     * neighbor-walkability logic directly so the turret gets +1 facing cover
     * from each non-walkable cardinal neighbor — for LIGHT/MEDIUM posts that's
     * the full 4-wall surround (total cover 4); for LARGE turrets that's the
     * 3 embankment neighbors (the middle walkable cell between the two turrets
     * gives the inward-facing direction 0 cover).
     */
    private static void stampTurretCenter(NavigationGrid grid, CellTopology topology,
                                          int cellX, int cellY) {
        if (!grid.inBounds(cellX, cellY)) return;
        grid.setWalkable(cellX, cellY, false);
        grid.setSeeThrough(cellX, cellY, false);
        topology.setGroundKind(cellX, cellY, GroundKind.STONE);
        grid.setCoverAtFacing(cellX, cellY, NavigationGrid.FACING_N, coverFromNeighbor(grid, cellX,     cellY - 1));
        grid.setCoverAtFacing(cellX, cellY, NavigationGrid.FACING_E, coverFromNeighbor(grid, cellX + 1, cellY    ));
        grid.setCoverAtFacing(cellX, cellY, NavigationGrid.FACING_S, coverFromNeighbor(grid, cellX,     cellY + 1));
        grid.setCoverAtFacing(cellX, cellY, NavigationGrid.FACING_W, coverFromNeighbor(grid, cellX - 1, cellY    ));
    }

    /** 1 if the neighbor at {@code (x, y)} is in-bounds and non-walkable (wall/embankment/vehicle), 0 otherwise. */
    private static int coverFromNeighbor(NavigationGrid grid, int x, int y) {
        if (!grid.inBounds(x, y)) return 0;
        return grid.isWalkable(x, y) ? 0 : 1;
    }

    /**
     * Seal the LARGE-post middle cell — non-walkable STONE pad with no doodad
     * and no cover bake (it's not a turret, just a filler). Prevents the
     * middle from forming an unreachable walkable island, completes the
     * 5×3 footprint as one contiguous non-walkable mass.
     */
    private static void sealInnerCell(NavigationGrid grid, CellTopology topology, int cellX, int cellY) {
        if (!grid.inBounds(cellX, cellY)) return;
        grid.setWalkable(cellX, cellY, false);
        grid.setSeeThrough(cellX, cellY, true);
        topology.setGroundKind(cellX, cellY, GroundKind.STONE);
    }

    /**
     * Emit one {@link TacticalNode.Kind#GUARDPOST} node for a placed post.
     * Anchor + bbox cover the post's footprint so the defender-allocator BFS
     * spawns the squad within the embankment perimeter (or at adjacent cells
     * if the perimeter is fully ring + turret).
     */
    private static TacticalNode emitGuardpostNode(DefensePostKind tier, DefensePost post) {
        int halfX = (tier == DefensePostKind.LARGE) ? 2 : 1;
        int halfY = 1;
        return new TacticalNode(TacticalNode.Kind.GUARDPOST,
                post.anchorX, post.anchorY,
                post.anchorX - halfX, post.anchorY - halfY,
                post.anchorX + halfX, post.anchorY + halfY,
                Faction.DEFENDER, tier.priorityScore, tier.garrisonSize);
    }
}
