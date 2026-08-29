package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

/**
 * Fills a city block with a small airbase — one berth, its shed, and a fence.
 *
 * <p>The block is the constraint. Measured on a Conquest map the largest leaves
 * run to about eighteen by sixteen, so this is the size of airbase that fits in
 * one without claiming its neighbours, and it is why {@link AirbaseLot.Size#STRIP}
 * reserves no clearance of its own: a block is already bounded by streets, and
 * the way past it exists whether the lot asks for it or not.
 *
 * <p><b>Scenery, not an air arm.</b> The lot is authored in full — paving,
 * markings, a worked shed, parked vehicles, gates on every side — but it
 * publishes no {@code AIRBASE} tactical node, so no commander treats it as a
 * place to fly from and no reinforcement means gates on holding it. What it
 * publishes is a landing pad, which is what makes it worth having: somewhere a
 * mission can put the player down, in a lot built to be fought over. Making it
 * a working field is a later decision and a bigger one — a capturable airbase
 * in the middle of a city is a different game from a landing site in it.
 *
 * <p>Faced onto the block's long axis so the shed's mouth and the fence's gates
 * line up with the way traffic already runs past the lot.
 */
public final class AirbasePadFiller implements BlockFiller {

    @Override
    public BlockKind kind() {
        return BlockKind.AIRBASE_PAD;
    }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        AirbaseLot.Size size = AirbaseLot.Size.STRIP;
        AirbaseLot.Facing facing = facingFor(leaf, ctx, size);
        if (facing == null) return;
        int spanX = AirbaseLot.spanX(size, facing);
        int spanY = AirbaseLot.spanY(size, facing);

        // Centred in its block. A lot pinned to one corner leaves an offcut
        // strip on two sides that is neither the base nor the street.
        int left = leaf.left + (leaf.width() - spanX) / 2;
        int top = leaf.top + (leaf.height() - spanY) / 2;
        new AirbaseLot(left, top, left + spanX - 1, top + spanY - 1, facing, size,
                LandingPad.Purpose.CIVIC_LANDING_ZONE).author(ctx, ctx.rng);

        // Published as a place, so a mission can name it and a reader of the
        // generated map can find it. It is the only handle on the site: the lot
        // is geometry, and geometry is not something a later stage can ask for.
        int cx = (left + left + spanX - 1) / 2;
        int cy = (top + top + spanY - 1) / 2;
        ctx.pois.add(new PointOfInterest(PointOfInterest.Kind.LANDING_SITE,
                left, top, left + spanX - 1, top + spanY - 1, cx, cy));
    }

    /**
     * A facing the block can actually hold, preferring one that fronts into the
     * city rather than off the edge of the map.
     *
     * <p>Chosen by fit rather than by the block's proportions. A first version
     * read the block — wider than tall, so face along the width — which is only
     * right for a lot that is wider than it is deep. This one is deeper than it
     * is wide, so that reasoning picked the orientation that did not fit and the
     * base silently failed to appear on any block shaped the wrong way. Asking
     * every facing whether it fits cannot get that backwards for any size.
     */
    private static AirbaseLot.Facing facingFor(BlockLeaf leaf, GenContext ctx,
                                               AirbaseLot.Size size) {
        AirbaseLot.Facing best = null;
        int bestScore = Integer.MIN_VALUE;
        for (AirbaseLot.Facing facing : AirbaseLot.Facing.values()) {
            if (leaf.width() < AirbaseLot.spanX(size, facing)) continue;
            if (leaf.height() < AirbaseLot.spanY(size, facing)) continue;
            // Front into the middle of the map: a frontage pointing at the map
            // edge is a base whose gates open onto nothing.
            int score = facing.alongY()
                    ? (facing.sign() > 0 ? ctx.height - leaf.top : leaf.top)
                    : (facing.sign() > 0 ? ctx.width - leaf.left : leaf.left);
            if (score > bestScore) {
                bestScore = score;
                best = facing;
            }
        }
        return best;
    }
}
