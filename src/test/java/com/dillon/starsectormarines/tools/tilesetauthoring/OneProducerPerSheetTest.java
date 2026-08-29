package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.json.JSONException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shipped atlas has one producer.
 *
 * <p>The failure this prevents has happened, and it is quiet. A script that
 * derives an atlas by transferring fresh colour onto the alpha of the file it is
 * about to overwrite makes that file its own input; run it after something else
 * has written the same path and it rebuilds the sheet from the export it is
 * replacing. A valid PNG, the right size, the wrong art, and nothing red at the
 * moment it happens.
 *
 * <p>Every sheet has since moved to the exporter, and the scripts that were the
 * second producer are gone. What is worth keeping is the law rather than the
 * list: an authoring document that declares blocks or a strip has said what its
 * atlas contains, and nothing under {@code art-source/} may write that same file
 * behind it.
 *
 * <p>Both halves are measured off the files. No script is named here and no
 * sheet is exempt by hand, so a new script that writes an exported sheet is
 * caught the day it is added rather than the day someone remembers this rule.
 */
public class OneProducerPerSheetTest {

    private static final Path SOURCE = Path.of("art-source", "tilesets");

    @Test
    void noScriptWritesASheetAnAuthoringDocumentExports() throws IOException, JSONException {
        Set<String> exported = exportedSheets();
        assertTrue(!exported.isEmpty(),
                "no authoring document declares an atlas, so this guard has no subject; "
                        + "either the documents moved or the export shape changed");

        List<String> clashes = new ArrayList<>();
        for (Path script : scripts()) {
            String body = Files.readString(script);
            for (String sheet : exported) {
                if (body.contains(sheet)) {
                    clashes.add(script.getFileName() + " writes " + sheet);
                }
            }
        }
        assertEquals(List.of(), clashes,
                "these sheets are exported from an authoring document, so a script that also "
                        + "writes them is a second producer: running it would overwrite the "
                        + "export, and nothing would say so");
    }

    /** The file name of every atlas an authoring document declares it produces. */
    private static Set<String> exportedSheets() throws IOException, JSONException {
        Set<String> sheets = new LinkedHashSet<>();
        for (TilesetLibrary.Sheet sheet : TilesetLibrary.scan(Path.of("").toAbsolutePath())) {
            if (sheet.document() == null) continue;
            TilesetDocument document = TilesetDocument.read(sheet.document());
            if (document.blocks.isEmpty() && !document.isStrip()) continue;
            String path = document.resolvedOutputSheet(!document.blocks.isEmpty());
            sheets.add(path.substring(path.lastIndexOf('/') + 1));
        }
        return sheets;
    }

    private static List<Path> scripts() throws IOException {
        try (Stream<Path> files = Files.list(SOURCE)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".py")).toList();
        }
    }
}
