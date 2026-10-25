package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MainStreet;
import com.dillon.starsectormarines.battle.world.gen.OpenSquare;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.District;
import com.dillon.starsectormarines.battle.world.gen.bsp.SpineClaim;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * The main street layout ({@code set-piece-districts.md}) over a planned {@link SpineClaim.Spine}. Each member
 * is a {@link FrontageRow} of narrow shopfronts behind a pavement, party walls between them, their doors and
 * windows on the street and on the service yard behind; a member too short for a row is one shop. The kerb is
 * cover: planters and stalls at a steady rhythm, and a parked van on each member's kerb where the pavement is
 * two rows deep (three keep a clear walk behind it), all kept out of the doorways and the cross-street corners.
 * Street lamps and awnings have no art yet. The plaza end is the {@link MarketSquare} set piece facing the
 * street where its lot holds it, framed by its own shops with the cross street between its two members running
 * through it, and otherwise an open paved square with a bandstand where it is large enough; the hall at the
 * other end faces the street like the shops. The cross streets' narrowed edges become pavement. Draws only from the
 * map's random stream, inside the shell carve.
 */
final class MainStreetLayout {

    /** Rows of pavement in front of a shop, where the member is deep enough for two. */
    static final int PAVEMENT_MIN = 2;
    /** Rows of pavement where the member is deep enough to keep a clear walk behind a parked van. */
    static final int PAVEMENT_WIDE = 3;
    /** The least depth of a shop behind its pavement: a hollow shell with a standable room. */
    static final int SHOP_MIN_DEPTH = 5;
    /** The member depth from which the pavement is wide. */
    static final int WIDE_FROM = PAVEMENT_WIDE + 6;
    /** Cells along the kerb between one piece of street furniture and the next. */
    static final int RHYTHM = 3;
    /** Length of the van along the kerb, and the least frontage that takes one. */
    static final int VAN = 3, VAN_FRONTAGE = 9;
    /** The least plaza side that holds the bandstand with a walk round it. */
    static final int BANDSTAND_SIDE = 10;

    private static final String PLANTER_H = "doodad.residential-planter-h";
    private static final String PLANTER_V = "doodad.residential-planter-v";
    private static final String STALL = "doodad.galley-servery-counter";
    private static final String VAN_ID = "doodad.parked-box-van";
    private static final String BANDSTAND = "origin-park.bandstand-clean";

    private MainStreetLayout() { }

    /** Lays the main street over {@code district}. Publishes its marks. */
    static MainStreet place(GenContext c, District district) {
        SpineClaim.Spine s = (SpineClaim.Spine) district.claim;
        TileRegistry registry = TileRegistry.installed();
        boolean[][] road = c.get(BspKeys.ROAD_CELLS);
        BlockLeaf plaza = null, hall = null;
        int doorX = -1, doorY = -1;
        List<BlockLeaf> squareGround = new ArrayList<>();
        for (BlockLeaf leaf : s.members()) {
            District.Role role = district.roles.get(leaf);
            if (role == District.Role.MARKET_SQUARE) {
                squareGround.add(leaf);
                continue;
            }
            if (role == District.Role.PLAZA) {
                plaza = leaf;
                plaza(c, s, leaf, registry);
                continue;
            }
            int[] door = frontage(c, s, leaf, role == District.Role.LANDMARK, registry);
            if (role == District.Role.LANDMARK) {
                hall = leaf;
                doorX = door[0];
                doorY = door[1];
            }
        }
        pavedCrossStreets(c, s, road, registry);
        carriageway(c, s, road);
        OpenSquare square = null;
        if (!squareGround.isEmpty()) {
            plaza = streetEnd(s, squareGround, hall);
            square = marketSquare(c, s, squareGround);
            if (square == null) {
                // The finished streets would not hold the square: a plain plaza at the end, shops behind it.
                plaza(c, s, plaza, registry);
                for (BlockLeaf leaf : squareGround) if (leaf != plaza) frontage(c, s, leaf, false, registry);
            }
        }

        int mid = (s.lo + s.hi + 1) / 2;
        boolean plazaLow = plaza != null && hall != null && s.alongLo(plaza) < s.alongLo(hall);
        int startA = plazaLow ? s.from : s.to + 1, endA = plazaLow ? s.to + 1 : s.from;
        MainStreet street = new MainStreet(s.x(startA, mid), s.y(startA, mid), s.x(endA, mid), s.y(endA, mid),
                s.streetWidth(),
                square != null ? square.left() : plaza == null ? -1 : plaza.left,
                square != null ? square.bottom() : plaza == null ? -1 : plaza.top,
                square != null ? square.right() : plaza == null ? -1 : plaza.right,
                square != null ? square.top() : plaza == null ? -1 : plaza.bottom, doorX, doorY, square);
        c.mainStreets.add(street);
        return street;
    }

    /** Which way a member's front faces: onto the street. */
    static BuildingPlacement.Side side(SpineClaim.Spine s, BlockLeaf leaf) {
        boolean near = s.onNearSide(leaf);
        if (s.horizontal) return near ? BuildingPlacement.Side.BOTTOM : BuildingPlacement.Side.TOP;
        return near ? BuildingPlacement.Side.RIGHT : BuildingPlacement.Side.LEFT;
    }

    /** The rows of pavement in front of a member of {@code depth}. */
    static int pavement(int depth) {
        if (depth >= WIDE_FROM) return PAVEMENT_WIDE;
        return depth >= PAVEMENT_MIN + SHOP_MIN_DEPTH ? PAVEMENT_MIN : 1;
    }

    /**
     * A shop or the hall behind its pavement, then the kerb in front of it. Returns the front door nearest the
     * middle of its frontage, or {@code -1, -1}.
     */
    private static int[] frontage(GenContext c, SpineClaim.Spine s, BlockLeaf leaf, boolean hall,
                                  TileRegistry registry) {
        boolean near = s.onNearSide(leaf);
        int cLo = s.acrossLo(leaf), cHi = s.acrossHi(leaf), aLo = s.alongLo(leaf), aHi = s.alongHi(leaf);
        int pave = pavement(cHi - cLo + 1);
        // The kerb row is the one on the street; the wall row is the shop's front wall.
        int kerb = near ? cHi : cLo, step = near ? -1 : 1, wall = kerb + step * pave;
        for (int a = aLo; a <= aHi; a++) {
            for (int k = 0; k < pave; k++) pave(c, s.x(a, kerb + step * k), s.y(a, kerb + step * k));
        }
        int bLo = near ? cLo : cLo + pave, bHi = near ? cHi - pave : cHi;
        BlockLeaf shell = s.horizontal ? new BlockLeaf(aLo, bLo, aHi, bHi, false)
                : new BlockLeaf(bLo, aLo, bHi, aHi, false);
        if (hall || !FrontageRow.place(shell, c, side(s, leaf), FrontageRow.SHOPFRONTS,
                BuildingCommercialFiller.CONFIG, false)) {
            PointOfInterest poi = BuildingShellCore.carve(shell, c.grid, c.topology, c.doodads, c.rng,
                    hall ? BuildingCivicFiller.CONFIG : BuildingCommercialFiller.CONFIG,
                    BuildingPlacement.frontOnly(side(s, leaf)));
            if (poi != null) c.pois.add(poi);
        }

        boolean[] clear = new boolean[aHi - aLo + 1];
        int door = -1, centre = (aLo + aHi) / 2;
        for (int a = aLo; a <= aHi; a++) {
            boolean open = a <= aLo + 1 || a >= aHi - 1;
            if (c.grid.isDoorway(s.x(a, wall), s.y(a, wall))) {
                open = true;
                if (door < 0 || Math.abs(a - centre) < Math.abs(door - centre)) door = a;
                for (int d = -1; d <= 1; d++) if (a + d >= aLo && a + d <= aHi) clear[a + d - aLo] = true;
            }
            if (open) clear[a - aLo] = true;
        }
        int van = -1;
        if (!hall && pave >= PAVEMENT_MIN && aHi - aLo + 1 >= VAN_FRONTAGE) van = vanSpot(clear, aLo, centre);
        DoodadDef vanDef = registry.doodad(VAN_ID);
        if (van >= 0 && vanDef != null) {
            int a0 = van, a1 = van + VAN - 1, c0 = kerb, c1 = kerb + step;
            int minX = Math.min(s.x(a0, c0), s.x(a1, c1)), minY = Math.min(s.y(a0, c0), s.y(a1, c1));
            Doodad prop = new Doodad(minX, minY, vanDef, s.horizontal ? 0 : 1);
            for (int y = minY; y < minY + prop.footprintCellsY; y++) {
                for (int x = minX; x < minX + prop.footprintCellsX; x++) fixture(c, x, y);
            }
            c.doodads.add(prop);
            for (int a = a0 - 1; a <= a1 + 1; a++) if (a >= aLo && a <= aHi) clear[a - aLo] = true;
        }
        int beat = 0;
        for (int a = aLo; a <= aHi; a++) {
            if (clear[a - aLo]) continue;
            if (beat++ % RHYTHM != 0) continue;
            String id = (beat / RHYTHM) % 2 == 0 ? (s.horizontal ? PLANTER_H : PLANTER_V) : STALL;
            solid(c, s.x(a, kerb), s.y(a, kerb), registry.doodad(id));
        }
        return door < 0 ? new int[]{-1, -1} : new int[]{s.x(door, wall), s.y(door, wall)};
    }

    /** The first run of a van's length clear of doors and corners, nearest the middle of the frontage. */
    private static int vanSpot(boolean[] clear, int aLo, int centre) {
        int best = -1;
        for (int i = 0; i + VAN <= clear.length; i++) {
            boolean free = true;
            for (int k = 0; k < VAN && free; k++) free = !clear[i + k];
            if (!free) continue;
            int a = aLo + i;
            if (best < 0 || Math.abs(a + 1 - centre) < Math.abs(best + 1 - centre)) best = a;
        }
        return best;
    }

    /**
     * The plaza: the whole leaf paved, a stone border, a planter at each corner on the street and the
     * bandstand in the middle where the square is large enough: the plaza end where the market square does not
     * stand.
     */
    private static void plaza(GenContext c, SpineClaim.Spine s, BlockLeaf leaf, TileRegistry registry) {
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                c.grid.setWalkableFloor(x, y);
                boolean ring = x == leaf.left || x == leaf.right || y == leaf.top || y == leaf.bottom;
                c.topology.setGroundKind(x, y, ring ? GroundKind.STONE : GroundKind.BRICK);
                c.markMadeGround(x, y);
            }
        }
        boolean near = s.onNearSide(leaf);
        int kerb = near ? s.acrossHi(leaf) : s.acrossLo(leaf);
        String planter = s.horizontal ? PLANTER_H : PLANTER_V;
        solid(c, s.x(s.alongLo(leaf), kerb), s.y(s.alongLo(leaf), kerb), registry.doodad(planter));
        solid(c, s.x(s.alongHi(leaf), kerb), s.y(s.alongHi(leaf), kerb), registry.doodad(planter));
        DoodadDef stand = registry.doodad(BANDSTAND);
        if (stand == null || leaf.width() < BANDSTAND_SIDE || leaf.height() < BANDSTAND_SIDE) return;
        int x0 = leaf.centerX() - stand.footprintCellsX / 2 + 1, y0 = leaf.centerY() - stand.footprintCellsY / 2 + 1;
        Doodad prop = new Doodad(x0, y0, stand);
        for (int y = y0; y < y0 + prop.footprintCellsY; y++) {
            for (int x = x0; x < x0 + prop.footprintCellsX; x++) fixture(c, x, y);
        }
        c.doodads.add(prop);
    }


    /** The member of the square's lot at the street's end, away from the hall. */
    private static BlockLeaf streetEnd(SpineClaim.Spine s, List<BlockLeaf> ground, BlockLeaf hall) {
        BlockLeaf first = ground.get(0), last = ground.get(0);
        for (BlockLeaf leaf : ground) {
            if (s.alongLo(leaf) < s.alongLo(first)) first = leaf;
            if (s.alongHi(leaf) > s.alongHi(last)) last = leaf;
        }
        return hall != null && s.alongLo(hall) < s.alongLo(first) ? last : first;
    }

    /**
     * The market square over its lot, facing the main street, or null where the finished streets do not hold
     * it. What the square leaves of the lot is more paving, as round a standalone square.
     */
    private static OpenSquare marketSquare(GenContext c, SpineClaim.Spine s, List<BlockLeaf> ground) {
        boolean[][] road = c.get(BspKeys.ROAD_CELLS);
        MarketSquare.Placement market = chooseSquare(s, ground, road, c.get(BspKeys.ROAD_RESERVATION),
                c.width, c.height, c.landingPads);
        if (market == null) return null;
        boolean[][] footprint = MarketSquare.footprint(market, c.width, c.height);
        for (BlockLeaf leaf : ground) {
            for (int y = leaf.top; y <= leaf.bottom; y++) {
                for (int x = leaf.left; x <= leaf.right; x++) {
                    if (footprint[x][y]) continue;
                    c.grid.setWalkableFloor(x, y);
                    c.topology.setGroundKind(x, y, GroundKind.BRICK);
                }
            }
        }
        return MarketSquare.place(c, market);
    }

    /**
     * Where the market square stands on its lot ({@code ground}, the members holding
     * {@link District.Role#MARKET_SQUARE}), or null: its front on the main street's carriageway or the street it
     * runs on into past the spine's end, its footprint on the lot and on street round it but never on that
     * street or another leaf, and the street between the lot's members running through its open middle, between
     * the side rows. {@code reserved} may be
     * null before the road reservation is drawn. Reads nothing else, so the program asks it while planning.
     */
    static MarketSquare.Placement chooseSquare(SpineClaim.Spine s, List<BlockLeaf> ground, boolean[][] road,
                                               boolean[][] reserved, int width, int height, List<LandingPad> pads) {
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        for (BlockLeaf leaf : ground) {
            left = Math.min(left, leaf.left);
            top = Math.min(top, leaf.top);
            right = Math.max(right, leaf.right);
            bottom = Math.max(bottom, leaf.bottom);
        }
        BiPredicate<Integer, Integer> carriageway = (x, y) -> {
            int across = s.across(x, y);
            return across >= s.lo && across <= s.hi && x >= 0 && y >= 0 && x < width && y < height && road[x][y];
        };
        BiPredicate<Integer, Integer> lot = (x, y) -> {
            for (BlockLeaf leaf : ground) if (leaf.contains(x, y)) return true;
            return false;
        };
        // Between the members: inside the lot's box, on neither member and off the main street.
        int boxLeft = left, boxTop = top, boxRight = right, boxBottom = bottom;
        BiPredicate<Integer, Integer> through = (x, y) -> x >= boxLeft && x <= boxRight && y >= boxTop
                && y <= boxBottom && !lot.test(x, y) && !carriageway.test(x, y);
        return MarketSquare.choose(width, height, pads, road, reserved, left, top, right, bottom,
                (x, y) -> lot.test(x, y) || (road[x][y] && !carriageway.test(x, y)), lot, carriageway, through,
                MarketSquare.preference(left + top));
    }

    /** The cells the rewrite took from the cross streets: pavement, with a planter on the main street's kerb. */
    private static void pavedCrossStreets(GenContext c, SpineClaim.Spine s, boolean[][] road, TileRegistry registry) {
        for (SpineClaim.Gap gap : s.gaps) {
            int kerb = gap.near() ? gap.hi() : gap.lo();
            for (int a = gap.from(); a <= gap.to(); a++) {
                for (int cc = gap.lo(); cc <= gap.hi(); cc++) {
                    int x = s.x(a, cc), y = s.y(a, cc);
                    if (road[x][y]) continue;
                    pave(c, x, y);
                    if (cc == kerb) solid(c, x, y, registry.doodad(s.horizontal ? PLANTER_V : PLANTER_H));
                }
            }
        }
    }

    /**
     * The main street and the stubs of its cross streets are paved city street, wherever the settlement would
     * have left a lane of its own ground: the street finish then lays its asphalt and sidewalks over them like
     * any other.
     */
    private static void carriageway(GenContext c, SpineClaim.Spine s, boolean[][] road) {
        for (int a = s.from; a <= s.to; a++) {
            for (int cc = s.lo; cc <= s.hi; cc++) street(c, road, s.x(a, cc), s.y(a, cc));
        }
        for (SpineClaim.Gap gap : s.gaps) {
            for (int a = gap.from(); a <= gap.to(); a++) {
                for (int cc = gap.lo(); cc <= gap.hi(); cc++) street(c, road, s.x(a, cc), s.y(a, cc));
            }
        }
    }

    private static void street(GenContext c, boolean[][] road, int x, int y) {
        if (!road[x][y]) return;
        c.topology.setGroundKind(x, y, GroundKind.STREET);
        c.topology.setSurface(x, y, 0);
        c.topology.setNatureOverlayIndex(x, y, -1);
    }

    private static void pave(GenContext c, int x, int y) {
        c.grid.setWalkableFloor(x, y);
        c.grid.openAllEdges(x, y);
        c.topology.setFixture(x, y, false);
        c.topology.setGroundKind(x, y, GroundKind.SIDEWALK);
    }

    private static void fixture(GenContext c, int x, int y) {
        c.grid.setWalkable(x, y, false);
        c.grid.setSeeThrough(x, y, true);
        c.topology.setFixture(x, y, true);
    }

    private static void solid(GenContext c, int x, int y, DoodadDef def) {
        if (def == null) return;
        fixture(c, x, y);
        c.doodads.add(new Doodad(x, y, def));
    }
}
