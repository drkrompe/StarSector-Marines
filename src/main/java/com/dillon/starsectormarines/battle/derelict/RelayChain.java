package com.dillon.starsectormarines.battle.derelict;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;

/**
 * The relay chain (`fallen-hull-nouns.md`, Vocabulary): the links from each stake back to the hull through the stakes
 * between. A stake's chain is cut when the autoforge is out, when its own node is down, or when the chain of the stake
 * it runs through is cut. So cutting one node strands everything beyond it, and putting the forge out strands every
 * stake. A planted stake has no node yet; its pylon counts as standing while it stands.
 */
public final class RelayChain {

    private RelayChain() {
    }

    /**
     * The stakes whose chain is cut.
     *
     * @param parentOf   each stake's parent, by its point; {@link ClaimLattice#HULL} for the hull
     * @param nodeStands whether a stake's node (or a planted stake's pylon) stands
     * @param forgeLit   whether the autoforge still burns
     * @return the cut stakes' points, in order
     */
    public static Set<Integer> cut(Map<Integer, Integer> parentOf, IntPredicate nodeStands, boolean forgeLit) {
        Set<Integer> out = new TreeSet<>();
        for (int stake : parentOf.keySet()) {
            if (!holds(stake, parentOf, nodeStands, forgeLit)) out.add(stake);
        }
        return out;
    }

    /** Whether one stake's chain holds back to the hull. */
    public static boolean holds(int stake, Map<Integer, Integer> parentOf, IntPredicate nodeStands, boolean forgeLit) {
        if (!forgeLit) return false;
        int at = stake;
        // A chain is never longer than the stakes there are; a loop is no chain.
        for (int hops = 0; hops <= parentOf.size(); hops++) {
            if (at == ClaimLattice.HULL) return true;
            Integer parent = parentOf.get(at);
            if (parent == null || !nodeStands.test(at)) return false;
            at = parent;
        }
        return false;
    }
}
