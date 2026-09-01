package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.tools.snapshot.SnapshotCatalog;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapshotDiscoveryTest {

    @Test
    void rootToolingRuntimeDiscoversEverySnapshotSuiteInStableOrder() {
        List<String> ids = SnapshotCatalog.discover().suites().stream()
                .map(SnapshotSuite::id)
                .toList();

        assertEquals(List.of("airfield-sortie", "armory", "deployable-cover", "durability-bars",
                "firing-line", "frontage-scene",
                "integral-system-fx", "killing-ground", "late-arrival", "layers", "mech-doctrine",
                "perception-sweep",
                "player-order", "point-defence", "prosecution-hold", "runway-sortie", "ship-decks",
                "ships-boats", "sun-shadows",
                "swarm-overkill",
                "turrets", "ui", "yield-freeze"), ids);
    }

    @Test
    void selectorChoosesMultipleSuitesInCatalogOrder() {
        List<String> ids = SnapshotCatalog.discover().select("ui,turrets").stream()
                .map(SnapshotSuite::id)
                .toList();

        assertEquals(List.of("turrets", "ui"), ids);
    }
}
