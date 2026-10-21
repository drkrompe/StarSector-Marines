package com.dillon.starsectormarines.campaign.refit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The ship's exotic parts are end-game finds: AI cores, never colony items or other special items. */
class RefitExoticsTest {

    private static final Set<String> AI_CORES = Set.of(Commodities.GAMMA_CORE, Commodities.BETA_CORE, Commodities.ALPHA_CORE);

    @Test
    void everyBillLineIsAVanillaCommodity() throws IllegalAccessException {
        Set<String> commodities = new HashSet<>();
        for (Field field : Commodities.class.getFields())
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) commodities.add((String) field.get(null));
        for (RefitOrder order : RefitOrder.values())
            for (String id : order.materials().keySet()) assertTrue(commodities.contains(id), order + " bills " + id);
    }

    @Test
    void aiCoresAreOnlyAskedAboveRestored() {
        for (RefitOrder order : RefitOrder.values()) {
            boolean core = order.materials().keySet().stream().anyMatch(AI_CORES::contains);
            assertFalse(core && !order.band.atLeast(RefitBand.IMPROVED_I), order + " asks for an AI core to restore");
        }
    }
}
