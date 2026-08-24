package com.dillon.starsectormarines.tools.authoring;

import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthoringPageCatalogTest {

    @Test
    void ordersProvidersAndRejectsInvalidMetadata() {
        AuthoringPageCatalog catalog = new AuthoringPageCatalog(List.of(
                provider("turrets", "Turrets"), provider("armory", "Armory")));

        assertEquals(List.of("armory", "turrets"), catalog.providers().stream()
                .map(AuthoringPageProvider::id)
                .toList());
        assertThrows(UnsupportedOperationException.class,
                () -> catalog.providers().add(provider("ui", "UI")));
        assertThrows(IllegalArgumentException.class,
                () -> new AuthoringPageCatalog(List.of(
                        provider("turrets", "Turrets"), provider("turrets", "Other"))));
        assertThrows(IllegalArgumentException.class,
                () -> new AuthoringPageCatalog(List.of(provider("Not Valid", "Invalid"))));
        assertThrows(IllegalArgumentException.class,
                () -> new AuthoringPageCatalog(List.of(provider("turrets", " "))));
    }

    @Test
    void contextNormalizesPathsAndRelaysHostCallbacks() {
        List<String> statuses = new ArrayList<>();
        boolean[] changed = {false};
        AuthoringPageContext context = new AuthoringPageContext(
                Path.of("project/..", "project"), Path.of("core", "."),
                statuses::add, () -> changed[0] = true);

        assertTrue(context.projectRoot().isAbsolute());
        assertTrue(context.starsectorCoreRoot().isAbsolute());
        context.reportStatus("Saved turrets");
        context.stateChanged();

        assertEquals(List.of("Saved turrets"), statuses);
        assertTrue(changed[0]);
    }

    @Test
    void pageDefaultsAreCleanAndClosable() throws Exception {
        AuthoringPage page = new AuthoringPage() {
            @Override
            public JComponent component() {
                return new JPanel();
            }
        };

        assertFalse(page.hasUnsavedChanges());
        page.close();
    }

    private static AuthoringPageProvider provider(String id, String label) {
        return new AuthoringPageProvider() {
            @Override public String id() { return id; }
            @Override public String label() { return label; }
            @Override public AuthoringPage create(AuthoringPageContext context) {
                return new AuthoringPage() {
                    @Override public JComponent component() { return new JPanel(); }
                };
            }
        };
    }
}
