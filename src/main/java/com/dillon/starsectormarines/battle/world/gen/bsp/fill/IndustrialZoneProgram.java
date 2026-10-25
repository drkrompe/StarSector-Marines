package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.District;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictClaim;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictProgram;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictStreets;
import com.dillon.starsectormarines.battle.world.gen.bsp.SpineClaim;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraphBuilder;
import com.dillon.starsectormarines.battle.world.gen.road.RoadReservation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * The industrial zone program ({@code set-piece-districts.md}): a {@link SpineClaim} of at least three leaves
 * along one service street, standing in an industrial pocket on balance ({@link #pocket}). It is the map's host
 * for the freight yard and the factory row. The freight-yard parcel and the factory row's seed may be members
 * and keep their set piece as their role; a row seed too small for the row alone takes its neighbour along the
 * street with it, the cross street between them joined into the row's ground. Where the street has neither,
 * the members that hold one best become the yard, then the row, a single member or a neighbouring pair joined
 * the same way; once committed, the zone stands down the yard or the row planned elsewhere on the map
 * ({@link #committed}). Every other member deep and wide enough is a {@link District.Role#WAREHOUSE} with its
 * loading doors on the street; the rest, and the smallest of the warehouses where every member would be one,
 * are tank farms and vehicle depots. Its street rewrite narrows a cross street to the narrowest street where
 * only warehouses and depots stand either side of it, so a yard or a row keeps every gate and turn.
 */
public final class IndustrialZoneProgram implements DistrictProgram {

    public static final String ID = "industrial-zone";

    /**
     * The pull of the freight-yard parcel or the factory row's seed on a zone: worth more than any two members
     * deep in the pocket, so a street that hosts one beats a longer one that hosts neither.
     */
    static final int SET_PIECE = 6;

    /** The fewest members a zone takes: a yard, a row or a warehouse, and two more along the street. */
    static final int MIN_MEMBERS = 3;

    /**
     * One to three members a side; a street at least twenty cells long and no wider than a primary trunk;
     * members deep and wide enough for a walled yard behind its pavement.
     */
    static final SpineClaim.Spec SPEC = new SpineClaim.Spec(1, 3, 20, 7,
            IndustrialZoneLayout.MEMBER_MIN_DEPTH, IndustrialZoneLayout.MEMBER_MIN_FRONT);

    /**
     * Kinds a zone may take over: working ground, ground a settlement can spare, homes and guard posts, and the
     * two set pieces it hosts. The pocket keeps it in the industrial belt.
     */
    private static final Set<BlockKind> KINDS = EnumSet.of(BlockKind.BUILDING_INDUSTRIAL, BlockKind.INDUSTRIAL_YARD,
            BlockKind.FREIGHT_YARD, BlockKind.FACTORY_ROW, BlockKind.WASTELAND_RUBBLE, BlockKind.BUILDING_COMMERCIAL,
            BlockKind.DENSE_BLOCK, BlockKind.PLAZA, BlockKind.PARK, BlockKind.BUILDING_RESIDENTIAL,
            BlockKind.FORTIFIED_POST);

    @Override public String id() { return ID; }

    @Override public BlockKind kind() { return BlockKind.INDUSTRIAL_ZONE; }

    @Override
    public Request request(GenContext ctx) {
        DistrictMap zoning = ctx.get(BspKeys.DISTRICT_MAP);
        if (zoning == null) return null;
        return new SpineRequest(SPEC, IndustrialZoneProgram::eligible,
                leaf -> pocket(leaf.kind, zoning.themeAt(leaf.centerX(), leaf.centerY())));
    }

    /**
     * Whether a leaf may join a zone; compound seeds, civic ground and nature never do. The factory row's seed
     * joins only where some side of it is as deep as the row, so it may hold the row alone or with a neighbour;
     * a smaller seed is left to claim its neighbour as it would have, and the zone looks along another street.
     */
    static boolean eligible(BlockLeaf leaf) {
        if (leaf.kind == BlockKind.FACTORY_ROW) return Math.max(leaf.width(), leaf.height()) >= FactoryRow.DEPTH;
        return KINDS.contains(leaf.kind);
    }

    /**
     * How much a leaf belongs in an industrial zone: the zoning's industrial pocket counts twice and a harbour
     * or the outskirts once; a lot already labelled working ground counts once more, and the freight yard's
     * parcel or the factory row's seed {@link #SET_PIECE} more, so a zone stands where those set pieces are. Anything else
     * outside the pocket weighs nothing.
     */
    static int pocket(BlockKind kind, MapDistrictTheme theme) {
        int zoned = theme == MapDistrictTheme.INDUSTRIAL ? 2 : theme == MapDistrictTheme.HARBOR_PORT || theme == MapDistrictTheme.OUTSKIRTS ? 1 : 0;
        int working = switch (kind) {
            case FREIGHT_YARD, FACTORY_ROW -> SET_PIECE;
            case BUILDING_INDUSTRIAL, INDUSTRIAL_YARD -> 1;
            default -> 0;
        };
        return zoned + working;
    }

    @Override
    public Plan plan(DistrictClaim claim, boolean[][] road, GenContext ctx) {
        if (!(claim instanceof SpineClaim.Spine spine) || spine.members().size() < MIN_MEMBERS) return null;
        Stands stands = ctx == null ? (ground, yard) -> true : stands(spine, road, ctx);
        Map<BlockLeaf, District.Role> roles = roles(spine, true, stands);
        if (roles != null && !DistrictStreets.keepsEntries(spine, road, narrowed(spine, roles, road))) {
            roles = roles(spine, false, stands);
        }
        if (roles == null) return null;
        BlockLeaf seed = spine.members().get(0);
        for (District.Role first : new District.Role[]{District.Role.FACTORY_ROW, District.Role.FREIGHT_YARD}) {
            for (BlockLeaf leaf : spine.members()) if (roles.get(leaf) == first) { seed = leaf; break; }
        }
        boolean[][] streets = narrowed(spine, roles, road, true);
        if (ctx != null && !keepsYardOutside(spine, roles, road, streets, ctx)) streets = narrowed(spine, roles, road, false);
        return new Plan(roles, seed, streets);
    }

    /** Whether the freight yard ({@code yard}) or the factory row stands on a member or a joined pair. */
    interface Stands {
        boolean test(List<BlockLeaf> ground, boolean yard);
    }

    /**
     * Asks the layout whether the set piece stands on the ground, over the streets as the plan would leave them
     * round it (the joined cross street gone) and the road reservation those streets would draw: so the zone
     * takes a yard or a row only where one will stand, and never stands down one planned elsewhere for nothing.
     */
    private static Stands stands(SpineClaim.Spine spine, boolean[][] road, GenContext ctx) {
        TrunkPlan.Plan trunks = ctx.get(BspKeys.TRUNK_PLAN);
        return (ground, yard) -> {
            boolean[][] streets = new boolean[road.length][];
            for (int x = 0; x < road.length; x++) streets[x] = road[x].clone();
            SpineClaim.Gap gap = ground.size() == 2 ? between(spine, ground.get(0), ground.get(1)) : null;
            if (gap != null) {
                for (int a = gap.from(); a <= gap.to(); a++) {
                    for (int c = gap.lo(); c <= gap.hi(); c++) streets[spine.x(a, c)][spine.y(a, c)] = false;
                }
            }
            boolean[][] reserved = trunks == null ? null
                    : RoadReservation.mask(RoadGraphBuilder.build(streets, trunks), road.length, road[0].length);
            return IndustrialZoneLayout.stands(ctx, spine, ground, yard, streets, reserved);
        };
    }

    /**
     * Each member's role, or null where the factory row's seed is a member and holds no row even with a
     * neighbour: the seed then stays outside, to claim as it would have. Without {@code join} no neighbouring
     * pair is joined: the plan falls back to that where a joined cross street would cut a way in. The freight-yard parcel stays a yard
     * and the row's seed a row. Where the street has neither, the largest member that holds one alone takes
     * it, else the neighbouring pair whose joined ground holds it. The rest are warehouses where deep and wide
     * enough and depots where not, alternating tank farm and vehicle depot along the street; where every one of
     * them would be a warehouse and there are two or more, the smallest becomes a depot. A guard post keeps its
     * hardpoint.
     */
    static Map<BlockLeaf, District.Role> roles(SpineClaim.Spine spine, boolean join, Stands stands) {
        Map<BlockLeaf, District.Role> roles = new IdentityHashMap<>();
        for (BlockLeaf leaf : spine.members()) {
            if (leaf.kind == BlockKind.FREIGHT_YARD) roles.put(leaf, District.Role.FREIGHT_YARD);
            else if (leaf.kind == BlockKind.FORTIFIED_POST) roles.put(leaf, District.Role.SECURITY_POST);
        }
        for (BlockLeaf leaf : spine.members()) {
            if (leaf.kind != BlockKind.FACTORY_ROW || roles.containsValue(District.Role.FACTORY_ROW)) continue;
            List<BlockLeaf> ground = null;
            for (List<BlockLeaf> g : grounds(spine, roles, FactoryRow::fits, join)) {
                if (g.contains(leaf) && stands.test(g, false)) {
                    ground = g;
                    break;
                }
            }
            if (ground == null) return null;
            for (BlockLeaf m : ground) roles.put(m, District.Role.FACTORY_ROW);
        }
        host(spine, roles, District.Role.FREIGHT_YARD, FreightYard.Size.STANDARD::fits, join, stands);
        host(spine, roles, District.Role.FACTORY_ROW, FactoryRow::fits, join, stands);
        List<BlockLeaf> warehouses = new ArrayList<>(), depots = new ArrayList<>();
        for (BlockLeaf leaf : spine.members()) {
            if (roles.containsKey(leaf)) continue;
            int depth = spine.acrossHi(leaf) - spine.acrossLo(leaf) + 1;
            int front = spine.alongHi(leaf) - spine.alongLo(leaf) + 1;
            boolean warehouse = depth >= IndustrialZoneLayout.WAREHOUSE_MIN_DEPTH
                    && front >= IndustrialZoneLayout.WAREHOUSE_MIN_FRONT;
            (warehouse ? warehouses : depots).add(leaf);
        }
        if (depots.isEmpty() && warehouses.size() >= 2) {
            BlockLeaf smallest = warehouses.get(0);
            for (BlockLeaf leaf : warehouses) if (leaf.area() < smallest.area()) smallest = leaf;
            warehouses.remove(smallest);
            depots.add(smallest);
        }
        for (BlockLeaf leaf : warehouses) roles.put(leaf, District.Role.WAREHOUSE);
        int depot = 0;
        for (BlockLeaf leaf : spine.members()) {
            if (!depots.contains(leaf)) continue;
            roles.put(leaf, depot++ % 2 == 0 ? District.Role.TANK_FARM : District.Role.VEHICLE_DEPOT);
        }
        return roles;
    }

    /**
     * Gives {@code role} to the first ground {@link #grounds} offers that the set piece stands on; nothing where
     * the street already has it or nothing holds it.
     */
    private static void host(SpineClaim.Spine spine, Map<BlockLeaf, District.Role> roles, District.Role role,
                             BiPredicate<Integer, Integer> holds, boolean join, Stands stands) {
        if (roles.containsValue(role)) return;
        for (List<BlockLeaf> ground : grounds(spine, roles, holds, join)) {
            if (!stands.test(ground, role == District.Role.FREIGHT_YARD)) continue;
            for (BlockLeaf m : ground) roles.put(m, role);
            return;
        }
    }

    /**
     * The grounds a set piece might stand on, best first: each free member that holds it alone, largest first,
     * then (where {@code join}) each free neighbouring pair on one side whose joined ground holds it, largest
     * first.
     */
    static List<List<BlockLeaf>> grounds(SpineClaim.Spine spine, Map<BlockLeaf, District.Role> roles,
                                         BiPredicate<Integer, Integer> holds, boolean join) {
        List<List<BlockLeaf>> singles = new ArrayList<>(), pairs = new ArrayList<>();
        for (BlockLeaf leaf : spine.members()) {
            if (!roles.containsKey(leaf) && holds.test(leaf.width(), leaf.height())) singles.add(List.of(leaf));
        }
        if (join) {
            for (SpineClaim.Gap gap : spine.gaps) {
                BlockLeaf a = before(spine, gap), b = after(spine, gap);
                if (a == null || b == null || roles.containsKey(a) || roles.containsKey(b)) continue;
                int[] g = ground(spine, a, b);
                if (holds.test(g[0], g[1])) pairs.add(List.of(a, b));
            }
        }
        singles.sort((x, y) -> Integer.compare(area(spine, y), area(spine, x)));
        pairs.sort((x, y) -> Integer.compare(area(spine, y), area(spine, x)));
        singles.addAll(pairs);
        return singles;
    }

    private static int area(SpineClaim.Spine spine, List<BlockLeaf> ground) {
        if (ground.size() == 1) return ground.get(0).area();
        int[] g = ground(spine, ground.get(0), ground.get(1));
        return g[0] * g[1];
    }

    /**
     * The ground two neighbours on one side make with the cross street between them, {@code {along, across}}:
     * from the first's start to the second's end, and as deep as the cross street reaches between them.
     */
    static int[] ground(SpineClaim.Spine spine, BlockLeaf a, BlockLeaf b) {
        int along = Math.max(spine.alongHi(a), spine.alongHi(b)) - Math.min(spine.alongLo(a), spine.alongLo(b)) + 1;
        SpineClaim.Gap gap = between(spine, a, b);
        return new int[]{along, gap == null ? 0 : gap.hi() - gap.lo() + 1};
    }

    /** The cross street between two neighbours on one side, or null. */
    static SpineClaim.Gap between(SpineClaim.Spine spine, BlockLeaf a, BlockLeaf b) {
        for (SpineClaim.Gap gap : spine.gaps) {
            BlockLeaf before = before(spine, gap), after = after(spine, gap);
            if ((before == a && after == b) || (before == b && after == a)) return gap;
        }
        return null;
    }

    /**
     * The street mask with the cross street between a joined pair given to the set piece on it, and each cross
     * street between two warehouses or depots narrowed to the narrowest street, the cells beyond it given to the
     * member past it. A cross street beside a yard or a row is left whole.
     */
    static boolean[][] narrowed(SpineClaim.Spine spine, Map<BlockLeaf, District.Role> roles, boolean[][] road) {
        return narrowed(spine, roles, road, true);
    }

    /** {@link #narrowed(SpineClaim.Spine, Map, boolean[][])}, leaving the plain cross streets whole unless {@code narrow}. */
    static boolean[][] narrowed(SpineClaim.Spine spine, Map<BlockLeaf, District.Role> roles, boolean[][] road,
                                boolean narrow) {
        boolean[][] streets = new boolean[road.length][];
        for (int x = 0; x < road.length; x++) streets[x] = road[x].clone();
        for (SpineClaim.Gap gap : spine.gaps) {
            District.Role before = roles.get(before(spine, gap)), after = roles.get(after(spine, gap));
            int from;
            if (before == after && (before == District.Role.FACTORY_ROW || before == District.Role.FREIGHT_YARD)) {
                from = gap.from();
            } else if (narrow && plain(before) && plain(after)) {
                from = gap.from() + Bsp.ROAD_WIDTH_MIN;
            } else {
                continue;
            }
            for (int a = from; a <= gap.to(); a++) {
                for (int c = gap.lo(); c <= gap.hi(); c++) streets[spine.x(a, c)][spine.y(a, c)] = false;
            }
        }
        return streets;
    }

    /**
     * Whether the freight-yard parcel outside a zone that hosts no yard still holds its yard over the zone's
     * streets: a narrowed cross street can take the street its gates open onto, and the zone then narrows none.
     */
    private static boolean keepsYardOutside(SpineClaim.Spine spine, Map<BlockLeaf, District.Role> roles,
                                            boolean[][] road, boolean[][] streets, GenContext ctx) {
        if (roles.containsValue(District.Role.FREIGHT_YARD)) return true;
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        TrunkPlan.Plan trunks = ctx.get(BspKeys.TRUNK_PLAN);
        if (partition == null || trunks == null) return true;
        for (BlockLeaf leaf : partition.leaves) {
            if (leaf.kind != BlockKind.FREIGHT_YARD || spine.members().contains(leaf)) continue;
            boolean[][] before = RoadReservation.mask(RoadGraphBuilder.build(road, trunks), road.length, road[0].length);
            boolean[][] after = RoadReservation.mask(RoadGraphBuilder.build(streets, trunks), road.length, road[0].length);
            return FreightYard.choose(ctx, leaf.left, leaf.top, leaf.right, leaf.bottom, leaf::contains, road, before) == null
                    || FreightYard.choose(ctx, leaf.left, leaf.top, leaf.right, leaf.bottom, leaf::contains, streets, after) != null;
        }
        return true;
    }

    /**
     * Stands down the set pieces the zone now hosts: the freight-yard parcel outside it becomes an ordinary
     * industrial yard and the factory row's seed outside it an ordinary industrial lot, so the map has one of
     * each and they stand on the service street.
     */
    @Override
    public void committed(DistrictClaim claim, Plan plan, List<BlockLeaf> leaves) {
        boolean yard = plan.roles().containsValue(District.Role.FREIGHT_YARD);
        boolean row = plan.roles().containsValue(District.Role.FACTORY_ROW);
        for (BlockLeaf leaf : leaves) {
            if (claim.members().contains(leaf)) continue;
            if (yard && leaf.kind == BlockKind.FREIGHT_YARD) leaf.kind = BlockKind.INDUSTRIAL_YARD;
            if (row && leaf.kind == BlockKind.FACTORY_ROW) leaf.kind = BlockKind.BUILDING_INDUSTRIAL;
        }
    }

    /** The member on the gap's side whose end it starts after. */
    static BlockLeaf before(SpineClaim.Spine spine, SpineClaim.Gap gap) {
        for (BlockLeaf leaf : gap.near() ? spine.near : spine.far) {
            if (spine.alongHi(leaf) == gap.from() - 1) return leaf;
        }
        return null;
    }

    /** The member on the gap's side that starts after it. */
    static BlockLeaf after(SpineClaim.Spine spine, SpineClaim.Gap gap) {
        for (BlockLeaf leaf : gap.near() ? spine.near : spine.far) {
            if (spine.alongLo(leaf) == gap.to() + 1) return leaf;
        }
        return null;
    }

    private static boolean plain(District.Role role) {
        return role == District.Role.WAREHOUSE || role == District.Role.TANK_FARM
                || role == District.Role.VEHICLE_DEPOT || role == District.Role.SECURITY_POST;
    }
}
