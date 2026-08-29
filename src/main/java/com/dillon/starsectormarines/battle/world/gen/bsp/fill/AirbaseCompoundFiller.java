package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.bsp.CompoundFiller;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

/**
 * Fills a multi-block claim with a landing site — the compact airbase, across
 * two or three adjacent city blocks and the streets between them.
 *
 * <p>This is what the block-sized {@link BlockKind#AIRBASE_PAD} cannot be. A
 * single leaf tops out around eighteen by sixteen, which holds one berth and
 * its shed; two berths, a shed and a vehicle park inside one fence need
 * twenty-two by nineteen, and the only way to get that in a city is to claim
 * neighbours. Compounds already do exactly that — a shared outer boundary, the
 * street frames between members dissolved into the interior — so an airbase is
 * one more thing a claim can be rather than a second claiming mechanism.
 *
 * <p><b>It takes the largest size the claim can actually hold, and never
 * fails.</b> A claim's rectangle is whatever its members turned out to be, not
 * what was asked for, so a filler that only knew how to lay the large one would
 * paint nothing on a claim that came up short — leaving a hole in the city with
 * no error. Falling back through the sizes means the worst case is a smaller
 * base, which is the same bargain the seed's demotion makes one stage earlier.
 *
 * <p>Scenery like its smaller sibling: civil landing pads and no airbase node,
 * so nothing flies from it and no commander gates on holding it.
 */
public final class AirbaseCompoundFiller implements CompoundFiller {

    @Override
    public BlockKind kind() {
        return BlockKind.AIRBASE_COMPOUND;
    }

    @Override
    public void fill(Compound compound, GenContext ctx) {
        for (AirbaseLot.Size size : new AirbaseLot.Size[]{
                AirbaseLot.Size.PAD, AirbaseLot.Size.STRIP }) {
            AirbaseLot.Facing facing = facingFor(compound, ctx, size);
            if (facing == null) continue;
            author(compound, ctx, size, facing);
            return;
        }
    }

    private static void author(Compound compound, GenContext ctx,
                               AirbaseLot.Size size, AirbaseLot.Facing facing) {
        int spanX = AirbaseLot.reservedSpanX(size, facing);
        int spanY = AirbaseLot.reservedSpanY(size, facing);
        int clear = size.clearance();
        // Centred in the claim, then inset by the lot's own clearance: the
        // reservation is the lot plus the ground it keeps clear outside its
        // fence, and the lot itself is what sits inside that.
        int left = compound.left + (compound.width() - spanX) / 2 + clear;
        int top = compound.top + (compound.height() - spanY) / 2 + clear;
        int right = left + AirbaseLot.spanX(size, facing) - 1;
        int bottom = top + AirbaseLot.spanY(size, facing) - 1;

        new AirbaseLot(left, top, right, bottom, facing, size,
                LandingPad.Purpose.CIVIC_LANDING_ZONE).author(ctx, ctx.rng);
        ctx.pois.add(new PointOfInterest(PointOfInterest.Kind.LANDING_SITE,
                left, top, right, bottom, (left + right) / 2, (top + bottom) / 2));
    }

    /**
     * A facing the claim can hold at this size, fronting into the map rather
     * than off its edge. Asked of the lot at every facing rather than reasoned
     * from the claim's proportions, which is only right for a lot wider than it
     * is deep and none of these are.
     */
    private static AirbaseLot.Facing facingFor(Compound compound, GenContext ctx,
                                               AirbaseLot.Size size) {
        AirbaseLot.Facing best = null;
        int bestScore = Integer.MIN_VALUE;
        for (AirbaseLot.Facing facing : AirbaseLot.Facing.values()) {
            if (compound.width() < AirbaseLot.reservedSpanX(size, facing)) continue;
            if (compound.height() < AirbaseLot.reservedSpanY(size, facing)) continue;
            int score = facing.alongY()
                    ? (facing.sign() > 0 ? ctx.height - compound.top : compound.top)
                    : (facing.sign() > 0 ? ctx.width - compound.left : compound.left);
            if (score > bestScore) {
                bestScore = score;
                best = facing;
            }
        }
        return best;
    }
}
