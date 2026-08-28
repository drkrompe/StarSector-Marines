package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code deployMod} is a {@code Sync} of the whole {@code mod/} folder, so
 * anything left inside it is copied into every install. Pre-pack inputs — raw
 * generated sheets, ImageGen masters, annotation documents — are inputs to the
 * shipped art rather than part of it, and they belong under {@code art-source/}.
 *
 * <p>This is exactly the kind of boundary that holds until someone drops a file
 * in the convenient place, so it is asserted rather than remembered.
 */
class RawArtStaysOutOfModTest {

    private static final Path MOD = Path.of("mod");
    private static final Path ART_SOURCE = Path.of("art-source");

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
    void theRawSheetsAreStillInTheRepository() throws IOException {
        // The boundary is "not shipped", not "deleted": a sheet has to stay
        // re-derivable and re-annotatable.
        assertTrue(under(ART_SOURCE, ".raw.png").size() >= 5,
                "art-source/ should hold the raw sheets that used to sit under mod/");
    }
}
