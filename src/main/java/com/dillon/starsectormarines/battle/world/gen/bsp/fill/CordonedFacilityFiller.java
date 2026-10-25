package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.bsp.CompoundFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.District;

/**
 * The cordoned facility district's filler ({@code set-piece-districts.md}): the {@link CordonedFacilityLayout}
 * over the quarter its program planned. A compound of this kind with no planned district, which nothing makes,
 * fills as ordinary works.
 */
public final class CordonedFacilityFiller implements CompoundFiller {

    private static final BuildingIndustrialFiller WORKS = new BuildingIndustrialFiller();

    @Override
    public BlockKind kind() {
        return BlockKind.CORDONED_FACILITY;
    }

    @Override
    public void fill(Compound compound, GenContext ctx) {
        requireRoadOverlays(ctx);
        District district = District.of(compound, ctx.get(BspKeys.DISTRICTS));
        if (district == null || district.shape != District.Shape.QUARTER) {
            for (BlockLeaf member : compound.members) WORKS.fill(member, ctx);
            return;
        }
        CordonedFacilityLayout.place(ctx, district);
    }
}
