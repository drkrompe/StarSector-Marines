package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressInterior;

/**
 * What goes inside a precinct, once it has grown and claimed its ground.
 *
 * <p>A programmed precinct packs its authored footprints; a zoned one hands its
 * parcels to the ordinary labelling and fillers. That single fork is the whole
 * difference between a fortress and a town, which is the point of the model —
 * everything before it (how the roads grew, where the border landed) and after
 * it (how the ground is dressed) is shared.
 *
 * <p><b>The packer never needed a rectangle.</b> {@code FortressInterior.pack}
 * takes a buildable mask and a circulation mask and reads its extent off them,
 * so a precinct of any shape packs with no change to the packer at all. The
 * rectangle a fortress ward has today comes from its placement, not from what
 * places buildings into it — which is what makes growing the district cheap
 * rather than a rewrite.
 */
public final class PrecinctFill {

    private PrecinctFill() {}

    /**
     * The two masks a precinct's interior is packed against.
     *
     * @param buildable   its claimed ground that is not road — where buildings may stand
     * @param circulation its claimed road — what the packer must leave crossable
     */
    public record Masks(boolean[][] buildable, boolean[][] circulation) { }

    /**
     * Splits one precinct's claim into what can be built on and what must stay
     * open.
     *
     * <p>Road is circulation rather than buildable for the reason
     * {@code compound-programs.md} gives about leftovers: cut against solid
     * ground a route is the only way through, which is a warren; kept open
     * across a place that has yard around it, it is one made-up route among
     * others and a squad can leave it under fire.
     */
    public static Masks masks(int[][] claim, int[][] owner, int who, int width, int height) {
        boolean[][] buildable = new boolean[width][height];
        boolean[][] circulation = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (claim[x][y] != who) continue;
                if (owner[x][y] == GrownTrunkPlan.UNOWNED) buildable[x][y] = true;
                else circulation[x][y] = true;
            }
        }
        return new Masks(buildable, circulation);
    }

    /**
     * Packs a programmed precinct's buildings into its own claimed shape.
     *
     * @param facing which approach the interior is arranged against; a precinct
     *               faces the side its main gate is on even when it has city on
     *               every side
     * @return what was placed and what the precinct could not fit, so an
     *         under-provisioned place says so rather than being indistinguishable
     *         from a small one
     */
    public static FortressInterior.Result pack(GenContext ctx, Precinct precinct,
                                               Masks masks, TraversalAxis facing) {
        if (!precinct.isProgrammed()) {
            throw new IllegalArgumentException(
                    precinct.name() + " has no program; its parcels are zoned, not packed");
        }
        return FortressInterior.pack(ctx, masks.buildable(), masks.circulation(),
                facing, precinct.program());
    }
}
