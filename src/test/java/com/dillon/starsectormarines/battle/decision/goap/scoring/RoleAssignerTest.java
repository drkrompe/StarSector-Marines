package com.dillon.starsectormarines.battle.decision.goap.scoring;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standalone tests for {@link RoleAssigner}. Pure generics over a trivial
 * candidate record — no battle-sim coupling.
 */
public class RoleAssignerTest {

    private record Cand(String id, float anchorAffinity, float moverAffinity) {}

    @Test
    public void picksExpectedPairAcrossTwoSlots() {
        Cand alice = new Cand("alice", 0.9f, 0.1f);
        Cand bob   = new Cand("bob",   0.2f, 0.8f);
        Cand carl  = new Cand("carl",  0.3f, 0.3f);
        Cand dawn  = new Cand("dawn",  0.4f, 0.5f);
        List<Cand> pool = List.of(alice, bob, carl, dawn);

        List<RoleAssigner.Slot<Cand>> slots = List.of(
                new RoleAssigner.Slot<>("anchor", 1, c -> c.anchorAffinity),
                new RoleAssigner.Slot<>("mover",  1, c -> c.moverAffinity)
        );

        Map<String, List<Cand>> result = RoleAssigner.assign(pool, slots);

        assertEquals(List.of(alice), result.get("anchor"));
        assertEquals(List.of(bob),   result.get("mover"));
    }

    @Test
    public void swapPassFixesSuboptimalGreedy() {
        // Hand-crafted to force the greedy pick to be wrong.
        //
        // Slot priority: "alpha" has a higher mean (0.55) than "beta" (0.45),
        // so greedy fills alpha first. Greedy picks x for alpha (its highest
        // alpha score), forcing beta to take y (the lone remaining candidate).
        //
        //   greedy : alpha=x (1.0), beta=y (0.4)   total = 1.4
        //   optimal: alpha=y (0.7), beta=x (0.9)   total = 1.6
        //
        // The swap pass must detect (1.0 + 0.4) -> (0.7 + 0.9) is an
        // improvement and execute the swap.
        Cand x = new Cand("x", 1.0f, 0.9f);
        Cand y = new Cand("y", 0.7f, 0.4f);
        List<Cand> pool = List.of(x, y);

        List<RoleAssigner.Slot<Cand>> slots = List.of(
                new RoleAssigner.Slot<>("alpha", 1, c -> c.anchorAffinity),
                new RoleAssigner.Slot<>("beta",  1, c -> c.moverAffinity)
        );

        Map<String, List<Cand>> result = RoleAssigner.assign(pool, slots);

        assertEquals(List.of(y), result.get("alpha"), "swap pass should hand alpha=y");
        assertEquals(List.of(x), result.get("beta"),  "swap pass should hand beta=x");

        float totalScore =
                result.get("alpha").get(0).anchorAffinity
                + result.get("beta").get(0).moverAffinity;
        assertTrue(totalScore > 1.4f, "swap should beat the greedy total (1.4); got " + totalScore);
        assertEquals(1.6f, totalScore, 1e-5f);
    }

    @Test
    public void slotCountCappedByPoolSize() {
        Cand a = new Cand("a", 1f, 0f);
        Cand b = new Cand("b", 0.5f, 0f);
        List<Cand> pool = List.of(a, b);

        List<RoleAssigner.Slot<Cand>> slots = List.of(
                new RoleAssigner.Slot<>("anchor", 5, c -> c.anchorAffinity)
        );

        Map<String, List<Cand>> result = RoleAssigner.assign(pool, slots);
        assertEquals(List.of(a, b), result.get("anchor"));
    }

    @Test
    public void emptyInputsReturnEmptyBuckets() {
        List<RoleAssigner.Slot<Cand>> slots = List.of(
                new RoleAssigner.Slot<>("anchor", 1, c -> c.anchorAffinity)
        );
        Map<String, List<Cand>> result = RoleAssigner.assign(List.of(), slots);
        assertEquals(List.of(), result.get("anchor"));
    }
}
