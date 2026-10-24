package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlliedColumnsProgressTest {

    @Test
    void aColumnHeldAtAFiringLineHasStalledWhateverItKills() {
        // Broad Front's lane 4 (2026-10-24): a column halted a hundred cells short of its bore, shooting the swarm
        // that streams out of it, for six thousand ticks; it came no nearer however much it fought.
        AlliedColumns.Progress progress = new AlliedColumns.Progress();
        progress.reset(0);
        progress.observe(0, 140f);
        progress.observe(300, 100f);
        progress.observe(600, 99.5f);
        assertFalse(progress.stalled(600, 600), "it came forty cells nearer at tick 300");
        assertTrue(progress.stalled(900, 600), "half a cell is not ground gained");
    }

    @Test
    void aNewDestinationStartsTheColumnAfresh() {
        AlliedColumns.Progress progress = new AlliedColumns.Progress();
        progress.reset(0);
        progress.observe(0, 20f);
        progress.reset(900);
        progress.observe(900, 80f);
        assertFalse(progress.stalled(1000, 600), "farther from the new destination is not a stall on it");
    }
}
