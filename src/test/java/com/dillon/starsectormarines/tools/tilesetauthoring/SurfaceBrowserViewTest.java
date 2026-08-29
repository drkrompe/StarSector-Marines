package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The purpose-first panel, checked by drawing it.
 *
 * <p>Compiling proves nothing about a list whose whole job is to be read. The
 * renderers here cast their values and build their own markup, so the way this
 * breaks is a panel that comes up empty or throws while painting a row — which
 * a constructor test would pass straight through. So it is painted, and the
 * picture is written out to be looked at.
 */
public class SurfaceBrowserViewTest {

    private static final Path EVIDENCE = Paths.get("build", "tileset-authoring");

    @Test
    void theAdviceSaysWhyOpeningIsUnavailable() {
        SurfaceCatalog.Candidate shipped = new SurfaceCatalog.Candidate(
                "floors.grass", SurfaceCatalog.VARIANT_POOL, "Floors_Tiles",
                null, List.of(), true);
        SurfaceCatalog.Purpose grass = new SurfaceCatalog.Purpose(
                "GRASS", SurfaceCatalog.GROUND_KIND, "floors.grass",
                SurfaceCatalog.VARIANT_POOL, List.of(shipped));

        String advice = SurfaceBrowserView.adviceFor(grass, shipped);
        assertTrue(advice.contains("no authoring document"),
                "an operator must be told why Open is dead: " + advice);

        SurfaceCatalog.Candidate authored = new SurfaceCatalog.Candidate(
                "urban.wall", "wall-3x3", "urban-tileset",
                Paths.get("art-source/tilesets/urban-tileset.tileset-authoring.json"),
                List.of(new SurfaceCatalog.Slot("doodad.urban.c3r0", "nw", true)), true);
        assertTrue(SurfaceBrowserView.adviceFor(grass, authored).contains("1 slots selected"));
    }

    @Test
    void anUnmappedSurfaceSaysSoRatherThanLookingEmpty() {
        SurfaceCatalog.Purpose snow = new SurfaceCatalog.Purpose(
                "SNOW", SurfaceCatalog.GROUND_KIND, null, null, List.of());
        assertEquals("SNOW has nothing mapped to it.",
                SurfaceBrowserView.adviceFor(snow, null));
    }

    /**
     * The real listing, painted. Catches an exception thrown from a cell
     * renderer and a panel that draws nothing, neither of which shows up in a
     * headless assertion about the model.
     */
    @Test
    void theProjectsSurfacesPaint() throws Exception {
        AtomicReference<SurfaceCatalog.Candidate> opened = new AtomicReference<>();
        SurfaceBrowserView view = new SurfaceBrowserView(opened::set);
        List<SurfaceCatalog.Purpose> purposes =
                SurfaceCatalog.scan(Paths.get("").toAbsolutePath());
        view.setPurposes(purposes);
        view.setSize(280, 460);
        layOut(view);

        BufferedImage painted = new BufferedImage(280, 460, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = painted.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }

        Files.createDirectories(EVIDENCE);
        Path out = EVIDENCE.resolve("surface-browser.png");
        ImageIO.write(painted, "PNG", out.toFile());

        assertTrue(distinctColours(painted) > 3,
                "the panel painted nothing legible — see " + out.toAbsolutePath());
        assertNotNull(purposes);
        assertFalse(purposes.isEmpty());
    }

    /**
     * Lay a component tree out with no window behind it.
     *
     * <p>{@code Container.validate()} does nothing without a native peer, so a
     * panel that has never been in a frame paints as a blank rectangle. Walking
     * the tree and calling {@code doLayout} runs the layout managers directly,
     * which is all that is missing — and it keeps the test off a display, so it
     * runs the same headless as not.
     */
    private static void layOut(Component component) {
        component.doLayout();
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                layOut(child);
            }
        }
    }

    /**
     * Picking a surface offers its candidates and arms the handoff. This is the
     * whole interaction: WALL is chosen, the wall that fills it is listed, and
     * opening it hands back the candidate whose sheet the page must load.
     */
    @Test
    void pickingASurfaceOffersItsCandidatesAndHandsOneBack() throws Exception {
        AtomicReference<SurfaceCatalog.Candidate> opened = new AtomicReference<>();
        SurfaceBrowserView view = new SurfaceBrowserView(opened::set);
        view.setPurposes(SurfaceCatalog.scan(Paths.get("").toAbsolutePath()));

        assertTrue(view.select("WALL"), "WALL must be offered");
        // Not a census - how many walls the project has is content that moves.
        // What matters is that picking a surface offers something and arms the
        // handoff on the one actually being drawn.
        assertTrue(view.shownCandidateCount() >= 1, "WALL must offer its candidates");
        SurfaceCatalog.Candidate picked = view.selectedCandidate();
        assertNotNull(picked, "the in-use candidate should come up selected");
        assertEquals("urban.wall", picked.blockId(), "the wall in use is preselected");
        assertTrue(picked.isEditable(), "and its sheet is the one the handoff opens");

        view.setSize(280, 460);
        layOut(view);
        BufferedImage painted = new BufferedImage(280, 460, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = painted.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
        Files.createDirectories(EVIDENCE);
        ImageIO.write(painted, "PNG", EVIDENCE.resolve("surface-browser-wall.png").toFile());

        assertTrue(distinctColours(painted) > 3, "the candidate list painted nothing");
    }

    private static int distinctColours(BufferedImage image) {
        Set<Integer> seen = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                seen.add(image.getRGB(x, y));
                if (seen.size() > 64) return seen.size();
            }
        }
        return seen.size();
    }
}
