package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * One kind of district ({@code set-piece-districts.md}): main street, the industrial zone, the cordoned
 * facility. The plan stage asks it which claim it wants, finds one, and has it plan roles and streets over
 * that claim; a {@link CompoundFiller} registered under {@link #kind} then fills it. Both questions are
 * read-only: a program that cannot plan answers null and nothing has changed.
 */
public interface DistrictProgram {

    /** The program's name, e.g. {@code "main-street"}. */
    String id();

    /** The seed kind its compound carries; its filler is registered under it. */
    BlockKind kind();

    /** The claim this map should be searched for, or null when the map does not ask for this district. */
    Request request(GenContext ctx);

    /**
     * The program laid over {@code claim}, or null to refuse it. {@code road} is the street mask as it stands;
     * the plan's streets are a copy the program may have rewritten inside the claim.
     */
    Plan plan(DistrictClaim claim, boolean[][] road, GenContext ctx);

    /**
     * Called once the plan is committed, with the members already relabelled: a program that hosts a set piece
     * planned elsewhere on the map stands that one down here. Nothing by default.
     */
    default void committed(DistrictClaim claim, Plan plan, List<BlockLeaf> leaves) {
    }

    /**
     * Which claim to search for: which leaves may be members, and how well each sits in the program's zoning
     * pocket (0 outside it, more the more it belongs); a claim must stand in its pocket on balance.
     */
    sealed interface Request permits SpineRequest, QuarterRequest {
        Predicate<BlockLeaf> eligible();
        ToIntFunction<BlockLeaf> pocket();
    }

    /** Ask for a {@link SpineClaim}. */
    record SpineRequest(SpineClaim.Spec spec, Predicate<BlockLeaf> eligible, ToIntFunction<BlockLeaf> pocket)
            implements Request {
    }

    /** Ask for a {@link QuarterClaim}. */
    record QuarterRequest(QuarterClaim.Spec spec, Predicate<BlockLeaf> eligible, ToIntFunction<BlockLeaf> pocket)
            implements Request {
    }

    /**
     * A program over a claim: every member's role, the member whose leaf carries the compound's seed kind,
     * and the street mask with the claim's streets as the program wants them.
     */
    record Plan(Map<BlockLeaf, District.Role> roles, BlockLeaf seed, boolean[][] streets) {
    }
}
