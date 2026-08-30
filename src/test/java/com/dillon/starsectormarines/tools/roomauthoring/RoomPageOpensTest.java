package com.dillon.starsectormarines.tools.roomauthoring;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.tools.authoring.AuthoringPage;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The page opens in a process that has loaded nothing.
 *
 * <p>Written after it did not. The workbench installs no catalogs — that is the
 * game's job at application load, and a test run's job through its own extension
 * — so the page's own use of the tile catalog threw on the way in and the
 * workbench showed "Could not load Rooms" where the tool should have been.
 *
 * <p>Every test in this suite runs with the catalogs already installed, which is
 * exactly why nothing caught it. So this one takes them away first.
 */
class RoomPageOpensTest {

    private static AuthoringPageContext context(List<String> status) {
        Path project = Paths.get("").toAbsolutePath();
        return new AuthoringPageContext(project,
                Paths.get("C:/Program Files (x86)/Fractal Softworks/Starsector/starsector-core"),
                status::add, () -> { });
    }

    /**
     * With no catalog installed, the page installs what it needs rather than
     * throwing.
     *
     * <p>The catalogs are process-wide and every other test wants them, so they
     * are put back whatever happens here.
     */
    @Test
    void thePageOpensInAProcessThatHasLoadedNothing() throws Exception {
        TileRegistry tiles = TileRegistry.installed();
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        try {
            TileRegistry.install(null);
            GenMappingRegistry.install(null);

            List<String> status = new ArrayList<>();
            try (AuthoringPage page = new RoomAuthoringPage(context(status))) {
                assertNotNull(page.component(), "the page built no component");
                assertNotNull(TileRegistry.installed(),
                        "the page did not install the tile catalog it depends on");
                assertNotNull(GenMappingRegistry.installed(),
                        "the page did not install the mapping the render depends on");
                assertFalse(page.hasUnsavedChanges(), "a freshly opened page is not dirty");
            }
        } finally {
            TileRegistry.install(tiles);
            GenMappingRegistry.install(mapping);
        }
    }

    /**
     * Opening the page does not generate a ship on the way in.
     *
     * <p>Filling the room list means generating a whole deck, which takes long
     * enough to freeze the window before the page has drawn anything to explain
     * itself. The list arrives later, from its own thread.
     */
    @Test
    void openingThePageDoesNotGenerateAShipOnTheWayIn() throws Exception {
        List<String> status = new ArrayList<>();
        long before = System.nanoTime();
        try (AuthoringPage page = new RoomAuthoringPage(context(status))) {
            long millis = (System.nanoTime() - before) / 1_000_000L;
            assertTrue(millis < 2_000,
                    "opening the page took " + millis + "ms, so it is doing the ship's work "
                            + "on the thread that draws the window");
            assertNotNull(page.component());
        }
    }
}
