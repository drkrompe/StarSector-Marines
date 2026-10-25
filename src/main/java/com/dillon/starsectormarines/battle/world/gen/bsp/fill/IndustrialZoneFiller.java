package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.bsp.CompoundFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.District;

/**
 * The industrial zone district's filler ({@code set-piece-districts.md}): the {@link IndustrialZoneLayout} over
 * the spine its program planned. A compound of this kind with no planned district, which nothing makes, fills as
 * ordinary industrial lots.
 */
public final class IndustrialZoneFiller implements CompoundFiller {

    private static final BuildingIndustrialFiller LOT = new BuildingIndustrialFiller();

    @Override
    public BlockKind kind() {
        return BlockKind.INDUSTRIAL_ZONE;
    }

    @Override
    public void fill(Compound compound, GenContext ctx) {
        requireRoadOverlays(ctx);
        District district = District.of(compound, ctx.get(BspKeys.DISTRICTS));
        if (district == null || district.shape != District.Shape.SPINE) {
            for (BlockLeaf member : compound.members) LOT.fill(member, ctx);
            return;
        }
        IndustrialZoneLayout.place(ctx, district);
    }
}
