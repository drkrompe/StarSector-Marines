package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.District;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictClaim;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictProgram;
import com.dillon.starsectormarines.battle.world.gen.bsp.SpineClaim;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraphBuilder;
import com.dillon.starsectormarines.battle.world.gen.road.RoadReservation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The main street program ({@code set-piece-districts.md}): a {@link SpineClaim} of four to six leaves, two or
 * three each side, of shops, homes, offices or open public ground, standing in a civic, mixed or residential pocket on
 * balance ({@link #pocket}: the zoning's civic core, mixed town or residential quarter, or lots already labelled commercial). Where the two members at one end
 * of a side hold the {@link MarketSquare} facing the street ({@link #square}), they and the cross street between
 * them are its {@link District.Role#MARKET_SQUARE} lot; otherwise the larger end leaf becomes a plain
 * {@link District.Role#PLAZA}. The leaf at the other end is the {@link District.Role#LANDMARK} hall, and every
 * other member a {@link District.Role#SHOPFRONT}. Its street rewrite narrows the cross streets between shops to the
 * narrowest street, so the main street is the widest way through and its frontage nearly unbroken; every
 * cross street still reaches the main street.
 */
public final class MainStreetProgram implements DistrictProgram {

    public static final String ID = "main-street";

    /**
     * Two or three members a side; a street at least twenty-four cells long and no wider than a primary trunk;
     * members deep enough for a pavement in front of a shop with a standable interior, and wide enough for a
     * shopfront.
     */
    static final SpineClaim.Spec SPEC = new SpineClaim.Spec(2, 3, 24, 7,
            1 + MainStreetLayout.SHOP_MIN_DEPTH, MainStreetLayout.SHOP_MIN_DEPTH);

    /** Kinds a main street may take over: shops, homes, offices and open public ground. */
    private static final Set<BlockKind> KINDS = EnumSet.of(BlockKind.BUILDING_COMMERCIAL, BlockKind.BUILDING_CIVIC,
            BlockKind.BUILDING_RESIDENTIAL, BlockKind.DENSE_BLOCK, BlockKind.PLAZA, BlockKind.PARK);

    @Override public String id() { return ID; }

    @Override public BlockKind kind() { return BlockKind.MAIN_STREET; }

    @Override
    public Request request(GenContext ctx) {
        DistrictMap zoning = ctx.get(BspKeys.DISTRICT_MAP);
        if (zoning == null) return null;
        return new SpineRequest(SPEC, leaf -> KINDS.contains(leaf.kind),
                leaf -> pocket(leaf.kind, zoning.themeAt(leaf.centerX(), leaf.centerY())));
    }

    /**
     * How much a leaf belongs on a main street: the civic core of the zoning counts twice, and the mixed town
     * and the residential quarter once each, so a high street stands among houses (owner, 2026-10-25); a lot
     * the labeller already made a shop, an office or a square counts once more wherever it is.
     */
    static int pocket(BlockKind kind, MapDistrictTheme theme) {
        int zoned = switch (theme) {
            case CIVIC -> 2;
            case MIXED, RESIDENTIAL -> 1;
            default -> 0;
        };
        boolean commercial = kind == BlockKind.BUILDING_COMMERCIAL || kind == BlockKind.BUILDING_CIVIC
                || kind == BlockKind.PLAZA;
        return zoned + (commercial ? 1 : 0);
    }

    @Override
    public Plan plan(DistrictClaim claim, boolean[][] road, GenContext ctx) {
        if (!(claim instanceof SpineClaim.Spine spine)) return null;
        BlockLeaf low = spine.members().get(0), high = low;
        for (BlockLeaf leaf : spine.members()) {
            if (spine.alongLo(leaf) < spine.alongLo(low)
                    || (spine.alongLo(leaf) == spine.alongLo(low) && leaf.area() > low.area())) low = leaf;
            if (spine.alongHi(leaf) > spine.alongHi(high)
                    || (spine.alongHi(leaf) == spine.alongHi(high) && leaf.area() > high.area())) high = leaf;
        }
        boolean plazaLow = low.area() >= high.area();
        Map<BlockLeaf, District.Role> roles = new IdentityHashMap<>();
        for (BlockLeaf leaf : spine.members()) roles.put(leaf, District.Role.SHOPFRONT);
        Square square = ctx == null ? square(spine, road, List.of(), null, plazaLow)
                : square(spine, road, ctx.landingPads, ctx.get(BspKeys.TRUNK_PLAN), plazaLow);
        if (square != null) {
            for (BlockLeaf leaf : square.ground()) roles.put(leaf, District.Role.MARKET_SQUARE);
            BlockLeaf hall = end(spine, square.ground(), !square.low());
            roles.put(hall, District.Role.LANDMARK);
            return new Plan(roles, hall, narrowed(spine, road, square.street()));
        }
        roles.put(plazaLow ? low : high, District.Role.PLAZA);
        BlockLeaf hall = plazaLow ? high : low;
        roles.put(hall, District.Role.LANDMARK);
        return new Plan(roles, hall, narrowed(spine, road, null));
    }

    /**
     * The market square's lot at one end of the spine: the two members at that end on one side and the cross
     * street between them, which stays its full width and runs through the square. {@code low} says which end.
     */
    record Square(List<BlockLeaf> ground, SpineClaim.Gap street, boolean low) { }

    /**
     * The first end pair that holds the {@link MarketSquare} facing the main street, or null: a side with three
     * members first, so the street keeps a shopfront both sides, then the plaza end before the other. Each is
     * tried over its own street rewrite and the road reservation that rewrite would draw, given the trunk plan
     * (without one every shop is taken to stand); the filler asks again over the finished streets and falls
     * back to a plaza where the square no longer stands.
     */
    static Square square(SpineClaim.Spine spine, boolean[][] road, List<LandingPad> pads, TrunkPlan.Plan trunks,
                         boolean plazaLow) {
        List<Square> candidates = new ArrayList<>();
        for (boolean low : new boolean[]{plazaLow, !plazaLow}) {
            for (List<BlockLeaf> side : List.of(spine.near, spine.far)) {
                if (side.size() < 2) continue;
                BlockLeaf end = low ? side.get(0) : side.get(side.size() - 1);
                BlockLeaf next = low ? side.get(1) : side.get(side.size() - 2);
                candidates.add(new Square(List.of(end, next), between(spine, end, next), low));
            }
        }
        candidates.sort(Comparator.comparingInt(sq -> spine.onNearSide(sq.ground().get(0))
                ? -spine.near.size() : -spine.far.size()));
        for (Square candidate : candidates) {
            boolean[][] streets = narrowed(spine, road, candidate.street());
            boolean[][] reserved = trunks == null ? null
                    : RoadReservation.mask(RoadGraphBuilder.build(streets, trunks), road.length, road[0].length);
            if (MainStreetLayout.chooseSquare(spine, candidate.ground(), streets, reserved, road.length,
                    road[0].length, pads) != null) return candidate;
        }
        return null;
    }

    /** The cross street between two neighbouring members on one side, or null. */
    private static SpineClaim.Gap between(SpineClaim.Spine spine, BlockLeaf a, BlockLeaf b) {
        int from = Math.min(spine.alongHi(a), spine.alongHi(b)), to = Math.max(spine.alongLo(a), spine.alongLo(b));
        for (SpineClaim.Gap gap : spine.gaps) {
            if (gap.near() == spine.onNearSide(a) && gap.from() > from && gap.to() < to) return gap;
        }
        return null;
    }

    /** The member reaching furthest towards one end of the street, leaving out {@code taken}. */
    private static BlockLeaf end(SpineClaim.Spine spine, List<BlockLeaf> taken, boolean low) {
        BlockLeaf best = null;
        for (BlockLeaf leaf : spine.members()) {
            if (taken.contains(leaf)) continue;
            if (best == null) { best = leaf; continue; }
            int at = low ? -spine.alongLo(leaf) : spine.alongHi(leaf);
            int bestAt = low ? -spine.alongLo(best) : spine.alongHi(best);
            if (at > bestAt || (at == bestAt && leaf.area() > best.area())) best = leaf;
        }
        return best;
    }

    /**
     * The street mask with every cross street between shops narrowed to the narrowest street, except
     * {@code keep}, the street through the market square, or none where it is null.
     */
    static boolean[][] narrowed(SpineClaim.Spine spine, boolean[][] road, SpineClaim.Gap keep) {
        boolean[][] streets = new boolean[road.length][];
        for (int x = 0; x < road.length; x++) streets[x] = road[x].clone();
        for (SpineClaim.Gap gap : spine.gaps) {
            if (gap.equals(keep)) continue;
            for (int a = gap.from() + Bsp.ROAD_WIDTH_MIN; a <= gap.to(); a++) {
                for (int c = gap.lo(); c <= gap.hi(); c++) streets[spine.x(a, c)][spine.y(a, c)] = false;
            }
        }
        return streets;
    }
}
