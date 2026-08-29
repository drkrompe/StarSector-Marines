package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.bsp.CompoundFiller;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.HashSet;
import java.util.Set;

/**
 * Fills a multi-block claim with a landing site — the compact airbase, across
 * two or three adjacent city blocks and the street frames between them.
 *
 * <p>This is what the block-sized {@link BlockKind#AIRBASE_PAD} cannot be. A
 * single leaf tops out around eighteen by sixteen, which holds one berth and
 * its shed; two berths, a shed and a vehicle park inside one fence need
 * twenty-two by nineteen, and the only way to get that in a city is to claim
 * neighbours. Compounds already do exactly that, so an airbase is one more
 * thing a claim can be rather than a second claiming mechanism.
 *
 * <p><b>A claim's bounding box is not the ground it owns.</b> Members are
 * rarely a neat rectangle — two leaves offset, or three in an L — so the box
 * around them contains cells belonging to leaves that are not members, and
 * those leaves are filled by their own fillers either side of this one. Placing
 * the lot in the box put a city building through the airbase's own fence: the
 * perimeter ran across a wall, and the building carried on outside the site.
 * Nothing detected it, because both halves were individually correct.
 *
 * <p>So the lot goes on ground the claim actually holds: cells inside a member
 * leaf, plus cells inside no leaf at all, which are the street frames between
 * members and belong to nobody. Everything else is somebody's block.
 *
 * <p><b>It takes the largest size that fits and never fails.</b> A claim's
 * shape is whatever its members turned out to be, so a filler that only knew
 * the large one would paint nothing on an awkward claim — a hole in the city
 * with no error. Falling back through the sizes makes the worst case a smaller
 * base, the same bargain the seed's demotion makes a stage earlier.
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
        boolean[][] owned = ownedGround(compound, ctx);
        for (AirbaseLot.Size size : new AirbaseLot.Size[]{
                AirbaseLot.Size.PAD, AirbaseLot.Size.STRIP }) {
            for (AirbaseLot.Facing facing : byFrontage(compound, ctx)) {
                int[] at = placement(compound, owned, size, facing);
                if (at == null) continue;
                author(ctx, size, facing, at[0], at[1]);
                return;
            }
        }
    }

    /**
     * Cells this claim may build on: inside one of its member leaves, or inside
     * no leaf at all.
     *
     * <p>The second half is the street frame between members, which belongs to
     * nobody and is exactly what a compound absorbs. The cells deliberately not
     * included are those of a leaf that is not a member — another block, with
     * its own filler, which will build there whatever this one does.
     */
    private static boolean[][] ownedGround(Compound compound, GenContext ctx) {
        boolean[][] owned = new boolean[ctx.width][ctx.height];
        for (int x = compound.left; x <= compound.right; x++) {
            for (int y = compound.top; y <= compound.bottom; y++) {
                if (x < 0 || y < 0 || x >= ctx.width || y >= ctx.height) continue;
                owned[x][y] = true;
            }
        }
        Set<BlockLeaf> members = new HashSet<>(compound.members);
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        if (partition == null) return owned;
        for (BlockLeaf leaf : partition.leaves) {
            if (members.contains(leaf)) continue;
            for (int x = leaf.left; x <= leaf.right; x++) {
                for (int y = leaf.top; y <= leaf.bottom; y++) {
                    if (x < 0 || y < 0 || x >= ctx.width || y >= ctx.height) continue;
                    owned[x][y] = false;
                }
            }
        }
        return owned;
    }

    /**
     * Where the lot's reservation fits on owned ground, as its top-left cell,
     * or null when it does not fit anywhere.
     */
    private static int[] placement(Compound compound, boolean[][] owned,
                                   AirbaseLot.Size size, AirbaseLot.Facing facing) {
        int spanX = AirbaseLot.reservedSpanX(size, facing, CLAIM_CLEARANCE);
        int spanY = AirbaseLot.reservedSpanY(size, facing, CLAIM_CLEARANCE);
        int[] best = null;
        int bestOffset = Integer.MAX_VALUE;
        int centreX = (compound.left + compound.right) / 2;
        int centreY = (compound.top + compound.bottom) / 2;
        for (int x = compound.left; x + spanX - 1 <= compound.right; x++) {
            for (int y = compound.top; y + spanY - 1 <= compound.bottom; y++) {
                if (!clear(owned, x, y, spanX, spanY)) continue;
                // Nearest the middle of the claim, so the base sits in its lot
                // rather than against one edge of it.
                int offset = Math.abs(x + spanX / 2 - centreX)
                        + Math.abs(y + spanY / 2 - centreY);
                if (offset < bestOffset) {
                    bestOffset = offset;
                    best = new int[]{ x, y };
                }
            }
        }
        return best;
    }

    private static boolean clear(boolean[][] owned, int left, int top,
                                 int spanX, int spanY) {
        for (int x = left; x < left + spanX; x++) {
            for (int y = top; y < top + spanY; y++) {
                if (x < 0 || y < 0 || x >= owned.length || y >= owned[0].length) return false;
                if (!owned[x][y]) return false;
            }
        }
        return true;
    }

    /**
     * Clear ground this host reserves outside the fence: none.
     *
     * <p>A claim is bounded by the streets its member blocks front onto, so the
     * way round the base already exists. Asking for more is asking for ground
     * that is there — and it is not free: the usable ground inside a claim
     * measured exactly the large lot, so the four cells of a clearance it did
     * not need were the difference between the city getting that lot and
     * falling back to the small one on every seed.
     */
    private static final int CLAIM_CLEARANCE = 0;

    private static void author(GenContext ctx, AirbaseLot.Size size,
                               AirbaseLot.Facing facing, int reservedLeft, int reservedTop) {
        int left = reservedLeft + CLAIM_CLEARANCE;
        int top = reservedTop + CLAIM_CLEARANCE;
        int right = left + AirbaseLot.spanX(size, facing) - 1;
        int bottom = top + AirbaseLot.spanY(size, facing) - 1;

        new AirbaseLot(left, top, right, bottom, facing, size,
                LandingPad.Purpose.CIVIC_LANDING_ZONE, CLAIM_CLEARANCE).author(ctx, ctx.rng);
        ctx.pois.add(new PointOfInterest(PointOfInterest.Kind.LANDING_SITE,
                left, top, right, bottom, (left + right) / 2, (top + bottom) / 2));
    }

    /**
     * Facings in preference order: fronting into the map before fronting at its
     * edge. Every one is offered to the placement search rather than picked up
     * front, because which of them fits depends on the shape of the owned
     * ground and not on the claim's proportions.
     */
    private static AirbaseLot.Facing[] byFrontage(Compound compound, GenContext ctx) {
        AirbaseLot.Facing[] all = AirbaseLot.Facing.values().clone();
        java.util.Arrays.sort(all, (a, b) -> Integer.compare(
                score(b, compound, ctx), score(a, compound, ctx)));
        return all;
    }

    private static int score(AirbaseLot.Facing facing, Compound compound, GenContext ctx) {
        return facing.alongY()
                ? (facing.sign() > 0 ? ctx.height - compound.top : compound.top)
                : (facing.sign() > 0 ? ctx.width - compound.left : compound.left);
    }
}
