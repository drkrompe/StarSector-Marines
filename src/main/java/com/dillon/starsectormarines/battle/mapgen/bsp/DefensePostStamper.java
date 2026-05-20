package com.dillon.starsectormarines.battle.mapgen.bsp;

import com.dillon.starsectormarines.battle.DefensePost;
import com.dillon.starsectormarines.battle.DefensePostKind;
import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.PointOfInterest;
import com.dillon.starsectormarines.battle.TileManifest;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.mapgen.BiomeKind;
import com.dillon.starsectormarines.battle.mapgen.PlacementGuards;
import com.dillon.starsectormarines.battle.mapgen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;

import java.util.ArrayList;
import java.util.Collections;
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
 *   <li>{@link BiomeKind#FORTRESS_DISTRICT} rear — 1-2
 *       {@link DefensePostKind#ARTILLERY} posts (2 LOCUST rocket-battery turrets
 *       in a 5×3 bow-out embankment). Placed BEHIND the fortress wall (2-10
 *       cells past the wall, inside the kremlin proper) so the long-range
 *       salvos arc over the wall into the kill zone and beyond.</li>
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
 *       The {@link MapTurret} unit spawned
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
    private static final int BEACH_LIGHT_MIN         = 1, BEACH_LIGHT_MAX         = 2;
    private static final int PORT_MEDIUM_MIN         = 2, PORT_MEDIUM_MAX         = 3;
    private static final int FORTRESS_LARGE_MIN      = 3, FORTRESS_LARGE_MAX      = 5;
    /** Artillery batteries are rare (1-2 per battle) — a single battery already controls a wide arc with LOCUST's 42-cell range, and two on the same map is enough to make the whole approach feel suppressed. */
    private static final int FORTRESS_ARTILLERY_MIN  = 1, FORTRESS_ARTILLERY_MAX  = 2;

    /** Minimum center-to-center distance between any two placed posts. Stops clumping. */
    private static final int POST_MIN_SEPARATION = 14;
    /** Anchor offset from the attacker-facing edge of FORTRESS_DISTRICT — 1-cell biome-edge gap + 1-cell footprint margin. Cells right at the biome boundary would half-spill into PORT. */
    private static final int FORTRESS_EDGE_BUFFER_NEAR = 2;
    /** Anchor max-offset from the attacker-facing biome edge — keeps posts in the kill-zone strip ahead of the wall (the wall sits ~12 cells in via {@code FortressWallStamper.SETBACK_CELLS}; post footprint reaches 1 cell past anchor). Cells inside the wall fail walkability validation anyway, but bounding the search keeps placement attempts efficient. */
    private static final int FORTRESS_EDGE_BUFFER_FAR = 10;
    /** Artillery rear-band: minimum depth from the attacker-facing edge of FORTRESS_DISTRICT. The wall sits at depth ~12; this offset puts the battery 2 cells PAST the wall, inside the kremlin proper. */
    private static final int FORTRESS_ARTILLERY_DEPTH_NEAR = 14;
    /** Artillery rear-band: maximum depth from the attacker-facing edge. Caps placement at ~10 cells past the wall — deep enough to feel like rear-area artillery, but bounded so the search doesn't drift into the far edge of the fortress (which on small maps can be only a few cells deeper). */
    private static final int FORTRESS_ARTILLERY_DEPTH_FAR  = 22;
    /** Cells to look outward when sliding off an unsuitable anchor. */
    private static final int ANCHOR_SLIDE_RADIUS = 6;
    /** Attempts per post before giving up — large enough that tight maps still place SOMETHING per budget. */
    private static final int PLACEMENT_ATTEMPTS_PER_POST = 80;

    /** Non-conquest tier-count rolls. Smaller and more varied than the per-biome rolls — the map has no kill zone, no port, no beach, so the layered "tier per biome" structure doesn't apply. Layered counts instead: a few LIGHT vent rings, a handful of MEDIUM sandbag embankments, occasionally one LARGE multi-turret set-piece. */
    private static final int NONCONQUEST_LIGHT_MIN  = 1, NONCONQUEST_LIGHT_MAX  = 2;
    private static final int NONCONQUEST_MEDIUM_MIN = 1, NONCONQUEST_MEDIUM_MAX = 2;
    private static final int NONCONQUEST_LARGE_MIN  = 0, NONCONQUEST_LARGE_MAX  = 1;
    /** Optional drone-hub stamp on non-conquest maps — at most one. Drones won't appear yet (spawn logic is a follow-up commit), so the count is intentionally low until the rest of the feature lands. */
    private static final int NONCONQUEST_DRONE_HUB_MIN = 0, NONCONQUEST_DRONE_HUB_MAX = 1;
    /** Margin from the grid edges for non-conquest placement — posts whose ring would hug the wall read poorly + risk being cut off by the implicit map-edge wall. */
    private static final int NONCONQUEST_EDGE_MARGIN = 3;

    /** Conquest CITY-biome drone-hub count — 1-2 hubs scattered through the civilian core, reading as the city's air-defense network. Kept low while the drone-spawn logic is still pending; can scale up once the feature is fully wired. */
    private static final int CITY_DRONE_HUB_MIN = 1, CITY_DRONE_HUB_MAX = 2;

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

        int beachCount     = BEACH_LIGHT_MIN        + rng.nextInt(BEACH_LIGHT_MAX        - BEACH_LIGHT_MIN        + 1);
        int portCount      = PORT_MEDIUM_MIN        + rng.nextInt(PORT_MEDIUM_MAX        - PORT_MEDIUM_MIN        + 1);
        int fortressCount  = FORTRESS_LARGE_MIN     + rng.nextInt(FORTRESS_LARGE_MAX     - FORTRESS_LARGE_MIN     + 1);
        int artilleryCount = FORTRESS_ARTILLERY_MIN + rng.nextInt(FORTRESS_ARTILLERY_MAX - FORTRESS_ARTILLERY_MIN + 1);
        int cityDroneHubCount = CITY_DRONE_HUB_MIN  + rng.nextInt(CITY_DRONE_HUB_MAX  - CITY_DRONE_HUB_MIN  + 1);

        placePosts(grid, topology, biomeMap, axis, doodads, tactical, defensePosts, rng,
                BiomeKind.BEACH,             DefensePostKind.LIGHT,     beachCount,     w, h);
        placePosts(grid, topology, biomeMap, axis, doodads, tactical, defensePosts, rng,
                BiomeKind.PORT,              DefensePostKind.MEDIUM,    portCount,      w, h);
        placePosts(grid, topology, biomeMap, axis, doodads, tactical, defensePosts, rng,
                BiomeKind.FORTRESS_DISTRICT, DefensePostKind.LARGE,     fortressCount,  w, h);
        // Artillery shares the FORTRESS_DISTRICT biome but lives in a deeper
        // band — placePosts() reads the tier to pick the rear clamp.
        placePosts(grid, topology, biomeMap, axis, doodads, tactical, defensePosts, rng,
                BiomeKind.FORTRESS_DISTRICT, DefensePostKind.ARTILLERY, artilleryCount, w, h);
        // Drone hubs in the civilian core — reads as the city's air-defense
        // network. CITY has no special depth clamp (unlike FORTRESS) so the
        // bbox of every CITY cell is the placement rect.
        placePosts(grid, topology, biomeMap, axis, doodads, tactical, defensePosts, rng,
                BiomeKind.CITY,              DefensePostKind.DRONE_HUB, cityDroneHubCount, w, h);
    }

    /**
     * Non-conquest entry — replaces the legacy random-cell turret scatter that
     * BattleSetup used to apply to Sabotage / Assault / Raid / Extraction maps.
     * Stamps a layered defense in the defender half of the map using the same
     * embankment vocabulary as the conquest path (LIGHT vent rings, MEDIUM
     * sandbag embankments, occasional LARGE multi-turret set-pieces), with
     * POI-aware anchor seeding so posts cluster around the buildings they're
     * defending instead of landing on random street tiles.
     *
     * <p><b>Unmanned</b>: no {@link TacticalNode.Kind#GUARDPOST} nodes are
     * emitted, so the existing defender allocator's balance (DefenderRoster
     * pool size tuned for missions without dedicated guard squads) stays
     * intact. The turret unit spawned by
     * {@code BattleSetup.spawnDefensePostTurrets} still fires autonomously,
     * and any defender patrol that happens to walk through the embankment
     * picks up the standard wall-adjacency cover bonus from the ring cells.
     *
     * <p>No ARTILLERY tier — there's no fortress wall to lob over, and a
     * long-range rocket battery deep in a non-conquest map plays badly
     * against a 12-unit Sabotage roster.
     */
    public static void stampNonConquest(NavigationGrid grid, CellTopology topology,
                                        List<PointOfInterest> pointsOfInterest,
                                        List<Doodad> doodads, List<DefensePost> defensePosts,
                                        Random rng) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        // Defender half mirrors the legacy stampTurrets choice (defender =
        // right half, marines spawn on the left). Pull the rect in by the
        // edge-margin so embankments don't kiss the implicit map-edge wall.
        int rectLeft  = Math.max(NONCONQUEST_EDGE_MARGIN, w / 2);
        int rectTop   = NONCONQUEST_EDGE_MARGIN;
        int rectRight = w - 1 - NONCONQUEST_EDGE_MARGIN;
        int rectBot   = h - 1 - NONCONQUEST_EDGE_MARGIN;

        int lightCount    = NONCONQUEST_LIGHT_MIN     + rng.nextInt(NONCONQUEST_LIGHT_MAX     - NONCONQUEST_LIGHT_MIN     + 1);
        int mediumCount   = NONCONQUEST_MEDIUM_MIN    + rng.nextInt(NONCONQUEST_MEDIUM_MAX    - NONCONQUEST_MEDIUM_MIN    + 1);
        int largeCount    = NONCONQUEST_LARGE_MIN     + rng.nextInt(NONCONQUEST_LARGE_MAX     - NONCONQUEST_LARGE_MIN     + 1);
        int droneHubCount = NONCONQUEST_DRONE_HUB_MIN + rng.nextInt(NONCONQUEST_DRONE_HUB_MAX - NONCONQUEST_DRONE_HUB_MIN + 1);

        // Per-tier POI seeds — high-value POIs (lab/comms/depot) anchor the
        // bigger emplacements, residential POIs anchor the smaller ones. The
        // slide-to-valid pass inside placePostsInRect handles the case where
        // the POI center itself is inside a building (slides outward to the
        // nearest valid outdoor anchor).
        List<int[]> largeSeeds  = poiSeeds(pointsOfInterest, rectLeft, /*highValueOnly=*/true,  rng);
        List<int[]> mediumSeeds = poiSeeds(pointsOfInterest, rectLeft, /*highValueOnly=*/true,  rng);
        List<int[]> lightSeeds  = poiSeeds(pointsOfInterest, rectLeft, /*highValueOnly=*/false, rng);

        placePostsInRect(grid, topology, /*biomeMap*/ null, /*biome*/ null,
                doodads, /*tactical*/ null, defensePosts, rng,
                DefensePostKind.LARGE, largeCount, largeSeeds,
                rectLeft, rectTop, rectRight, rectBot);
        placePostsInRect(grid, topology, null, null,
                doodads, null, defensePosts, rng,
                DefensePostKind.MEDIUM, mediumCount, mediumSeeds,
                rectLeft, rectTop, rectRight, rectBot);
        placePostsInRect(grid, topology, null, null,
                doodads, null, defensePosts, rng,
                DefensePostKind.LIGHT, lightCount, lightSeeds,
                rectLeft, rectTop, rectRight, rectBot);
        // Drone hub — reuses the high-value-POI seed pool so the hub clusters
        // around the things worth defending from the air rather than landing
        // in an empty street tile.
        placePostsInRect(grid, topology, null, null,
                doodads, null, defensePosts, rng,
                DefensePostKind.DRONE_HUB, droneHubCount, mediumSeeds,
                rectLeft, rectTop, rectRight, rectBot);
    }

    /**
     * Build a shuffled list of POI-derived candidate anchor cells for the
     * non-conquest placer. Filters to POIs in the defender half (centerX >=
     * {@code rectLeft}); when {@code highValueOnly} is true, residential POIs
     * are dropped (saving them for LIGHT-tier seeding). Picks the POI's
     * exterior anchor cell — the canonical "stand here to interact with this
     * building" cell that sits just outside a doorway — as the seed, with a
     * small random nudge so two consecutive posts seeded off neighboring POIs
     * don't snap to identical positions.
     */
    private static List<int[]> poiSeeds(List<PointOfInterest> all, int rectLeft,
                                        boolean highValueOnly, Random rng) {
        if (all == null || all.isEmpty()) return Collections.emptyList();
        List<int[]> out = new ArrayList<>();
        for (PointOfInterest poi : all) {
            if (poi.centerX() < rectLeft) continue;
            if (highValueOnly && poi.kind == PointOfInterest.Kind.RESIDENTIAL) continue;
            // Small ±2 nudge so the seed doesn't always land on the same
            // exterior-anchor cell when the POI is reused across tiers.
            int sx = poi.anchorCellX + rng.nextInt(5) - 2;
            int sy = poi.anchorCellY + rng.nextInt(5) - 2;
            out.add(new int[]{sx, sy});
        }
        Collections.shuffle(out, rng);
        return out;
    }

    /**
     * Place {@code count} posts of {@code tier} inside {@code biome}. Derives
     * the spawn rect from the biome's bounding box (and the fortress kill-zone
     * / rear-band clamps for FORTRESS_DISTRICT) and delegates the actual
     * placement loop to {@link #placePostsInRect}.
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
        // biome, between the biome edge and the fortress wall (LARGE) OR to
        // the rear strip behind the wall, inside the kremlin proper (ARTILLERY).
        if (biome == BiomeKind.FORTRESS_DISTRICT) {
            boolean rear = tier == DefensePostKind.ARTILLERY;
            int nearBuf = rear ? FORTRESS_ARTILLERY_DEPTH_NEAR : FORTRESS_EDGE_BUFFER_NEAR;
            int farBuf  = rear ? FORTRESS_ARTILLERY_DEPTH_FAR  : FORTRESS_EDGE_BUFFER_FAR;
            if (axis == TraversalAxis.SOUTH_TO_NORTH) {
                int bandBot = Math.min(bBot, bTop + farBuf);
                bTop = bTop + nearBuf;
                bBot = bandBot;
            } else {
                int bandRight = Math.min(bRight, bLeft + farBuf);
                bLeft = bLeft + nearBuf;
                bRight = bandRight;
            }
        }
        placePostsInRect(grid, topology, biomeMap, biome,
                doodads, tactical, defensePosts, rng,
                tier, count, /*seeds*/ null,
                bLeft, bTop, bRight, bBot);
    }

    /**
     * Biome-agnostic core placement loop. Picks anchors from {@code seeds}
     * first (in order, sliding to valid where the seed isn't quite right),
     * then falls back to uniform-random picks inside the {@code (bLeft,bTop)
     * .. (bRight,bBot)} rect. Each accepted anchor stamps its tier-specific
     * embankment, appends a {@link DefensePost}, and — when
     * {@code tactical != null} — emits a {@link TacticalNode.Kind#GUARDPOST}
     * for the defender allocator to garrison.
     *
     * <p>Pass {@code biomeMap = null} and {@code biome = null} to skip the
     * biome match check entirely (non-conquest path: there's no biome layer
     * to constrain against). Pass {@code tactical = null} to skip GUARDPOST
     * emission entirely (non-conquest path keeps posts unmanned to preserve
     * the legacy defender-balance — turrets still fire autonomously, the
     * embankment still grants cover to any defender that happens to pass).
     *
     * <p>Posts that fail validation after every attempt silently drop — better
     * a few missing posts than a hung generator.
     */
    private static void placePostsInRect(NavigationGrid grid, CellTopology topology,
                                         BiomeMap biomeMap, BiomeKind biome,
                                         List<Doodad> doodads, List<TacticalNode> tactical,
                                         List<DefensePost> defensePosts, Random rng,
                                         DefensePostKind tier, int count, List<int[]> seeds,
                                         int bLeft, int bTop, int bRight, int bBot) {
        if (count <= 0) return;
        // Minimum rect span every LARGE/ARTILLERY shape needs — 5×3 or 3×5
        // bbox. LIGHT/MEDIUM fit in 3×3.
        int minSpanLong = (tier == DefensePostKind.LARGE || tier == DefensePostKind.ARTILLERY) ? 5 : 3;
        if (bRight - bLeft < minSpanLong) return;
        if (bBot - bTop < minSpanLong) return;

        int placed = 0;
        int seedIdx = 0;
        int seedCount = (seeds != null) ? seeds.size() : 0;
        // Total attempt budget — seeds get a free turn each, then we fall
        // back to random picks for the rest of the budget.
        int budget = seedCount + count * PLACEMENT_ATTEMPTS_PER_POST;
        for (int attempt = 0; attempt < budget && placed < count; attempt++) {
            // Per-attempt shape pick — varies the silhouette across placements
            // so a row of LARGE posts reads as distinct emplacements. LIGHT and
            // MEDIUM ignore this; their stampers use a fixed ring.
            DefensePostShape shape = (tier == DefensePostKind.LARGE)
                    ? DefensePostShape.pickForLarge(rng) : null;
            int halfX = shapeHalfX(tier, shape);
            int halfY = shapeHalfY(tier, shape);

            int cx, cy;
            if (seedIdx < seedCount) {
                int[] seed = seeds.get(seedIdx++);
                cx = seed[0];
                cy = seed[1];
            } else {
                cx = bLeft + rng.nextInt(bRight - bLeft + 1);
                cy = bTop  + rng.nextInt(bBot  - bTop  + 1);
            }
            if (biomeMap != null && biomeMap.biomeAt(cx, cy) != biome) continue;
            if (!hasValidFootprint(grid, topology, cx, cy, halfX, halfY)) {
                int[] slid = slideToValid(grid, topology, biomeMap, biome, cx, cy, halfX, halfY);
                if (slid == null) continue;
                cx = slid[0];
                cy = slid[1];
            }
            if (tooCloseToExistingPost(defensePosts, cx, cy)) continue;
            // Final gate — would stamping this footprint partition the walkable
            // graph? Catches the case where the post sits between an existing
            // non-walkable mass (BSP outdoor wall, building, fortress wall) and
            // open ground, sealing off a thin strip the footprint check on its
            // own can't see.
            if (PlacementGuards.wouldPartitionWalkable(
                    grid, cx - halfX, cy - halfY, halfX * 2 + 1, halfY * 2 + 1)) continue;
            DefensePost post = stampPost(grid, topology, doodads, tier, shape, cx, cy, rng);
            defensePosts.add(post);
            // Tiers with a zero garrison (DRONE_HUB) defend themselves via
            // their own spawned units, so we skip the GUARDPOST emission that
            // would otherwise pull an infantry squad off the defender roster.
            if (tactical != null && tier.garrisonSize > 0) {
                tactical.add(emitGuardpostNode(tier, shape, post));
            }
            placed++;
        }
    }

    /** Footprint half-extent on X for the given tier+shape combo. ARTILLERY shares LARGE's 5×3 LINE_H footprint (halfX=2). */
    private static int shapeHalfX(DefensePostKind tier, DefensePostShape shape) {
        if (shape != null) return shape.halfX;
        if (tier == DefensePostKind.LARGE || tier == DefensePostKind.ARTILLERY) return 2;
        return 1;
    }

    /** Footprint half-extent on Y for the given tier+shape combo. */
    private static int shapeHalfY(DefensePostKind tier, DefensePostShape shape) {
        if (shape != null) return shape.halfY;
        return 1;
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
     * entrance under a defense post would orphan the interior, and blocking the
     * cell <em>directly outside</em> the doorway has the same effect (the
     * through-cell is the only walkable egress from the threshold). Hence the
     * cardinal-neighbor doorway check: rejects footprints that would stamp on a
     * doorway's perpendicular cell even though that cell itself isn't flagged
     * DOORWAY.
     */
    private static boolean hasValidFootprint(NavigationGrid grid, CellTopology topology,
                                             int cx, int cy, int halfX, int halfY) {
        for (int dy = -halfY; dy <= halfY; dy++) {
            for (int dx = -halfX; dx <= halfX; dx++) {
                int x = cx + dx;
                int y = cy + dy;
                if (!grid.inBounds(x, y)) return false;
                if (!grid.isWalkable(x, y)) return false;
                if (PlacementGuards.touchesDoorway(grid, x, y)) return false;
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
     * null if none found in the search radius. Pass {@code biomeMap == null} to
     * skip the biome match (non-conquest path).
     */
    private static int[] slideToValid(NavigationGrid grid, CellTopology topology,
                                      BiomeMap biomeMap, BiomeKind biome,
                                      int cx, int cy, int halfX, int halfY) {
        for (int r = 1; r <= ANCHOR_SLIDE_RADIUS; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int nx = cx + dx;
                    int ny = cy + dy;
                    if (!grid.inBounds(nx, ny)) continue;
                    if (biomeMap != null && biomeMap.biomeAt(nx, ny) != biome) continue;
                    if (hasValidFootprint(grid, topology, nx, ny, halfX, halfY)) return new int[]{nx, ny};
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
     * {@code shape} is non-null for LARGE only and selects the per-placement
     * silhouette variant; LIGHT/MEDIUM ignore it.
     */
    private static DefensePost stampPost(NavigationGrid grid, CellTopology topology,
                                         List<Doodad> doodads, DefensePostKind tier,
                                         DefensePostShape shape,
                                         int cx, int cy, Random rng) {
        switch (tier) {
            case LIGHT:     return stampLight(grid, topology, doodads, cx, cy);
            case MEDIUM:    return stampMedium(grid, topology, doodads, cx, cy);
            case LARGE:     return stampLarge(grid, topology, doodads, shape, cx, cy);
            case ARTILLERY: return stampArtillery(grid, topology, doodads, cx, cy);
            case DRONE_HUB: return stampDroneHub(grid, topology, doodads, cx, cy);
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
     * LARGE post dispatch. Each shape uses a different embankment silhouette
     * and turret arrangement; the kill zone reads as a varied line of distinct
     * positions rather than a row of clones. See {@link DefensePostShape}.
     */
    private static DefensePost stampLarge(NavigationGrid grid, CellTopology topology,
                                          List<Doodad> doodads, DefensePostShape shape,
                                          int cx, int cy) {
        switch (shape) {
            case LINE_H:             return stampLargeLineH(grid, topology, doodads, cx, cy);
            case LINE_V:             return stampLargeLineV(grid, topology, doodads, cx, cy);
            case WEDGE:              return stampLargeWedge(grid, topology, doodads, cx, cy);
            case TRAPEZOID:          return stampLargeTrapezoid(grid, topology, doodads, cx, cy);
            case TRIANGLE_FORMATION: return stampLargeTriangleFormation(grid, topology, doodads, cx, cy);
        }
        throw new IllegalStateException("Unhandled shape " + shape);
    }

    /**
     * LINE_H: 5×3 horizontal embankment with two turrets at {@code (cx±1, cy)}.
     * <pre>
     *   NW  N  N  N  NE
     *   W   T1 .  T2 E
     *   SW  S  S  S  SE
     * </pre>
     */
    private static DefensePost stampLargeLineH(NavigationGrid grid, CellTopology topology,
                                               List<Doodad> doodads, int cx, int cy) {
        stampRingCell(grid, topology, doodads, cx - 2, cy + 1, TileManifest.turretEmbankment(-1,  1));
        stampRingCell(grid, topology, doodads, cx - 1, cy + 1, TileManifest.turretEmbankment( 0,  1));
        stampRingCell(grid, topology, doodads, cx,     cy + 1, TileManifest.turretEmbankment( 0,  1));
        stampRingCell(grid, topology, doodads, cx + 1, cy + 1, TileManifest.turretEmbankment( 0,  1));
        stampRingCell(grid, topology, doodads, cx + 2, cy + 1, TileManifest.turretEmbankment( 1,  1));
        stampRingCell(grid, topology, doodads, cx - 2, cy - 1, TileManifest.turretEmbankment(-1, -1));
        stampRingCell(grid, topology, doodads, cx - 1, cy - 1, TileManifest.turretEmbankment( 0, -1));
        stampRingCell(grid, topology, doodads, cx,     cy - 1, TileManifest.turretEmbankment( 0, -1));
        stampRingCell(grid, topology, doodads, cx + 1, cy - 1, TileManifest.turretEmbankment( 0, -1));
        stampRingCell(grid, topology, doodads, cx + 2, cy - 1, TileManifest.turretEmbankment( 1, -1));
        stampRingCell(grid, topology, doodads, cx - 2, cy,     TileManifest.turretEmbankment(-1,  0));
        stampRingCell(grid, topology, doodads, cx + 2, cy,     TileManifest.turretEmbankment( 1,  0));
        stampTurretCenter(grid, topology, cx - 1, cy);
        stampTurretCenter(grid, topology, cx + 1, cy);
        sealInnerCell(grid, topology, cx, cy);

        List<DefensePost.TurretSpec> turrets = new ArrayList<>(2);
        turrets.add(new DefensePost.TurretSpec(TurretKind.HEPHAESTUS, cx - 1, cy));
        turrets.add(new DefensePost.TurretSpec(TurretKind.HEPHAESTUS, cx + 1, cy));
        return new DefensePost(DefensePostKind.LARGE, cx, cy, turrets);
    }

    /**
     * LINE_V: 3×5 vertical embankment with two turrets at {@code (cx, cy±1)}.
     * Rotated mirror of LINE_H.
     * <pre>
     *   NW  N  NE
     *   W   T1 E
     *   W   .  E
     *   W   T2 E
     *   SW  S  SE
     * </pre>
     */
    private static DefensePost stampLargeLineV(NavigationGrid grid, CellTopology topology,
                                               List<Doodad> doodads, int cx, int cy) {
        stampRingCell(grid, topology, doodads, cx - 1, cy + 2, TileManifest.turretEmbankment(-1,  1));
        stampRingCell(grid, topology, doodads, cx,     cy + 2, TileManifest.turretEmbankment( 0,  1));
        stampRingCell(grid, topology, doodads, cx + 1, cy + 2, TileManifest.turretEmbankment( 1,  1));
        stampRingCell(grid, topology, doodads, cx - 1, cy + 1, TileManifest.turretEmbankment(-1,  0));
        stampRingCell(grid, topology, doodads, cx + 1, cy + 1, TileManifest.turretEmbankment( 1,  0));
        stampRingCell(grid, topology, doodads, cx - 1, cy,     TileManifest.turretEmbankment(-1,  0));
        stampRingCell(grid, topology, doodads, cx + 1, cy,     TileManifest.turretEmbankment( 1,  0));
        stampRingCell(grid, topology, doodads, cx - 1, cy - 1, TileManifest.turretEmbankment(-1,  0));
        stampRingCell(grid, topology, doodads, cx + 1, cy - 1, TileManifest.turretEmbankment( 1,  0));
        stampRingCell(grid, topology, doodads, cx - 1, cy - 2, TileManifest.turretEmbankment(-1, -1));
        stampRingCell(grid, topology, doodads, cx,     cy - 2, TileManifest.turretEmbankment( 0, -1));
        stampRingCell(grid, topology, doodads, cx + 1, cy - 2, TileManifest.turretEmbankment( 1, -1));
        stampTurretCenter(grid, topology, cx, cy + 1);
        stampTurretCenter(grid, topology, cx, cy - 1);
        sealInnerCell(grid, topology, cx, cy);

        List<DefensePost.TurretSpec> turrets = new ArrayList<>(2);
        turrets.add(new DefensePost.TurretSpec(TurretKind.HEPHAESTUS, cx, cy + 1));
        turrets.add(new DefensePost.TurretSpec(TurretKind.HEPHAESTUS, cx, cy - 1));
        return new DefensePost(DefensePostKind.LARGE, cx, cy, turrets);
    }

    /**
     * WEDGE: 5×3 chevron with a 1-cell apex at {@code (cx, cy-1)} and a wide
     * back row. Single turret at the center. Uses the chunkier
     * {@link TileManifest#turretBowOut bow-out} art so the apex reads as a
     * heavier earthwork protruding into the kill zone.
     * <pre>
     *   NW  N  N  N  NE
     *   .   W  T  E  .
     *   .   .  S  .  .
     * </pre>
     */
    private static DefensePost stampLargeWedge(NavigationGrid grid, CellTopology topology,
                                               List<Doodad> doodads, int cx, int cy) {
        stampRingCell(grid, topology, doodads, cx - 2, cy + 1, TileManifest.turretBowOut(-1,  1));
        stampRingCell(grid, topology, doodads, cx - 1, cy + 1, TileManifest.turretBowOut( 0,  1));
        stampRingCell(grid, topology, doodads, cx,     cy + 1, TileManifest.turretBowOut( 0,  1));
        stampRingCell(grid, topology, doodads, cx + 1, cy + 1, TileManifest.turretBowOut( 0,  1));
        stampRingCell(grid, topology, doodads, cx + 2, cy + 1, TileManifest.turretBowOut( 1,  1));
        stampRingCell(grid, topology, doodads, cx - 1, cy,     TileManifest.turretBowOut(-1,  0));
        stampRingCell(grid, topology, doodads, cx + 1, cy,     TileManifest.turretBowOut( 1,  0));
        stampRingCell(grid, topology, doodads, cx,     cy - 1, TileManifest.turretBowOut( 0, -1));
        stampTurretCenter(grid, topology, cx, cy);

        List<DefensePost.TurretSpec> turrets = new ArrayList<>(1);
        turrets.add(new DefensePost.TurretSpec(TurretKind.HEPHAESTUS, cx, cy));
        return new DefensePost(DefensePostKind.LARGE, cx, cy, turrets);
    }

    /**
     * TRAPEZOID: 5×3 with a 5-cell back row, 5-cell middle, and a narrowed
     * 3-cell south row. Two turrets E/W of center. Uses the same bow-out art
     * as WEDGE so the two "protruding silhouette" shapes share their heavier
     * read.
     * <pre>
     *   NW  N  N  N  NE
     *   W   T1 .  T2 E
     *   .   SW S  SE .
     * </pre>
     */
    private static DefensePost stampLargeTrapezoid(NavigationGrid grid, CellTopology topology,
                                                   List<Doodad> doodads, int cx, int cy) {
        stampRingCell(grid, topology, doodads, cx - 2, cy + 1, TileManifest.turretBowOut(-1,  1));
        stampRingCell(grid, topology, doodads, cx - 1, cy + 1, TileManifest.turretBowOut( 0,  1));
        stampRingCell(grid, topology, doodads, cx,     cy + 1, TileManifest.turretBowOut( 0,  1));
        stampRingCell(grid, topology, doodads, cx + 1, cy + 1, TileManifest.turretBowOut( 0,  1));
        stampRingCell(grid, topology, doodads, cx + 2, cy + 1, TileManifest.turretBowOut( 1,  1));
        stampRingCell(grid, topology, doodads, cx - 2, cy,     TileManifest.turretBowOut(-1,  0));
        stampRingCell(grid, topology, doodads, cx + 2, cy,     TileManifest.turretBowOut( 1,  0));
        stampRingCell(grid, topology, doodads, cx - 1, cy - 1, TileManifest.turretBowOut(-1, -1));
        stampRingCell(grid, topology, doodads, cx,     cy - 1, TileManifest.turretBowOut( 0, -1));
        stampRingCell(grid, topology, doodads, cx + 1, cy - 1, TileManifest.turretBowOut( 1, -1));
        stampTurretCenter(grid, topology, cx - 1, cy);
        stampTurretCenter(grid, topology, cx + 1, cy);
        sealInnerCell(grid, topology, cx, cy);

        List<DefensePost.TurretSpec> turrets = new ArrayList<>(2);
        turrets.add(new DefensePost.TurretSpec(TurretKind.HEPHAESTUS, cx - 1, cy));
        turrets.add(new DefensePost.TurretSpec(TurretKind.HEPHAESTUS, cx + 1, cy));
        return new DefensePost(DefensePostKind.LARGE, cx, cy, turrets);
    }

    /**
     * TRIANGLE_FORMATION: 3 turrets in a spearhead with apex south. Reads as
     * a coordinated battery — more firepower per post than the line shapes,
     * fully enclosed by wall + sealed stone platform. Uses bow-out art to
     * match the other "protruding" silhouettes.
     * <pre>
     *   NW  T1  N   T3  NE
     *   W   S   S   S   E
     *   SS  SW  T2  SE  SS
     * </pre>
     * The middle row and bbox corner cells are sealed (non-walkable STONE
     * pad, no doodad) rather than left open — keeps the 3 turrets inside one
     * non-walkable mass so no isolated walkable pocket forms between them.
     */
    private static DefensePost stampLargeTriangleFormation(NavigationGrid grid, CellTopology topology,
                                                           List<Doodad> doodads, int cx, int cy) {
        stampRingCell(grid, topology, doodads, cx - 2, cy + 1, TileManifest.turretBowOut(-1,  1));
        stampRingCell(grid, topology, doodads, cx,     cy + 1, TileManifest.turretBowOut( 0,  1));
        stampRingCell(grid, topology, doodads, cx + 2, cy + 1, TileManifest.turretBowOut( 1,  1));
        stampRingCell(grid, topology, doodads, cx - 2, cy,     TileManifest.turretBowOut(-1,  0));
        stampRingCell(grid, topology, doodads, cx + 2, cy,     TileManifest.turretBowOut( 1,  0));
        stampRingCell(grid, topology, doodads, cx - 1, cy - 1, TileManifest.turretBowOut(-1, -1));
        stampRingCell(grid, topology, doodads, cx + 1, cy - 1, TileManifest.turretBowOut( 1, -1));
        stampTurretCenter(grid, topology, cx - 1, cy + 1);
        stampTurretCenter(grid, topology, cx + 1, cy + 1);
        stampTurretCenter(grid, topology, cx,     cy - 1);
        sealInnerCell(grid, topology, cx - 1, cy);
        sealInnerCell(grid, topology, cx,     cy);
        sealInnerCell(grid, topology, cx + 1, cy);
        sealInnerCell(grid, topology, cx - 2, cy - 1);
        sealInnerCell(grid, topology, cx + 2, cy - 1);

        List<DefensePost.TurretSpec> turrets = new ArrayList<>(3);
        turrets.add(new DefensePost.TurretSpec(TurretKind.HEPHAESTUS, cx - 1, cy + 1));
        turrets.add(new DefensePost.TurretSpec(TurretKind.HEPHAESTUS, cx + 1, cy + 1));
        turrets.add(new DefensePost.TurretSpec(TurretKind.HEPHAESTUS, cx,     cy - 1));
        return new DefensePost(DefensePostKind.LARGE, cx, cy, turrets);
    }

    /**
     * ARTILLERY post: 5×3 bow-out embankment with two LOCUST rocket batteries
     * at {@code (cx±1, cy)}. Footprint geometry mirrors {@link #stampLargeLineH}
     * — same 10-cell ring, same two turret pads, same sealed inner cell — but
     * uses the chunkier {@link TileManifest#turretBowOut} art instead of the
     * thinner sandbag embankment, and stamps {@link TurretKind#LOCUST} instead
     * of {@code HEPHAESTUS}. Reads as a fortified rocket battery deep in the
     * kremlin interior, lobbing salvos over the wall.
     * <pre>
     *   NW  N  N  N  NE
     *   W   T1 .  T2 E
     *   SW  S  S  S  SE
     * </pre>
     */
    private static DefensePost stampArtillery(NavigationGrid grid, CellTopology topology,
                                              List<Doodad> doodads, int cx, int cy) {
        stampRingCell(grid, topology, doodads, cx - 2, cy + 1, TileManifest.turretBowOut(-1,  1));
        stampRingCell(grid, topology, doodads, cx - 1, cy + 1, TileManifest.turretBowOut( 0,  1));
        stampRingCell(grid, topology, doodads, cx,     cy + 1, TileManifest.turretBowOut( 0,  1));
        stampRingCell(grid, topology, doodads, cx + 1, cy + 1, TileManifest.turretBowOut( 0,  1));
        stampRingCell(grid, topology, doodads, cx + 2, cy + 1, TileManifest.turretBowOut( 1,  1));
        stampRingCell(grid, topology, doodads, cx - 2, cy - 1, TileManifest.turretBowOut(-1, -1));
        stampRingCell(grid, topology, doodads, cx - 1, cy - 1, TileManifest.turretBowOut( 0, -1));
        stampRingCell(grid, topology, doodads, cx,     cy - 1, TileManifest.turretBowOut( 0, -1));
        stampRingCell(grid, topology, doodads, cx + 1, cy - 1, TileManifest.turretBowOut( 0, -1));
        stampRingCell(grid, topology, doodads, cx + 2, cy - 1, TileManifest.turretBowOut( 1, -1));
        stampRingCell(grid, topology, doodads, cx - 2, cy,     TileManifest.turretBowOut(-1,  0));
        stampRingCell(grid, topology, doodads, cx + 2, cy,     TileManifest.turretBowOut( 1,  0));
        stampTurretCenter(grid, topology, cx - 1, cy);
        stampTurretCenter(grid, topology, cx + 1, cy);
        sealInnerCell(grid, topology, cx, cy);

        List<DefensePost.TurretSpec> turrets = new ArrayList<>(2);
        turrets.add(new DefensePost.TurretSpec(TurretKind.LOCUST, cx - 1, cy));
        turrets.add(new DefensePost.TurretSpec(TurretKind.LOCUST, cx + 1, cy));
        return new DefensePost(DefensePostKind.ARTILLERY, cx, cy, turrets);
    }

    /**
     * DRONE_HUB post: 3×3 sandbag embankment ring around a sealed STONE launch
     * pad at {@code (cx, cy)}. Geometry mirrors {@link #stampMedium} — same
     * 8-cell ring, same outward-facing embankment art — but the center cell is
     * sealed (non-walkable STONE, no doodad) instead of hosting a turret. A
     * follow-up commit will spawn a {@code DroneHubUnit} at the sealed center
     * cell to drive periodic drone launches; the empty
     * {@link DefensePost#turrets} list keeps the existing
     * {@code BattleSetup.spawnDefensePostTurrets} loop a no-op for this tier.
     * <pre>
     *   NW  N  NE
     *   W   .  E
     *   SW  S  SE
     * </pre>
     */
    private static DefensePost stampDroneHub(NavigationGrid grid, CellTopology topology,
                                             List<Doodad> doodads, int cx, int cy) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                stampRingCell(grid, topology, doodads, cx + dx, cy + dy,
                        TileManifest.turretEmbankment(dx, dy));
            }
        }
        sealInnerCell(grid, topology, cx, cy);
        return new DefensePost(DefensePostKind.DRONE_HUB, cx, cy, new ArrayList<>(0));
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
     * existing {@link MapTurret} stamp
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
    private static TacticalNode emitGuardpostNode(DefensePostKind tier, DefensePostShape shape, DefensePost post) {
        int halfX = shapeHalfX(tier, shape);
        int halfY = shapeHalfY(tier, shape);
        return new TacticalNode(TacticalNode.Kind.GUARDPOST,
                post.anchorX, post.anchorY,
                post.anchorX - halfX, post.anchorY - halfY,
                post.anchorX + halfX, post.anchorY + halfY,
                Faction.DEFENDER, tier.priorityScore, tier.garrisonSize);
    }
}
