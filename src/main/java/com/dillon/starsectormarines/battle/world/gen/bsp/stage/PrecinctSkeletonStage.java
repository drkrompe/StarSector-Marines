package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctAllowance;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctArtery;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctClaim;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctInterconnect;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 1a alternative — grows several places at once instead of one.
 *
 * <p>The skeleton stage a {@code PrecinctPlan} needs. It grows every precinct
 * through one shared frontier, works out what ground each of them holds, and
 * makes sure any walled one has a way out — then hands downstream exactly what
 * {@link GrownTrunkSkeletonStage} does, an ordinary {@link TrunkPlan.Plan}
 * under {@link BspKeys#TRUNK_PLAN}, so the partition, zoning and fill stages
 * are untouched by any of this.
 *
 * <p>Ground painting is that stage's, called rather than copied: a precinct's
 * roads are trunks and want the same flanks, crossings and hinterland
 * treatment. What is new is only that there is more than one place.
 */
public final class PrecinctSkeletonStage implements GenStage {

    private final PrecinctPlan plan;

    public PrecinctSkeletonStage(PrecinctPlan plan) {
        this.plan = plan;
    }

    @Override
    public void run(GenContext ctx) {
        CellTopology topology = ctx.topology;
        GrownTrunkPlan.Grown grown =
                GrownTrunkPlan.grow(ctx.width, ctx.height, ctx.rng, plan.seeds());

        int[] allowance =
                PrecinctAllowance.derive(plan.precincts(), grown.owner(), ctx.width, ctx.height);
        int[][] claim = PrecinctClaim.byKind(plan.precincts())
                .assign(grown.owner(), ctx.width, ctx.height, allowance);

        // A walled place owes a way out, and growth does not always leave one.
        // Done before anything is painted so a carved artery is ordinary road
        // to every stage after this one.
        for (int i = 0; i < plan.precincts().size(); i++) {
            Precinct precinct = plan.precincts().get(i);
            if (precinct.boundary() != Precinct.Boundary.WALLED) continue;
            PrecinctArtery.ensure(claim, grown.owner(), i,
                    precinct.seedX(), precinct.seedY(), ctx.width, ctx.height);
        }

        // Separately grown places share a road network only by accident.
        // Measured over four derived maps, three came out whole and one came
        // out in three pieces, which is the kind of intermittent structural
        // fault that has to be solved for rather than hoped about.
        PrecinctInterconnect.weld(grown.owner(), ctx.width, ctx.height);

        List<TrunkPlan.TrunkSegment> trunks = grown.plan().trunks;
        boolean[][] trunkPainted = new boolean[ctx.width][ctx.height];
        for (TrunkPlan.TrunkSegment trunk : trunks) {
            GrownTrunkSkeletonStage.paintTrunkGround(topology, trunkPainted, trunk);
        }
        GrownTrunkSkeletonStage.paintCrossings(topology, trunks);
        GrownTrunkSkeletonStage.paintHinterland(topology, trunkPainted, grown.hinterland());

        // The partition must be handed each place's own parcels, not the whole
        // map's. Grown returns sub-rects decomposed from frontage across the
        // entire map, so used as-is every cell near any road becomes a parcel
        // and the four places render as one continuous city with a ragged edge
        // - measured, 616 points of interest and no visible boundary between a
        // town, a garrison and two hamlets.
        List<TrunkPlan.SubRect> parcels = parcels(plan, claim, grown.owner(), ctx);
        List<TrunkPlan.SubRect> hinterland = hinterland(claim, grown.owner(), ctx);

        ctx.put(BspKeys.TRUNK_PLAN, new TrunkPlan.Plan(grown.plan().roadCells, parcels,
                trunks, grown.plan().intersection, ctx.width, ctx.height));
        ctx.put(BspKeys.HINTERLAND, hinterland);
        ctx.put(BspKeys.PRECINCTS, plan);
        ctx.put(BspKeys.PRECINCT_CLAIM, claim);
    }

    /**
     * The parcels the partition may cut, which are the zoned precincts' own
     * ground and nothing else.
     *
     * <p>A programmed precinct is left out on purpose: its interior is packed
     * from authored footprints rather than subdivided, and handing it to BSP
     * would fill it with ordinary city before the packer ever saw it.
     */
    private static List<TrunkPlan.SubRect> parcels(PrecinctPlan plan, int[][] claim,
                                                   int[][] owner, GenContext ctx) {
        List<TrunkPlan.SubRect> out = new ArrayList<>();
        for (int i = 0; i < plan.precincts().size(); i++) {
            if (plan.precincts().get(i).isProgrammed()) continue;
            GrownTrunkPlan.decompose(
                    blockedOutside(claim, owner, i, ctx.width, ctx.height),
                    ctx.width, ctx.height, out);
        }
        return out;
    }

    /** Open country: what no precinct claimed, dressed rather than built on. */
    private static List<TrunkPlan.SubRect> hinterland(int[][] claim, int[][] owner,
                                                      GenContext ctx) {
        List<TrunkPlan.SubRect> out = new ArrayList<>();
        GrownTrunkPlan.decompose(
                blockedOutside(claim, owner, GrownTrunkPlan.UNOWNED, ctx.width, ctx.height),
                ctx.width, ctx.height, out);
        return out;
    }

    /** Everything that is not this owner's unroaded ground, for the decomposer. */
    private static boolean[][] blockedOutside(int[][] claim, int[][] owner, int who,
                                              int width, int height) {
        boolean[][] blocked = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                blocked[x][y] = claim[x][y] != who
                        || owner[x][y] != GrownTrunkPlan.UNOWNED;
            }
        }
        return blocked;
    }
}
