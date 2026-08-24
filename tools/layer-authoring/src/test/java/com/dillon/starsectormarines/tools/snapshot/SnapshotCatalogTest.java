package com.dillon.starsectormarines.tools.snapshot;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotCatalogTest {

    @Test
    void discoversBuiltInLayerSuiteAndSelectsInStableCatalogOrder() {
        SnapshotCatalog discovered = SnapshotCatalog.discover();
        assertTrue(discovered.suites().stream().anyMatch(suite -> suite.id().equals("layers")));

        SnapshotCatalog catalog = new SnapshotCatalog(List.of(
                suite("turrets"), suite("armory"), suite("ui")));
        assertEquals(List.of("armory", "turrets", "ui"), ids(catalog.suites()));
        assertEquals(List.of("armory", "ui"), ids(catalog.select("ui, armory")));
        assertEquals(ids(catalog.suites()), ids(catalog.select("all")));
    }

    @Test
    void rejectsDuplicateInvalidAndUnknownSuiteIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new SnapshotCatalog(List.of(suite("ui"), suite("ui"))));
        assertThrows(IllegalArgumentException.class,
                () -> new SnapshotCatalog(List.of(suite("Not Valid"))));
        SnapshotCatalog catalog = new SnapshotCatalog(List.of(suite("layers")));
        assertThrows(IllegalArgumentException.class, () -> catalog.select("turrets"));
    }

    private static SnapshotSuite suite(String id) {
        return new SnapshotSuite() {
            @Override public String id() { return id; }
            @Override public String label() { return id; }
            @Override public List<SnapshotArtifact> render(SnapshotContext context) {
                return List.of(new SnapshotArtifact("sample.png",
                        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)));
            }
        };
    }

    private static List<String> ids(List<SnapshotSuite> suites) {
        return suites.stream().map(SnapshotSuite::id).toList();
    }
}
