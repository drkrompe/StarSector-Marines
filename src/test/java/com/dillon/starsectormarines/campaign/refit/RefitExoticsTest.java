package com.dillon.starsectormarines.campaign.refit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The ship's exotic parts are end-game finds: AI cores and outskirts tech, never colony items. */
class RefitExoticsTest {

    private static final Set<String> AI_CORES = Set.of(Commodities.GAMMA_CORE, Commodities.BETA_CORE, Commodities.ALPHA_CORE);

    /** Vanilla's colony items: they belong in industries, not in the company's ship. */
    private static final Set<String> COLONY_ITEMS = Set.of(Items.CORRUPTED_NANOFORGE, Items.PRISTINE_NANOFORGE,
            Items.SYNCHROTRON, Items.ORBITAL_FUSION_LAMP, Items.MANTLE_BORE, Items.CATALYTIC_CORE, Items.SOIL_NANITES,
            Items.BIOFACTORY_EMBRYO, Items.FULLERENE_SPOOL, Items.PLASMA_DYNAMO, Items.CRYOARITHMETIC_ENGINE,
            Items.DRONE_REPLICATOR, Items.DEALMAKER_HOLOSUITE, Items.CORONAL_PORTAL);

    @Test
    void everyBillLineIsACommodityOrAListedSpecialItem() throws IllegalAccessException {
        Set<String> commodities = new HashSet<>();
        for (Field field : Commodities.class.getFields())
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) commodities.add((String) field.get(null));
        for (RefitOrder order : RefitOrder.values())
            for (String id : order.materials().keySet())
                assertTrue(commodities.contains(id) || RefitOrder.specialItem(id), order + " bills " + id);
    }

    @Test
    void noColonyItemGoesIntoTheShip() {
        for (RefitOrder order : RefitOrder.values())
            for (String id : order.materials().keySet()) assertFalse(COLONY_ITEMS.contains(id), order + " takes " + id);
        for (String id : RefitOrder.SPECIAL_ITEMS) assertFalse(COLONY_ITEMS.contains(id), id);
    }

    @Test
    void exoticsAreOnlyAskedAboveRestored() {
        for (RefitOrder order : RefitOrder.values()) {
            boolean exotic = order.materials().keySet().stream().anyMatch(id -> AI_CORES.contains(id) || RefitOrder.specialItem(id));
            assertFalse(exotic && !order.band.atLeast(RefitBand.IMPROVED_I), order + " asks for an exotic part to restore");
        }
    }
}
