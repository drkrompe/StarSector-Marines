package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.bsp.District;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictClaim;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictProgram;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictStreets;
import com.dillon.starsectormarines.battle.world.gen.bsp.QuarterClaim;
import com.dillon.starsectormarines.battle.world.gen.bsp.SpineClaim;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plans the districts ({@code set-piece-districts.md}) after the leaves are labelled and before compound
 * claims and the road graph. For each program the map asks for, it finds the claim the program wants among
 * the leaves no one has taken, has the program plan it, and commits the plan only if its street rewrite keeps
 * every way in ({@link DistrictStreets#keepsEntries}) and leaves the trunks alone. Committing relabels the
 * members (the seed to the program's kind, the rest {@link BlockKind#COMPOUND_MEMBER}), writes the rewritten
 * streets back into the road mask the road graph is built from, and publishes the district under
 * {@link BspKeys#DISTRICTS}, where compound claiming takes its compound. Everything before the commit is
 * read-only, and nothing here draws from the map's random stream.
 */
public final class DistrictPlanStage implements GenStage {

    private static final Logger LOG = Logger.getLogger(DistrictPlanStage.class);

    private final List<DistrictProgram> programs;

    public DistrictPlanStage(List<DistrictProgram> programs) {
        this.programs = List.copyOf(programs);
    }

    @Override
    public void run(GenContext ctx) {
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        TrunkPlan.Plan trunks = ctx.get(BspKeys.TRUNK_PLAN);
        if (partition == null || trunks == null) return;
        for (DistrictProgram program : programs) {
            DistrictProgram.Request request = program.request(ctx);
            if (request == null) continue;
            DistrictClaim claim = find(request, partition);
            if (claim == null) continue;
            DistrictProgram.Plan plan = program.plan(claim, partition.roadCells, ctx);
            if (plan == null) continue;
            if (!DistrictStreets.keepsEntries(claim, partition.roadCells, plan.streets())
                    || touchesTrunk(claim, partition.roadCells, plan.streets(), trunks)) {
                LOG.debug("DistrictPlanStage: " + program.id() + " refused its street rewrite");
                continue;
            }
            commit(ctx, program, claim, plan, partition, trunks);
        }
    }

    private static DistrictClaim find(DistrictProgram.Request request, Bsp.Partition partition) {
        if (request instanceof DistrictProgram.SpineRequest spine) {
            return SpineClaim.find(partition.leaves, partition.roadCells, spine.eligible(), spine.pocket(),
                    spine.spec());
        }
        DistrictProgram.QuarterRequest quarter = (DistrictProgram.QuarterRequest) request;
        return QuarterClaim.find(partition.leaves, partition.roadCells, quarter.eligible(), quarter.pocket(),
                quarter.spec());
    }

    /** Whether the plan changes a street cell inside a trunk's band. */
    private static boolean touchesTrunk(DistrictClaim claim, boolean[][] road, boolean[][] after,
                                        TrunkPlan.Plan trunks) {
        int[] b = claim.bounds();
        for (int y = b[1]; y <= b[3]; y++) {
            for (int x = b[0]; x <= b[2]; x++) {
                if (!claim.owns(x, y) || road[x][y] == after[x][y]) continue;
                for (TrunkPlan.TrunkSegment t : trunks.trunks) {
                    if (x >= t.left && x <= t.right && y >= t.top && y <= t.bottom) return true;
                }
            }
        }
        return false;
    }

    private static void commit(GenContext ctx, DistrictProgram program, DistrictClaim claim,
                               DistrictProgram.Plan plan, Bsp.Partition partition, TrunkPlan.Plan trunks) {
        DistrictStreets.apply(claim, plan.streets(), partition.roadCells);
        if (trunks.roadCells != partition.roadCells) DistrictStreets.apply(claim, plan.streets(), trunks.roadCells);
        List<BlockLeaf> members = new ArrayList<>();
        members.add(plan.seed());
        for (BlockLeaf leaf : claim.members()) {
            if (leaf == plan.seed()) continue;
            leaf.kind = BlockKind.COMPOUND_MEMBER;
            members.add(leaf);
        }
        plan.seed().kind = program.kind();
        Map<BlockLeaf, Compound.Role> none = new IdentityHashMap<>();
        Compound compound = new Compound(program.kind(), plan.seed(), members, none, null);
        Map<BlockLeaf, District.Role> roles = new IdentityHashMap<>(plan.roles());
        District.Shape shape = claim instanceof SpineClaim.Spine ? District.Shape.SPINE : District.Shape.QUARTER;
        List<District> districts = ctx.get(BspKeys.DISTRICTS);
        districts = districts == null ? new ArrayList<>() : new ArrayList<>(districts);
        districts.add(new District(program.id(), shape, claim, compound, roles));
        ctx.put(BspKeys.DISTRICTS, districts);
        LOG.debug("DistrictPlanStage: " + program.id() + " on " + claim);
    }
}
