package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tender's option list: every entry names a real catalog structure, and the keys follow the list. */
class BuildOptionTest {

    @Test
    void everyOptionNamesAStructureTheCatalogHas() {
        for (BuildOption option : BuildOption.ALL) {
            assertNotNull(TurretCatalogRegistry.requireStructure(option.structureId()), option.id());
        }
    }

    @Test
    void theListFillsAtMostTheNumberKeysWithDistinctOptions() {
        assertTrue(BuildOption.ALL.size() <= TenderPilot.OPTION_SLOTS);
        Set<String> ids = new HashSet<>();
        Set<String> structures = new HashSet<>();
        for (BuildOption option : BuildOption.ALL) {
            assertTrue(ids.add(option.id()), "duplicate id " + option.id());
            assertTrue(structures.add(option.structureId()), "duplicate structure " + option.structureId());
        }
    }

    @Test
    void numberKeysFollowTheListOrder() {
        for (int slot = 0; slot < BuildOption.ALL.size(); slot++) {
            assertSame(BuildOption.ALL.get(slot), TenderPilot.option(slot));
        }
        assertSame(BuildOption.VULCAN_TURRET, TenderPilot.option(0));
    }

    @Test
    void heavierStructuresTakeLongerAndCostNoLessThanTheVulcan() {
        for (BuildOption option : BuildOption.ALL) {
            assertTrue(option.buildSeconds() >= BuildOption.VULCAN_TURRET.buildSeconds(), option.id());
            assertTrue(option.cost() >= BuildOption.VULCAN_TURRET.cost(), option.id());
        }
    }

    @Test
    void theDebugPanelCyclesThroughTheListAndWraps() {
        int index = 0;
        for (int i = 0; i < BuildOption.ALL.size(); i++) index = DebugTender.nextOption(index);
        assertEquals(0, index);
        assertEquals(1, DebugTender.nextOption(0));
    }
}
