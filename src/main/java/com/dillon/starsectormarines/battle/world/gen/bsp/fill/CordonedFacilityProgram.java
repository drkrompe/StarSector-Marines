package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.CordonedFacility;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.District;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictClaim;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictProgram;
import com.dillon.starsectormarines.battle.world.gen.bsp.QuarterClaim;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The cordoned facility program ({@code set-piece-districts.md}): a {@link QuarterClaim} of two rows of two or
 * three leaves standing in a working or public pocket on balance ({@link #pocket}). The member nearest the
 * quarter's middle is the {@link District.Role#COURTYARD}, the largest of the rest the
 * {@link District.Role#ADMIN} block, the next largest the {@link District.Role#DEPOT}; a quarter of five or six
 * gives its smallest remaining member to a {@link District.Role#YARD}, and every other member is a
 * {@link District.Role#LAB}.
 *
 * <p>Its street rewrite is the gatehouses: wherever an inner street meets a street outside the quarter, the
 * last {@link #GATEHOUSE_DEPTH} cells of it narrow to a lane {@link #LANE} wide, the cells either side becoming
 * the gatehouse. The lane is kept where the street outside meets it, so every mouth keeps its way in, and the
 * inner streets inside the gatehouses are left whole. An inner street that ends on the edge against a lot rather
 * than a street is closed there, so the wall runs unbroken across it.
 */
public final class CordonedFacilityProgram implements DistrictProgram {

    public static final String ID = "cordoned-facility";

    /** Cells of street the gatehouse takes, from the wall line in. */
    static final int GATEHOUSE_DEPTH = 3;
    /** The lane a gatehouse keeps: the narrowest street. */
    static final int LANE = Bsp.ROAD_WIDTH_MIN;
    /**
     * A member's least side: the wall, an apron and a standable shell behind it, and the pavement onto its
     * street.
     */
    static final int MIN_SIDE = 7;

    /** Up to two rows of three; inner streets no wider than the partition lays one; members of {@link #MIN_SIDE}. */
    static final QuarterClaim.Spec SPEC = new QuarterClaim.Spec(2, 3, Bsp.ROAD_WIDTH_MAX, MIN_SIDE);

    /**
     * Kinds a facility may take over: working and public lots, spare ground and homes, which weigh nothing in its
     * pocket of their own, so a facility stands among houses only where its zoning carries them. A leaf on the
     * map's edge is never a member: the wall would stand on the edge of the world.
     */
    private static final Set<BlockKind> KINDS = EnumSet.of(BlockKind.BUILDING_INDUSTRIAL, BlockKind.BUILDING_CIVIC,
            BlockKind.BUILDING_COMMERCIAL, BlockKind.DENSE_BLOCK, BlockKind.INDUSTRIAL_YARD, BlockKind.PLAZA,
            BlockKind.PARK, BlockKind.WASTELAND_RUBBLE, BlockKind.BUILDING_RESIDENTIAL);

    @Override public String id() { return ID; }

    @Override public BlockKind kind() { return BlockKind.CORDONED_FACILITY; }

    @Override
    public Request request(GenContext ctx) {
        DistrictMap zoning = ctx.get(BspKeys.DISTRICT_MAP);
        if (zoning == null) return null;
        return new QuarterRequest(SPEC, leaf -> KINDS.contains(leaf.kind) && !leaf.touchesMapEdge,
                leaf -> pocket(leaf.kind, zoning.themeAt(leaf.centerX(), leaf.centerY())));
    }

    /**
     * How much a leaf belongs in a facility: the zoning's industrial, civic and military pockets count twice and
     * the mixed town once, and a lot the labeller already made a works or a public building once more.
     */
    static int pocket(BlockKind kind, MapDistrictTheme theme) {
        int zoned = theme == MapDistrictTheme.INDUSTRIAL || theme == MapDistrictTheme.CIVIC
                || theme == MapDistrictTheme.MILITARY_FORT ? 2 : theme == MapDistrictTheme.MIXED ? 1 : 0;
        boolean working = kind == BlockKind.BUILDING_INDUSTRIAL || kind == BlockKind.BUILDING_CIVIC
                || kind == BlockKind.INDUSTRIAL_YARD;
        return zoned + (working ? 1 : 0);
    }

    /** What a facility in {@code theme} is: a depot among works, a civil-defence centre among homes and halls. */
    static CordonedFacility.Character character(MapDistrictTheme theme) {
        if (theme == MapDistrictTheme.INDUSTRIAL || theme == MapDistrictTheme.HARBOR_PORT) {
            return CordonedFacility.Character.DEPOT;
        }
        if (theme == MapDistrictTheme.CIVIC || theme == MapDistrictTheme.RESIDENTIAL
                || theme == MapDistrictTheme.MILITARY_FORT) {
            return CordonedFacility.Character.CIVIL_DEFENCE;
        }
        return CordonedFacility.Character.RESEARCH_STATION;
    }

    @Override
    public Plan plan(DistrictClaim claim, boolean[][] road, GenContext ctx) {
        if (!(claim instanceof QuarterClaim.Quarter quarter)) return null;
        Map<BlockLeaf, District.Role> roles = roles(quarter);
        BlockLeaf admin = null;
        for (BlockLeaf leaf : quarter.members()) if (roles.get(leaf) == District.Role.ADMIN) admin = leaf;
        return new Plan(roles, admin, gated(quarter, road));
    }

    /** Each member's role (see the class comment). */
    static Map<BlockLeaf, District.Role> roles(QuarterClaim.Quarter quarter) {
        int[] b = quarter.bounds();
        int cx = b[0] + b[2], cy = b[1] + b[3];
        List<BlockLeaf> rest = new ArrayList<>(quarter.members());
        BlockLeaf courtyard = rest.get(0);
        for (BlockLeaf leaf : rest) {
            int d = Math.abs(2 * leaf.centerX() - cx) + Math.abs(2 * leaf.centerY() - cy);
            int best = Math.abs(2 * courtyard.centerX() - cx) + Math.abs(2 * courtyard.centerY() - cy);
            if (d < best || (d == best && leaf.area() < courtyard.area())) courtyard = leaf;
        }
        Map<BlockLeaf, District.Role> roles = new IdentityHashMap<>();
        roles.put(courtyard, District.Role.COURTYARD);
        rest.remove(courtyard);
        BlockLeaf admin = largest(rest);
        roles.put(admin, District.Role.ADMIN);
        rest.remove(admin);
        BlockLeaf depot = largest(rest);
        roles.put(depot, District.Role.DEPOT);
        rest.remove(depot);
        if (quarter.members().size() >= 5) {
            BlockLeaf yard = rest.get(0);
            for (BlockLeaf leaf : rest) if (leaf.area() < yard.area()) yard = leaf;
            roles.put(yard, District.Role.YARD);
            rest.remove(yard);
        }
        for (BlockLeaf leaf : rest) roles.put(leaf, District.Role.LAB);
        return roles;
    }

    private static BlockLeaf largest(List<BlockLeaf> leaves) {
        BlockLeaf best = leaves.get(0);
        for (BlockLeaf leaf : leaves) if (leaf.area() > best.area()) best = leaf;
        return best;
    }

    /**
     * A gatehouse at one end of an inner street: the street, whether it runs along x, the wall line's
     * coordinate along it and the step inward from there, and the lane kept across it, inclusive.
     */
    record Gate(QuarterClaim.Street street, boolean alongX, int wall, int inward, int laneLo, int laneHi) {
        int x(int along, int across) { return alongX ? along : across; }
        int y(int along, int across) { return alongX ? across : along; }
        int lo() { return alongX ? street.top() : street.left(); }
        int hi() { return alongX ? street.bottom() : street.right(); }
    }

    /**
     * Every gatehouse of the quarter under {@code road}: each end of an inner street on the quarter's edge with
     * street beyond it. Its lane is the {@link #LANE} cells, nearest the street's middle, that are street at the
     * wall line and have street beyond at least one of them; a street no wider than the lane is its own lane.
     * Read-only, and the same before and after {@link #gated}, which narrows only outside the lanes.
     */
    static List<Gate> gates(QuarterClaim.Quarter quarter, boolean[][] road) {
        List<Gate> gates = new ArrayList<>();
        for (Gate end : ends(quarter)) {
            Gate gate = lane(end, road);
            if (gate != null) gates.add(gate);
        }
        return gates;
    }

    /** Each end of an inner street on the quarter's edge, as a gate with the whole street for its lane. */
    static List<Gate> ends(QuarterClaim.Quarter quarter) {
        int[] b = quarter.bounds();
        List<Gate> ends = new ArrayList<>();
        int main = quarter.rows.size() - 1;
        for (int i = 0; i < quarter.streets.size(); i++) {
            QuarterClaim.Street s = quarter.streets.get(i);
            // A main cross street runs the quarter's length; a row's cross street runs across it.
            boolean alongX = (i < main) == quarter.horizontal;
            int lo = alongX ? s.top() : s.left(), hi = alongX ? s.bottom() : s.right();
            int first = alongX ? s.left() : s.top(), last = alongX ? s.right() : s.bottom();
            if (first == (alongX ? b[0] : b[1])) ends.add(new Gate(s, alongX, first, 1, lo, hi));
            if (last == (alongX ? b[2] : b[3])) ends.add(new Gate(s, alongX, last, -1, lo, hi));
        }
        return ends;
    }

    /** {@code end} with its lane chosen under {@code road}, or null where no street is beyond it. */
    private static Gate lane(Gate end, boolean[][] road) {
        int lo = end.lo(), hi = end.hi(), wall = end.wall(), inward = end.inward();
        int width = Math.min(LANE, hi - lo + 1), mid = (lo + hi - width + 1) / 2;
        int best = Integer.MIN_VALUE;
        for (int start = lo; start + width - 1 <= hi; start++) {
            boolean open = true, beyond = false;
            for (int c = start; c < start + width; c++) {
                open &= street(road, end.x(wall, c), end.y(wall, c));
                beyond |= street(road, end.x(wall - inward, c), end.y(wall - inward, c));
            }
            if (open && beyond && (best == Integer.MIN_VALUE || Math.abs(start - mid) < Math.abs(best - mid))) {
                best = start;
            }
        }
        if (best == Integer.MIN_VALUE) return null;
        return new Gate(end.street(), end.alongX(), wall, inward, best, best + width - 1);
    }

    /**
     * The street mask with every gatehouse narrowed to its lane for {@link #GATEHOUSE_DEPTH} cells, and every
     * inner street that ends on the quarter's edge with no street beyond closed at the wall line: it is no way
     * in, so the wall runs across it.
     */
    static boolean[][] gated(QuarterClaim.Quarter quarter, boolean[][] road) {
        boolean[][] streets = new boolean[road.length][];
        for (int x = 0; x < road.length; x++) streets[x] = road[x].clone();
        for (Gate end : ends(quarter)) {
            Gate g = lane(end, road);
            if (g == null) {
                for (int c = end.lo(); c <= end.hi(); c++) streets[end.x(end.wall(), c)][end.y(end.wall(), c)] = false;
                continue;
            }
            for (int d = 0; d < GATEHOUSE_DEPTH; d++) {
                int a = g.wall() + g.inward() * d;
                for (int c = g.lo(); c <= g.hi(); c++) {
                    if (c < g.laneLo() || c > g.laneHi()) streets[g.x(a, c)][g.y(a, c)] = false;
                }
            }
        }
        return streets;
    }

    private static boolean street(boolean[][] road, int x, int y) {
        return x >= 0 && y >= 0 && x < road.length && y < road[0].length && road[x][y];
    }
}
