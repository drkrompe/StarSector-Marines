package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DistrictTheme;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Civilian spaceport apron. Each sufficiently large leaf contains one authored
 * 5x5 shuttle berth with a clear approach lane, a corner control office and a
 * cargo/service band along the opposite edge. Props are placed in small rows
 * and work clusters rather than uniformly scattered across the tarmac.
 */
public final class SpaceportFiller implements BlockFiller {

    private static final int PAD_HALF = 2;
    private static final int PAD_MARGIN = 1;
    private static final int PAD_MIN_SIDE = PAD_HALF * 2 + 1;

    private static final int TOWER_SIDE = 3;
    private static final int TOWER_INSET = 1;
    private static final int TOWER_MIN_LEAF = 8;

    // Existing conquest/neutral-spaceport grammar. Keeping this branch exact
    // preserves established conquest seeds; the civilian grammar is selected
    // only when a campaign-backed ordinary city reports a real spaceport.
    private static final int LEGACY_CELLS_PER_COVER_ISLAND = 42;
    private static final int LEGACY_MIN_COVER_ISLANDS = 2;
    private static final int LEGACY_MAX_COVER_ISLANDS = 8;
    private static final int LEGACY_CELLS_PER_DOODAD = 55;
    private static final int LEGACY_MAX_DOODADS = 6;
    private static final int LEGACY_TOWER_MIN_LEAF = 7;

    private static final String[] CARGO_IDS = {
            "doodad.industrial-crate-stack",
            "doodad.industrial-pallet-stack",
            "doodad.industrial-drum-cluster",
            "doodad.industrial-pipe-bundle"
    };
    private static final String[] SERVICE_IDS = {
            "doodad.industrial-generator",
            "doodad.industrial-cable-reel",
            "doodad.industrial-dumpster"
    };
    /**
     * Two-cell apron islands: a substantial freight/support prop backed by a
     * lower companion.  Each pair reads as ordinary port staging while giving
     * infantry a useful short position instead of an implausible prop wall.
     */
    private static final String[][] COVER_ISLAND_IDS = {
            {"doodad.industrial-crate-stack", "doodad.industrial-pallet-stack"},
            {"doodad.industrial-generator", "doodad.industrial-cable-reel"},
            {"doodad.industrial-dumpster", "doodad.industrial-drum-cluster"},
            {"doodad.industrial-crate-stack", "doodad.industrial-pipe-bundle"}
    };

    @Override
    public BlockKind kind() { return BlockKind.SPACEPORT_PAD; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        TargetProfile profile = ctx.get(BspKeys.MARKET_PROFILE);
        boolean civilianDistrict = ctx.get(BspKeys.AXIS) == null
                && (profile == null || profile.spaceportTier() > 0);
        if (!civilianDistrict) {
            fillLegacyApron(leaf, ctx);
            return;
        }
        paintApron(leaf, ctx.grid, ctx.topology);

        boolean longX = leaf.width() >= leaf.height();
        boolean approachLow = ctx.rng.nextBoolean();
        LandingPad.Approach approach = approachFor(longX, approachLow);
        int[] padCenter = choosePadCenter(leaf, longX, approachLow);
        LandingPad pad = LandingPad.spaceport(padCenter[0], padCenter[1], approach);

        // Tiny legacy leaves still read as marked aprons, but only publish a
        // berth when the full 5x5 exclusion footprint fits.
        if (leaf.width() >= PAD_MIN_SIDE && leaf.height() >= PAD_MIN_SIDE) {
            stampPadSurface(pad, ctx.topology);
            ctx.landingPads.add(pad);
        } else {
            ctx.topology.setGroundKind(pad.centerX, pad.centerY, GroundKind.LZ_MARKER);
        }

        int[] tower = null;
        if (leaf.width() >= TOWER_MIN_LEAF && leaf.height() >= TOWER_MIN_LEAF) {
            tower = carveControlOffice(leaf, pad, longX, approachLow, ctx);
        }
        placeServiceBand(leaf, pad, tower, longX, approachLow, ctx);
        placeCoverIslands(leaf, pad, tower, longX, approachLow, ctx);
    }

    private static void paintApron(BlockLeaf leaf, NavigationGrid grid, CellTopology topology) {
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.STRIPED);
            }
        }
    }

    private static LandingPad.Approach approachFor(boolean longX, boolean approachLow) {
        if (longX) return approachLow ? LandingPad.Approach.WEST : LandingPad.Approach.EAST;
        return approachLow ? LandingPad.Approach.SOUTH : LandingPad.Approach.NORTH;
    }

    /** Places the pad toward its approach edge, leaving the far edge for servicing. */
    private static int[] choosePadCenter(BlockLeaf leaf, boolean longX, boolean approachLow) {
        int cx = leaf.centerX();
        int cy = leaf.centerY();
        if (longX) {
            cx = approachLow
                    ? leaf.left + PAD_MARGIN + PAD_HALF
                    : leaf.right - PAD_MARGIN - PAD_HALF;
        } else {
            cy = approachLow
                    ? leaf.top + PAD_MARGIN + PAD_HALF
                    : leaf.bottom - PAD_MARGIN - PAD_HALF;
        }
        cx = Math.max(leaf.left + PAD_HALF, Math.min(leaf.right - PAD_HALF, cx));
        cy = Math.max(leaf.top + PAD_HALF, Math.min(leaf.bottom - PAD_HALF, cy));
        return new int[]{cx, cy};
    }

    private static void stampPadSurface(LandingPad pad, CellTopology topology) {
        for (int y = pad.bottom(); y <= pad.top(); y++) {
            for (int x = pad.left(); x <= pad.right(); x++) {
                // A darker paver ring makes the reserved 5x5 footprint legible;
                // the 3x3 inner touchdown area keeps the safety striping.
                boolean perimeter = x == pad.left() || x == pad.right()
                        || y == pad.bottom() || y == pad.top();
                topology.setGroundKind(x, y, perimeter ? GroundKind.BRICK : GroundKind.STRIPED);
            }
        }
        topology.setGroundKind(pad.centerX, pad.centerY, GroundKind.LZ_MARKER);
    }

    /**
     * Carves the office in the far service corner, never inside the pad or its
     * approach side. The door opens toward the apron center.
     */
    private static int[] carveControlOffice(BlockLeaf leaf, LandingPad pad,
                                            boolean longX, boolean approachLow,
                                            GenContext ctx) {
        boolean farHigh = approachLow;
        int left;
        int top;
        if (longX) {
            left = farHigh
                    ? leaf.right - TOWER_INSET - TOWER_SIDE + 1
                    : leaf.left + TOWER_INSET;
            top = ctx.rng.nextBoolean()
                    ? leaf.top + TOWER_INSET
                    : leaf.bottom - TOWER_INSET - TOWER_SIDE + 1;
        } else {
            top = farHigh
                    ? leaf.bottom - TOWER_INSET - TOWER_SIDE + 1
                    : leaf.top + TOWER_INSET;
            left = ctx.rng.nextBoolean()
                    ? leaf.left + TOWER_INSET
                    : leaf.right - TOWER_INSET - TOWER_SIDE + 1;
        }
        int right = left + TOWER_SIDE - 1;
        int bottom = top + TOWER_SIDE - 1;
        if (rectTouchesPad(left, top, right, bottom, pad, 1)) return null;

        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        for (int x = left; x <= right; x++) {
            grid.setWalkable(x, top, false);
            grid.setWalkable(x, bottom, false);
        }
        for (int y = top + 1; y < bottom; y++) {
            grid.setWalkable(left, y, false);
            grid.setWalkable(right, y, false);
        }

        int interiorX = left + 1;
        int interiorY = top + 1;
        topology.setGroundKind(interiorX, interiorY, GroundKind.TILE);
        topology.setBuildingKindHint(interiorX, interiorY, BuildingKind.COMMERCIAL);

        int westDist = Math.abs(pad.centerX - left);
        int eastDist = Math.abs(pad.centerX - right);
        int northDist = Math.abs(pad.centerY - top);
        int southDist = Math.abs(pad.centerY - bottom);
        int nearest = Math.min(Math.min(westDist, eastDist), Math.min(northDist, southDist));
        int doorX;
        int doorY;
        if (nearest == westDist) {
            doorX = left;
            doorY = top + 1;
        } else if (nearest == eastDist) {
            doorX = right;
            doorY = top + 1;
        } else if (nearest == northDist) {
            doorX = left + 1;
            doorY = top;
        } else {
            doorX = left + 1;
            doorY = bottom;
        }
        grid.setWalkableFloor(doorX, doorY);
        grid.setDoorway(doorX, doorY, true);
        grid.openAllEdges(doorX, doorY);
        topology.setGroundKind(doorX, doorY, GroundKind.TILE);

        int anchorX = doorX + Integer.signum(pad.centerX - doorX);
        int anchorY = doorY + Integer.signum(pad.centerY - doorY);
        if (!grid.inBounds(anchorX, anchorY) || !grid.isWalkable(anchorX, anchorY)) {
            anchorX = doorX;
            anchorY = doorY;
        }
        ctx.pois.add(new PointOfInterest(PointOfInterest.Kind.COMMS,
                left, top, right, bottom, anchorX, anchorY, interiorX, interiorY));
        return new int[]{left, top, right, bottom};
    }

    /** Cargo rows at the service edge plus a small maintenance cluster by the office. */
    private static void placeServiceBand(BlockLeaf leaf, LandingPad pad, int[] tower,
                                         boolean longX, boolean approachLow,
                                         GenContext ctx) {
        List<int[]> cargo = new ArrayList<>();
        if (longX) {
            int x = approachLow ? leaf.right - 1 : leaf.left + 1;
            for (int y = leaf.top + 1; y <= leaf.bottom - 1; y += 2) cargo.add(new int[]{x, y});
        } else {
            int y = approachLow ? leaf.bottom - 1 : leaf.top + 1;
            for (int x = leaf.left + 1; x <= leaf.right - 1; x += 2) cargo.add(new int[]{x, y});
        }

        int budget = Math.min(4, Math.max(2, leaf.area() / 55));
        int placed = 0;
        for (int[] cell : cargo) {
            if (placed >= budget) break;
            if (onVehicleCenterline(cell[0], cell[1], pad, longX)
                    || inApproachCorridor(cell[0], cell[1], pad)) continue;
            if (!canPlaceProp(cell[0], cell[1], pad, tower, ctx.grid)) continue;
            DoodadDef def = resolve(CARGO_IDS[(placed + ctx.rng.nextInt(CARGO_IDS.length)) % CARGO_IDS.length]);
            if (def == null) break;
            ctx.doodads.add(new Doodad(cell[0], cell[1], def));
            placed++;
        }

        if (tower != null) {
            int[][] neighbors = {
                    {tower[0] - 1, tower[1]}, {tower[2] + 1, tower[1]},
                    {tower[0] - 1, tower[3]}, {tower[2] + 1, tower[3]}
            };
            int servicePlaced = 0;
            for (int[] cell : neighbors) {
                if (servicePlaced >= 2) break;
                if (!leaf.contains(cell[0], cell[1])) continue;
                if (onVehicleCenterline(cell[0], cell[1], pad, longX)
                        || inApproachCorridor(cell[0], cell[1], pad)) continue;
                if (!canPlaceProp(cell[0], cell[1], pad, tower, ctx.grid)) continue;
                DoodadDef def = resolve(SERVICE_IDS[servicePlaced % SERVICE_IDS.length]);
                if (def == null) break;
                ctx.doodads.add(new Doodad(cell[0], cell[1], def));
                servicePlaced++;
            }
        }
    }

    /**
     * Places two separated work islands between the touchdown pad and its
     * service edge.  They flank, rather than occupy, the vehicle centerline;
     * the whole pad-width approach corridor remains empty.  The companion of
     * each island extends toward the service side, producing a two-cell cargo
     * or machinery cluster with open apron between it and the other island.
     */
    private static void placeCoverIslands(BlockLeaf leaf, LandingPad pad, int[] tower,
                                          boolean longX, boolean approachLow,
                                          GenContext ctx) {
        int serviceDx = longX ? (approachLow ? 1 : -1) : 0;
        int serviceDy = longX ? 0 : (approachLow ? 1 : -1);
        int crossDx = longX ? 0 : 1;
        int crossDy = longX ? 1 : 0;
        int stagingDistance = PAD_HALF + 2;
        int flankDistance = PAD_HALF + 1;
        int variant = ctx.rng.nextInt(COVER_ISLAND_IDS.length);

        for (int island = 0; island < 2; island++) {
            int side = island == 0 ? -1 : 1;
            int anchorX = pad.centerX + serviceDx * stagingDistance
                    + crossDx * side * flankDistance;
            int anchorY = pad.centerY + serviceDy * stagingDistance
                    + crossDy * side * flankDistance;
            String[] ids = COVER_ISLAND_IDS[(variant + island) % COVER_ISLAND_IDS.length];
            placeIslandProp(ids[0], anchorX, anchorY, leaf, pad, tower, longX, ctx);
            placeIslandProp(ids[1], anchorX + serviceDx, anchorY + serviceDy,
                    leaf, pad, tower, longX, ctx);
        }
    }

    private static void placeIslandProp(String id, int x, int y, BlockLeaf leaf,
                                        LandingPad pad, int[] tower, boolean longX,
                                        GenContext ctx) {
        // Keep the cluster off the parcel rim and away from the office so its
        // open cells remain believable working and circulation space.
        if (x <= leaf.left || x >= leaf.right || y <= leaf.top || y >= leaf.bottom) return;
        if (onVehicleCenterline(x, y, pad, longX) || inApproachCorridor(x, y, pad)) return;
        if (withinMargin(x, y, tower, 1) || doodadAt(x, y, ctx.doodads)) return;
        if (!canPlaceProp(x, y, pad, tower, ctx.grid)) return;
        DoodadDef def = resolve(id);
        if (def != null) ctx.doodads.add(new Doodad(x, y, def));
    }

    private static boolean onVehicleCenterline(int x, int y, LandingPad pad, boolean longX) {
        return longX ? y == pad.centerY : x == pad.centerX;
    }

    private static boolean inApproachCorridor(int x, int y, LandingPad pad) {
        switch (pad.approach) {
            case WEST:
                return x < pad.left() && y >= pad.bottom() && y <= pad.top();
            case EAST:
                return x > pad.right() && y >= pad.bottom() && y <= pad.top();
            case SOUTH:
                return y < pad.bottom() && x >= pad.left() && x <= pad.right();
            case NORTH:
                return y > pad.top() && x >= pad.left() && x <= pad.right();
            default:
                return false;
        }
    }

    private static boolean doodadAt(int x, int y, List<Doodad> doodads) {
        for (Doodad doodad : doodads) {
            if (doodad.occupiesCell(x, y)) return true;
        }
        return false;
    }

    private static DoodadDef resolve(String id) {
        TileRegistry registry = TileRegistry.installed();
        return registry == null ? null : registry.doodad(id);
    }

    private static boolean canPlaceProp(int x, int y, LandingPad pad, int[] tower,
                                        NavigationGrid grid) {
        if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y)) return false;
        if (pad.contains(x, y)) return false;
        return tower == null || x < tower[0] || x > tower[2] || y < tower[1] || y > tower[3];
    }

    private static boolean rectTouchesPad(int left, int top, int right, int bottom,
                                          LandingPad pad, int margin) {
        return right >= pad.left() - margin && left <= pad.right() + margin
                && bottom >= pad.bottom() - margin && top <= pad.top() + margin;
    }

    /** Original open-killing-ground grammar retained for conquest and neutral previews. */
    private static void fillLegacyApron(BlockLeaf leaf, GenContext ctx) {
        paintApron(leaf, ctx.grid, ctx.topology);
        int[] tower = null;
        if (leaf.width() >= LEGACY_TOWER_MIN_LEAF && leaf.height() >= LEGACY_TOWER_MIN_LEAF) {
            tower = carveLegacyTower(leaf, ctx);
        }
        scatterLegacyCover(leaf, tower, ctx);
        scatterLegacyDoodads(leaf, ctx);
    }

    private static int[] carveLegacyTower(BlockLeaf leaf, GenContext ctx) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        boolean west = ctx.rng.nextBoolean();
        boolean north = ctx.rng.nextBoolean();
        int left = west ? leaf.left + TOWER_INSET : leaf.right - TOWER_INSET - (TOWER_SIDE - 1);
        int top = north ? leaf.top + TOWER_INSET : leaf.bottom - TOWER_INSET - (TOWER_SIDE - 1);
        int right = left + TOWER_SIDE - 1;
        int bottom = top + TOWER_SIDE - 1;
        for (int x = left; x <= right; x++) {
            grid.setWalkable(x, top, false);
            grid.setWalkable(x, bottom, false);
        }
        for (int y = top + 1; y <= bottom - 1; y++) {
            grid.setWalkable(left, y, false);
            grid.setWalkable(right, y, false);
        }
        int interiorX = left + 1;
        int interiorY = top + 1;
        topology.setGroundKind(interiorX, interiorY, GroundKind.STRIPED);
        topology.setBuildingKindHint(interiorX, interiorY, BuildingKind.FORTIFIED);

        int doorX = west ? right : left;
        int doorY = top + 1;
        int anchorX = west ? right + 1 : left - 1;
        int anchorY = doorY;
        grid.setWalkable(doorX, doorY, true);
        grid.setDoorway(doorX, doorY, true);
        grid.openAllEdges(doorX, doorY);
        topology.setGroundKind(doorX, doorY, GroundKind.STRIPED);
        if (!grid.inBounds(anchorX, anchorY) || !grid.isWalkable(anchorX, anchorY)) {
            anchorX = doorX;
            anchorY = doorY;
        }
        ctx.pois.add(new PointOfInterest(PointOfInterest.Kind.COMMS,
                left, top, right, bottom, anchorX, anchorY, interiorX, interiorY));
        return new int[]{left, top, right, bottom};
    }

    private static void scatterLegacyCover(BlockLeaf leaf, int[] tower, GenContext ctx) {
        List<int[]> candidates = new ArrayList<>();
        for (int y = leaf.top + 1; y <= leaf.bottom - 1; y++) {
            for (int x = leaf.left + 1; x <= leaf.right - 1; x++) {
                if (!withinMargin(x, y, tower, 1)) candidates.add(new int[]{x, y});
            }
        }
        int area = leaf.area();
        int budget = Math.max(LEGACY_MIN_COVER_ISLANDS,
                Math.min(LEGACY_MAX_COVER_ISLANDS, area / LEGACY_CELLS_PER_COVER_ISLAND));
        List<DoodadDef> pool = GenMappingRegistry.installed().doodadPool(DistrictTheme.SKY_PORT);
        int placed = 0;
        while (placed < budget && !candidates.isEmpty()) {
            int[] cell = candidates.remove(ctx.rng.nextInt(candidates.size()));
            int x = cell[0];
            int y = cell[1];
            if (!ctx.grid.isWalkable(x - 1, y) || !ctx.grid.isWalkable(x + 1, y)
                    || !ctx.grid.isWalkable(x, y - 1) || !ctx.grid.isWalkable(x, y + 1)) continue;
            ctx.grid.setWalkable(x, y, false);
            ctx.doodads.add(new Doodad(x, y, pool.get(ctx.rng.nextInt(pool.size()))));
            placed++;
        }
    }

    private static void scatterLegacyDoodads(BlockLeaf leaf, GenContext ctx) {
        List<int[]> walkable = new ArrayList<>();
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (ctx.grid.isWalkable(x, y) && !ctx.grid.isDoorway(x, y)) {
                    walkable.add(new int[]{x, y});
                }
            }
        }
        int budget = Math.min(LEGACY_MAX_DOODADS,
                Math.max(1, leaf.area() / LEGACY_CELLS_PER_DOODAD));
        List<DoodadDef> pool = GenMappingRegistry.installed().doodadPool(DistrictTheme.SKY_PORT);
        for (int i = 0; i < budget && !walkable.isEmpty(); i++) {
            int[] cell = walkable.remove(ctx.rng.nextInt(walkable.size()));
            ctx.doodads.add(new Doodad(cell[0], cell[1], pool.get(ctx.rng.nextInt(pool.size()))));
        }
    }

    private static boolean withinMargin(int x, int y, int[] rect, int margin) {
        if (rect == null) return false;
        return x >= rect[0] - margin && x <= rect[2] + margin
                && y >= rect[1] - margin && y <= rect[3] + margin;
    }
}
