package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;

import java.util.List;

/**
 * The ground a district takes ({@code set-piece-districts.md}): its member leaves and the streets inside it.
 * A claim is found read-only, before anything is changed; the plan stage commits it only when its program
 * accepts it and its street rewrite keeps every entry ({@link DistrictStreets}).
 */
public interface DistrictClaim {

    /** The member leaves, in the claim's own order (along a spine, row by row in a quarter). */
    List<BlockLeaf> members();

    /**
     * Whether {@code (x, y)} is the claim's ground: a member leaf, or a street cell inside the claim. The
     * streets round its outside stay public.
     */
    boolean owns(int x, int y);

    /** The claim's bounds, inclusive: {@code {left, top, right, bottom}}. */
    int[] bounds();
}
