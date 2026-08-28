package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code deployMod} is a {@code Sync} of the whole {@code mod/} folder, so
 * anything left inside it is copied into every install. Pre-pack inputs — raw
 * generated sheets, ImageGen masters, retained {@code sources/} originals,
 * annotation documents — and the build scripts that consume them are inputs to
 * the shipped art rather than part of it, and they belong under
 * {@code art-source/}.
 *
 * <p>The rule is that {@code mod/} holds the built asset and the data that
 * describes it, never the thing it was built from. Documentation of the shipped
 * art may stay beside it; documentation of how the art is produced moves with
 * the pipeline. See {@code art-source/README.md}.
 *
 * <p>This is exactly the kind of boundary that holds until someone drops a file
 * in the convenient place, so it is asserted rather than remembered.
 */
class RawArtStaysOutOfModTest {

    private static final Path MOD = Path.of("mod");
    private static final Path ART_SOURCE = Path.of("art-source");

    /** Long-edge ceiling for a shipped Armory icon, matching the armour-tier art. */
    private static final int MAX_ICON_EDGE = 512;

    /** Long-edge ceiling for a shipped battle FX texture. */
    private static final int MAX_FX_EDGE = 256;

    private static List<Path> under(Path root, String suffix) throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .toList();
        }
    }

    @Test
    void noRawSheetShipsInsideTheModFolder() throws IOException {
        assertEquals(List.of(), under(MOD, ".raw.png"),
                "raw sheets are pre-pack input; move them under art-source/");
    }

    @Test
    void noAnnotationDocumentShipsInsideTheModFolder() throws IOException {
        assertEquals(List.of(), under(MOD, ".tileset-authoring.json"),
                "an authoring document annotates a raw sheet and belongs beside it");
    }

    @Test
    void noPrePackInputDirectorySurvivesInsideTheModFolder() throws IOException {
        if (!Files.isDirectory(MOD)) return;
        try (Stream<Path> walk = Files.walk(MOD)) {
            List<Path> found = walk.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("imagegen"))
                    .sorted()
                    .toList();
            assertEquals(List.of(), found, "pre-pack input directories belong under art-source/");
        }
    }

    @Test
    void noRetainedSourceOriginalShipsInsideTheModFolder() throws IOException {
        // A sources/ directory holds the accepted originals a build script
        // normalizes into the shipped layer art. Five of them were sitting in the
        // shipped folder at 87 MB, which is larger than the art they produce.
        assertEquals(List.of(), directoriesNamed("sources"),
                "retained originals are pre-pack input; move them under art-source/");
    }

    @Test
    void noBuildScriptShipsInsideTheModFolder() throws IOException {
        // A script that produces shipped art is a build step, not an asset, even
        // when its inputs and outputs are both already inside mod/.
        assertEquals(List.of(), under(MOD, ".py"),
                "art build scripts belong under art-source/");
    }

    private static List<Path> directoriesNamed(String name) throws IOException {
        if (!Files.isDirectory(MOD)) return List.of();
        try (Stream<Path> walk = Files.walk(MOD)) {
            return walk.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().equals(name))
                    .sorted()
                    .toList();
        }
    }

    /**
     * An Armory icon is drawn between roughly 26 and 100 pixels. Authoring one
     * at the generator's native ~1254 square is a 48x oversample that nothing
     * ever sees, and the headless renderers decode every one of them into a
     * BufferedImage cache: eight of them exhausted Gradle's default worker heap
     * and surfaced as an OutOfMemoryError in three map-preview tests that have
     * nothing to do with icons. The cap is asserted here so the next authored
     * icon is refused at the source rather than three tests away.
     */
    @Test
    void armoryIconsShipAtDisplayResolution() throws IOException {
        Path icons = MOD.resolve("graphics").resolve("ui").resolve("armory");
        assertEquals(List.of(), oversized(icons, MAX_ICON_EDGE),
                "Armory icons must ship at no more than " + MAX_ICON_EDGE
                        + "px on their long edge; downscale the master before committing it");
    }

    /**
     * A battle FX texture is drawn at a fraction of a cell and is decoded into
     * the headless renderers' image cache exactly like an icon, so it gets the
     * same rule for the same reason. The masters these are derived from are
     * 1024px and live under {@code art-source/fx/}, where nothing loads them.
     */
    @Test
    void battleFxTexturesShipAtDisplayResolution() throws IOException {
        assertEquals(List.of(), oversized(MOD.resolve("graphics").resolve("fx"), MAX_FX_EDGE),
                "battle FX textures must ship at no more than " + MAX_FX_EDGE
                        + "px on their long edge; downscale the master before committing it");
    }

    private static List<String> oversized(Path directory, int maxEdge) throws IOException {
        List<String> found = new ArrayList<>();
        for (Path png : under(directory, ".png")) {
            Dimension size = pngSize(png);
            if (Math.max(size.width, size.height) > maxEdge) {
                found.add(png.getFileName() + " is " + size.width + "x" + size.height);
            }
        }
        return found;
    }

    /** Reads an IHDR without decoding the image, so the guard costs nothing. */
    private static Dimension pngSize(Path png) throws IOException {
        byte[] header = new byte[24];
        try (InputStream in = Files.newInputStream(png)) {
            if (in.readNBytes(header, 0, header.length) < header.length) {
                throw new IOException("truncated PNG: " + png);
            }
        }
        return new Dimension(intAt(header, 16), intAt(header, 20));
    }

    private static int intAt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }

    @Test
    void theRawSheetsAreStillInTheRepository() throws IOException {
        // The boundary is "not shipped", not "deleted": a sheet has to stay
        // re-derivable and re-annotatable.
        assertTrue(under(ART_SOURCE, ".raw.png").size() >= 5,
                "art-source/ should hold the raw sheets that used to sit under mod/");
    }
}
