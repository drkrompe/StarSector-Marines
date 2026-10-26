package com.dillon.starsectormarines.campaign.refit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The ship's exotic parts are end-game finds: AI cores, never colony items or other special items. */
class RefitExoticsTest {

    private static final Set<String> AI_CORES = Set.of(Commodities.GAMMA_CORE, Commodities.BETA_CORE, Commodities.ALPHA_CORE);

    @Test
    void theOnlyItemsATierTakesAreAiCores() {
        for (RefitOrder order : RefitOrder.values())
            for (String id : order.cores().keySet()) assertTrue(AI_CORES.contains(id), order + " takes " + id);
    }

    @Test
    void aiCoresAreOnlyAskedAboveRestored() {
        for (RefitOrder order : RefitOrder.values()) {
            boolean core = order.cores().keySet().stream().anyMatch(AI_CORES::contains);
            assertFalse(core && !order.band.atLeast(RefitBand.IMPROVED_I), order + " asks for an AI core to restore");
        }
    }
}
