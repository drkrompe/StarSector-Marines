package com.dillon.starsectormarines.battle.objective.optional;

import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectiveRollTest {

    private static OptionalObjective objective(String key, Set<String> needs, Set<String> excludes) {
        return new OptionalObjective(key, key, "", needs, excludes, 1, Reward.NONE, Cost.NONE,
                ctx -> (c, tick) -> Status.ACTIVE);
    }

    private static Laid piece(String name, Laid.Status status) {
        return new Laid(name, Laid.Kind.STRUCTURE, status, "", false, List.of(), List.of(), List.of());
    }

    private static List<OptionalObjective> pool(int size) {
        List<OptionalObjective> pool = new ArrayList<>();
        for (int i = 0; i < size; i++) pool.add(objective("o" + i, Set.of(), Set.of()));
        return pool;
    }

    @Test
    void sameInputsRollTheSameSet() {
        List<OptionalObjective> pool = pool(8);
        for (long seed = 0; seed < 20; seed++)
            assertEquals(ObjectiveRoll.pick(pool, LaidLayout.EMPTY, seed, 1, 5),
                    ObjectiveRoll.pick(pool, LaidLayout.EMPTY, seed, 1, 5));
    }

    @Test
    void fitsOnlyWhenEveryNeededPieceStands() {
        LaidLayout laid = new LaidLayout(List.of(piece("wreck", Laid.Status.LAID),
                piece("depot", Laid.Status.PARTIAL), piece("bridge", Laid.Status.REFUSED)));
        assertTrue(ObjectiveRoll.fits(objective("a", Set.of("wreck"), Set.of()), laid));
        assertTrue(ObjectiveRoll.fits(objective("b", Set.of("wreck", "depot"), Set.of()), laid));
        assertFalse(ObjectiveRoll.fits(objective("c", Set.of("bridge"), Set.of()), laid));
        assertFalse(ObjectiveRoll.fits(objective("d", Set.of("wreck", "tower"), Set.of()), laid));
        assertTrue(ObjectiveRoll.fits(objective("e", Set.of(), Set.of()), LaidLayout.EMPTY));
    }

    @Test
    void aNeedWithAlternativesFitsOnWhicheverStands() {
        LaidLayout laid = new LaidLayout(List.of(piece("tenement", Laid.Status.LAID),
                piece("farmstead", Laid.Status.REFUSED)));
        assertTrue(ObjectiveRoll.fits(objective("a", Set.of("farmstead|tenement"), Set.of()), laid));
        assertFalse(ObjectiveRoll.fits(objective("b", Set.of("farmstead|granary"), Set.of()), laid));
    }

    @Test
    void neverDrawsWhatDoesNotFit() {
        LaidLayout laid = new LaidLayout(List.of(piece("wreck", Laid.Status.LAID),
                piece("bridge", Laid.Status.REFUSED)));
        OptionalObjective fits = objective("fits", Set.of("wreck"), Set.of());
        OptionalObjective refused = objective("refused", Set.of("bridge"), Set.of());
        OptionalObjective missing = objective("missing", Set.of("tower"), Set.of());
        for (long seed = 0; seed < 20; seed++)
            assertEquals(List.of(fits), ObjectiveRoll.pick(List.of(refused, fits, missing), laid, seed, 0, 3));
    }

    @Test
    void countStaysInRangeAndClampsToWhatFits() {
        List<OptionalObjective> pool = pool(6);
        for (long seed = 0; seed < 50; seed++) {
            int n = ObjectiveRoll.pick(pool, LaidLayout.EMPTY, seed, 2, 4).size();
            assertTrue(n >= 2 && n <= 4, "count " + n);
            assertEquals(6, ObjectiveRoll.pick(pool, LaidLayout.EMPTY, seed, 8, 10).size());
            assertEquals(3, ObjectiveRoll.pick(pool, LaidLayout.EMPTY, seed, 3, 3).size());
        }
        assertEquals(List.of(), ObjectiveRoll.pick(pool, LaidLayout.EMPTY, 1, 0, 0));
        assertEquals(List.of(), ObjectiveRoll.pick(List.of(), LaidLayout.EMPTY, 1, 1, 3));
    }

    @Test
    void excludedPairsAreNeverDrawnTogether() {
        OptionalObjective a = objective("a", Set.of(), Set.of("b"));
        OptionalObjective b = objective("b", Set.of(), Set.of());
        OptionalObjective c = objective("c", Set.of(), Set.of());
        OptionalObjective d = objective("d", Set.of(), Set.of("c"));
        for (long seed = 0; seed < 100; seed++) {
            List<String> keys = ObjectiveRoll.pick(List.of(a, b, c, d), LaidLayout.EMPTY, seed, 4, 4)
                    .stream().map(OptionalObjective::key).toList();
            assertFalse(keys.contains("a") && keys.contains("b"), keys.toString());
            assertFalse(keys.contains("c") && keys.contains("d"), keys.toString());
            assertEquals(2, keys.size());
        }
    }
}
