package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.bsp.CompoundFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.District;

/**
 * The main street district's filler ({@code set-piece-districts.md}): the {@link MainStreetLayout} over the
 * spine its program planned. A compound of this kind with no planned district, which nothing makes, fills as
 * ordinary shops.
 */
public final class MainStreetFiller implements CompoundFiller {

    private static final BuildingCommercialFiller SHOP = new BuildingCommercialFiller();

    @Override
    public BlockKind kind() {
        return BlockKind.MAIN_STREET;
    }

    @Override
    public void fill(Compound compound, GenContext ctx) {
        requireRoadOverlays(ctx);
        District district = District.of(compound, ctx.get(BspKeys.DISTRICTS));
        if (district == null || district.shape != District.Shape.SPINE) {
            for (BlockLeaf member : compound.members) SHOP.fill(member, ctx);
            return;
        }
        MainStreetLayout.place(ctx, district);
    }
}
