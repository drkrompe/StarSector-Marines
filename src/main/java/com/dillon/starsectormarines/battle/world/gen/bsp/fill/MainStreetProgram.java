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

import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The main street program ({@code set-piece-districts.md}): a {@link SpineClaim} of four to six leaves, two or
 * three each side, of shops, homes, offices or open public ground, standing in a civic, mixed or residential pocket on
 * balance ({@link #pocket}: the zoning's civic core, mixed town or residential quarter, or lots already labelled commercial). The larger end leaf becomes the {@link District.Role#PLAZA},
 * the leaf at the other end the {@link District.Role#LANDMARK} hall, and every other member a
 * {@link District.Role#SHOPFRONT}. Its street rewrite narrows the cross streets between shops to the
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
        roles.put(plazaLow ? low : high, District.Role.PLAZA);
        BlockLeaf hall = plazaLow ? high : low;
        roles.put(hall, District.Role.LANDMARK);
        return new Plan(roles, hall, narrowed(spine, road));
    }

    /** The street mask with every cross street between shops narrowed to the narrowest street. */
    static boolean[][] narrowed(SpineClaim.Spine spine, boolean[][] road) {
        boolean[][] streets = new boolean[road.length][];
        for (int x = 0; x < road.length; x++) streets[x] = road[x].clone();
        for (SpineClaim.Gap gap : spine.gaps) {
            for (int a = gap.from() + Bsp.ROAD_WIDTH_MIN; a <= gap.to(); a++) {
                for (int c = gap.lo(); c <= gap.hi(); c++) streets[spine.x(a, c)][spine.y(a, c)] = false;
            }
        }
        return streets;
    }
}
