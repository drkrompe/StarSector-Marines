package com.dillon.starsectormarines.tools.layerauthoring;

import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.LayerDefinition;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.UnitComposition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthoringDocumentTest {

    @Test
    void seededDocumentValidatesAndRendersDeterministically() throws Exception {
        AuthoringDocument document = AuthoringDocument.load(Path.of("."));
        assertEquals(4, document.units().size());
        assertTrue(document.validate().isEmpty());

        UnitComposition marine = document.units().get(0);
        CompositionRenderer renderer = new CompositionRenderer(Path.of("."));
        BufferedImage first = renderer.renderSheet(marine, 360, 360);
        BufferedImage second = renderer.renderSheet(marine, 360, 360);
        int[] firstPixels = pixels(first);
        int[] secondPixels = pixels(second);

        assertTrue(Arrays.equals(firstPixels, secondPixels));
        assertTrue(Arrays.stream(firstPixels).distinct().count() > 300);
    }

    @Test
    void saveAtomicallyReplacesAndReloadsTheAuthoringJson(@TempDir Path temporary)
            throws Exception {
        AuthoringDocument source = AuthoringDocument.load(Path.of("."));
        copyFixture(source, temporary);
        AuthoringDocument editable = AuthoringDocument.load(temporary);
        LayerDefinition layer = editable.units().get(0).frames().get(0).layers().get(0);
        layer.offset(-0.321, 0.456);
        layer.scale(1.25, 0.75);
        editable.save();

        AuthoringDocument reloaded = AuthoringDocument.load(temporary);
        LayerDefinition saved = reloaded.units().get(0).frames().get(0).layers().get(0);
        assertEquals(-0.321, saved.offsetX(), 0.000001);
        assertEquals(0.456, saved.offsetY(), 0.000001);
        assertEquals(1.25, saved.scaleX(), 0.000001);
        assertEquals(0.75, saved.scaleY(), 0.000001);
        try (var siblings = Files.list(reloaded.sourcePath().getParent())) {
            assertFalse(siblings.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void batchExporterWritesOneCombinedSheetPerUnit(@TempDir Path temporary)
            throws Exception {
        LayerAuthoringWorkbench.exportAll(Path.of("."), temporary);
        assertTrue(Files.size(temporary.resolve("marine-line-sheet.png")) > 10_000L);
        assertTrue(Files.size(temporary.resolve("mech-bulwark-sheet.png")) > 10_000L);
        assertTrue(Files.size(temporary.resolve("mech-hound-sheet.png")) > 10_000L);
        assertTrue(Files.size(temporary.resolve("mech-sirocco-sheet.png")) > 10_000L);
    }

    @Test
    void duplicatedFramesOwnIndependentLayerTransforms() throws Exception {
        AuthoringDocument document = AuthoringDocument.load(Path.of("."));
        var source = document.units().get(0).frames().get(0);
        var copy = source.copy("new-frame", "New frame");
        copy.layers().get(0).offset(0.9, 0.8);

        assertFalse(source.layers().get(0).offsetX() == copy.layers().get(0).offsetX());
        assertFalse(source.layers().get(0).offsetY() == copy.layers().get(0).offsetY());
    }

    @Test
    void historyRestoresWholeDocumentChangesAndSupportsRedo() throws Exception {
        AuthoringDocument document = AuthoringDocument.load(Path.of("."));
        DocumentHistory history = new DocumentHistory();
        LayerDefinition original = document.units().get(0).frames().get(0).layers().get(0);
        double originalX = original.offsetX();

        history.begin(document);
        original.offset(0.4321, original.offsetY());
        assertTrue(history.commit(document));
        assertTrue(history.canUndo());

        document = history.undo(document);
        assertEquals(originalX,
                document.units().get(0).frames().get(0).layers().get(0).offsetX(), 0.000001);
        assertTrue(history.canRedo());

        document = history.redo(document);
        assertEquals(0.4321,
                document.units().get(0).frames().get(0).layers().get(0).offsetX(), 0.000001);
    }

    @Test
    void newEditAfterUndoClearsRedoHistory() throws Exception {
        AuthoringDocument document = AuthoringDocument.load(Path.of("."));
        DocumentHistory history = new DocumentHistory();
        LayerDefinition layer = document.units().get(0).frames().get(0).layers().get(0);

        history.begin(document);
        layer.offset(0.2, layer.offsetY());
        history.commit(document);
        document = history.undo(document);

        history.begin(document);
        layer = document.units().get(0).frames().get(0).layers().get(0);
        layer.offset(0.3, layer.offsetY());
        history.commit(document);

        assertFalse(history.canRedo());
    }

    private static int[] pixels(BufferedImage image) {
        return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    private static void copyFixture(AuthoringDocument source, Path target) throws Exception {
        Path targetJson = target.resolve(AuthoringDocument.RELATIVE_PATH);
        Files.createDirectories(targetJson.getParent());
        Files.copy(source.sourcePath(), targetJson);
        Path sourceMod = source.projectRoot().resolve("mod");
        for (UnitComposition unit : source.units()) {
            for (var frame : unit.frames()) {
                for (LayerDefinition layer : frame.layers()) {
                    Path sourceSprite = sourceMod.resolve(layer.spritePath());
                    Path targetSprite = target.resolve("mod").resolve(layer.spritePath());
                    Files.createDirectories(targetSprite.getParent());
                    if (!Files.exists(targetSprite)) Files.copy(sourceSprite, targetSprite);
                }
            }
        }
    }
}
