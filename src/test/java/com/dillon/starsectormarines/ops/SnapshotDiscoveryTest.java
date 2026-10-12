package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.tools.snapshot.SnapshotCatalog;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotDiscoveryTest {

    @Test
    void rootToolingRuntimeDiscoversEverySnapshotSuiteInStableOrder() {
        List<String> ids = SnapshotCatalog.discover().suites().stream()
                .map(SnapshotSuite::id)
                .toList();

        // Every suite another session registers lands here, so an exhaustive list went stale
        // with each one. What discovery owes is a stable (sorted), duplicate-free catalog
        // that still contains the long-standing suites.
        assertEquals(ids.stream().sorted().toList(), ids, "catalog order is sorted by id");
        assertEquals(ids.size(), ids.stream().distinct().count(), "suite ids are unique");
        assertTrue(ids.containsAll(List.of("airfield-sortie", "armory", "firing-line", "layers",
                "mech-doctrine", "player-order", "ship-decks", "turrets", "ui", "yield-freeze")), ids.toString());
    }

    @Test
    void selectorChoosesMultipleSuitesInCatalogOrder() {
        List<String> ids = SnapshotCatalog.discover().select("ui,turrets").stream()
                .map(SnapshotSuite::id)
                .toList();

        assertEquals(List.of("turrets", "ui"), ids);
    }
}
