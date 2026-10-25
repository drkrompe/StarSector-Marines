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
import com.dillon.starsectormarines.battle.world.gen.bsp.SpineClaim;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The industrial zone program ({@code set-piece-districts.md}): a {@link SpineClaim} of at least three working
 * leaves along one service street, standing in an industrial pocket on balance ({@link #pocket}). It hosts the
 * set pieces already planned on its ground rather than taking leaves from them: the map's freight-yard parcel
 * and its factory row seed may be members, and they keep their set piece as their role, so a zone gathers
 * round them. Where neither is a member, the member that holds one best becomes the yard, then the row. Every
 * other member deep enough is a {@link District.Role#WAREHOUSE} with its loading doors on the street; a leaf
 * too shallow for one, or the smallest of the warehouses where every member would be one, is a tank farm or a
 * vehicle depot. Its street rewrite narrows a cross street to the narrowest street where only warehouses and
 * depots stand either side of it, so a yard or a row keeps every gate and turn its parcel was planned for.
 */
public final class IndustrialZoneProgram implements DistrictProgram {

    public static final String ID = "industrial-zone";

    /** The fewest members a zone takes: a yard, a row or a warehouse, and two more along the street. */
    static final int MIN_MEMBERS = 3;

    /**
     * One to three members a side; a street at least twenty-four cells long and no wider than a primary trunk;
     * members deep enough for a dock apron in front of a standable hall, and wide enough for a gate.
     */
    static final SpineClaim.Spec SPEC = new SpineClaim.Spec(1, 3, 20, 7,
            IndustrialZoneLayout.MEMBER_MIN_DEPTH, IndustrialZoneLayout.YARD_MIN_FRONT);

    /** Kinds a zone may take over anywhere: working ground, and ground a settlement can spare. */
    private static final Set<BlockKind> KINDS = EnumSet.of(BlockKind.BUILDING_INDUSTRIAL, BlockKind.INDUSTRIAL_YARD,
            BlockKind.FREIGHT_YARD, BlockKind.FACTORY_ROW, BlockKind.WASTELAND_RUBBLE, BlockKind.BUILDING_COMMERCIAL,
            BlockKind.DENSE_BLOCK, BlockKind.PLAZA, BlockKind.PARK);
    /** Kinds a zone may take over inside the zoning's industrial pocket: the works' housing and its guard posts. */
    private static final Set<BlockKind> IN_POCKET = EnumSet.of(BlockKind.BUILDING_RESIDENTIAL,
            BlockKind.FORTIFIED_POST);

    @Override public String id() { return ID; }

    @Override public BlockKind kind() { return BlockKind.INDUSTRIAL_ZONE; }

    @Override
    public Request request(GenContext ctx) {
        DistrictMap zoning = ctx.get(BspKeys.DISTRICT_MAP);
        if (zoning == null) return null;
        return new SpineRequest(SPEC, leaf -> eligible(leaf, zoning.themeAt(leaf.centerX(), leaf.centerY())),
                leaf -> pocket(leaf.kind, zoning.themeAt(leaf.centerX(), leaf.centerY())));
    }

    /**
     * Whether a leaf may join a zone: working or spare ground anywhere, and inside the industrial pocket its
     * housing and guard posts too (a post keeps its hardpoint as a member). The factory row's seed joins only
     * where it holds the row on its own, since a member's set piece stands on its leaf; elsewhere it is left to
     * claim its neighbour as it would have, and the zone looks along another street.
     */
    static boolean eligible(BlockLeaf leaf, MapDistrictTheme theme) {
        if (leaf.kind == BlockKind.FACTORY_ROW) return FactoryRow.fits(leaf.width(), leaf.height());
        return KINDS.contains(leaf.kind) || (theme == MapDistrictTheme.INDUSTRIAL && IN_POCKET.contains(leaf.kind));
    }

    /**
     * How much a leaf belongs in an industrial zone: the zoning's industrial pocket counts twice and a harbour
     * once; a lot already labelled working ground counts once more, and the freight yard's parcel or the factory
     * row's seed twice more, so a zone stands where those set pieces are. Spare ground outside the pocket weighs
     * nothing.
     */
    static int pocket(BlockKind kind, MapDistrictTheme theme) {
        int zoned = theme == MapDistrictTheme.INDUSTRIAL ? 2 : theme == MapDistrictTheme.HARBOR_PORT || theme == MapDistrictTheme.OUTSKIRTS ? 1 : 0;
        int working = switch (kind) {
            case FREIGHT_YARD, FACTORY_ROW -> 2;
            case BUILDING_INDUSTRIAL, INDUSTRIAL_YARD -> 1;
            default -> 0;
        };
        return zoned + working;
    }

    @Override
    public Plan plan(DistrictClaim claim, boolean[][] road, GenContext ctx) {
        if (!(claim instanceof SpineClaim.Spine spine) || spine.members().size() < MIN_MEMBERS) return null;
        Map<BlockLeaf, District.Role> roles = roles(spine);
        BlockLeaf seed = spine.members().get(0);
        for (District.Role first : new District.Role[]{District.Role.FACTORY_ROW, District.Role.FREIGHT_YARD}) {
            for (BlockLeaf leaf : spine.members()) if (roles.get(leaf) == first) seed = leaf;
        }
        return new Plan(roles, seed, narrowed(spine, roles, road));
    }

    /**
     * Each member's role. The freight-yard parcel stays a yard and the factory row's seed a row; where the
     * street has neither, the largest member that holds one takes it. The rest are warehouses where deep enough
     * and depots where not, alternating tank farm and vehicle depot along the street; where every one of them
     * would be a warehouse and there are two or more, the smallest becomes a depot. A guard post keeps its
     * hardpoint.
     */
    static Map<BlockLeaf, District.Role> roles(SpineClaim.Spine spine) {
        Map<BlockLeaf, District.Role> roles = new IdentityHashMap<>();
        for (BlockLeaf leaf : spine.members()) {
            if (leaf.kind == BlockKind.FREIGHT_YARD) roles.put(leaf, District.Role.FREIGHT_YARD);
            else if (leaf.kind == BlockKind.FACTORY_ROW) roles.put(leaf, District.Role.FACTORY_ROW);
            else if (leaf.kind == BlockKind.FORTIFIED_POST) roles.put(leaf, District.Role.SECURITY_POST);
        }
        if (!roles.containsValue(District.Role.FREIGHT_YARD)) {
            BlockLeaf yard = largest(spine, roles, leaf -> FreightYard.Size.STANDARD.fits(leaf.width(), leaf.height()));
            if (yard != null) roles.put(yard, District.Role.FREIGHT_YARD);
        }
        if (!roles.containsValue(District.Role.FACTORY_ROW)) {
            BlockLeaf row = largest(spine, roles, leaf -> FactoryRow.fits(leaf.width(), leaf.height()));
            if (row != null) roles.put(row, District.Role.FACTORY_ROW);
        }
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

    private static BlockLeaf largest(SpineClaim.Spine spine, Map<BlockLeaf, District.Role> taken,
                                     Predicate<BlockLeaf> holds) {
        BlockLeaf best = null;
        for (BlockLeaf leaf : spine.members()) {
            if (taken.containsKey(leaf) || !holds.test(leaf)) continue;
            if (best == null || leaf.area() > best.area()) best = leaf;
        }
        return best;
    }

    /**
     * The street mask with each cross street between two warehouses or depots narrowed to the narrowest street,
     * the cells beyond it given to the member past it. A cross street beside a yard or a row is left whole.
     */
    static boolean[][] narrowed(SpineClaim.Spine spine, Map<BlockLeaf, District.Role> roles, boolean[][] road) {
        boolean[][] streets = new boolean[road.length][];
        for (int x = 0; x < road.length; x++) streets[x] = road[x].clone();
        for (SpineClaim.Gap gap : spine.gaps) {
            if (!(plain(roles.get(before(spine, gap))) && plain(roles.get(after(spine, gap))))) continue;
            for (int a = gap.from() + Bsp.ROAD_WIDTH_MIN; a <= gap.to(); a++) {
                for (int c = gap.lo(); c <= gap.hi(); c++) streets[spine.x(a, c)][spine.y(a, c)] = false;
            }
        }
        return streets;
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
