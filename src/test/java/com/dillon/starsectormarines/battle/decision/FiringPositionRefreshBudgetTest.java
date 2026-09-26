package com.dillon.starsectormarines.battle.decision;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiringPositionRefreshBudgetTest {
    @Test void parallelReadersShareOneTickBoundAndNextTickRestoresIt() {
        FiringPositionRefreshBudget budget = new FiringPositionRefreshBudget(8);
        AtomicInteger admitted = new AtomicInteger();
        IntStream.range(0, 100).parallel().forEach(i -> {
            if (budget.acquire(1)) admitted.incrementAndGet();
        });
        assertEquals(8, admitted.get());
        assertTrue(budget.acquire(2));
    }
}
