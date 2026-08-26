package com.dillon.starsectormarines.tools.layerauthoring;

import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.AnimationDefinition;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.AnimationDriver;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.FrameDefinition;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.LayerDefinition;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.UnitComposition;
import com.dillon.starsectormarines.tools.layerauthoring.CompositionRenderer.RenderedLayer;
import com.dillon.starsectormarines.tools.snapshot.LayerSnapshotSuite;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthoringDocumentTest {

    @Test
    void seededDocumentValidatesAndRendersDeterministically() throws Exception {
        AuthoringDocument document = AuthoringDocument.load(Path.of("."));
        assertEquals(13, document.units().size());
        assertTrue(document.validate().isEmpty());
        assertEquals(List.of(
                        "armor-master-aegis", "armor-master-palatine",
                        "armor-master-furnace-line", "armor-master-reaver",
                        "armor-master-specter-heavy", "armor-master-bulwark-heavy",
                        "armor-master-reliquary-heavy", "armor-master-lions-mantle",
                        "armor-master-foundry-breaker"),
                document.units().stream().map(UnitComposition::id)
                        .filter(id -> id.startsWith("armor-master-")).toList());
        assertTrue(document.units().stream()
                .filter(unit -> unit.id().startsWith("armor-master-"))
                .allMatch(unit -> "marine-line".equals(unit.animationSourceId())));

        UnitComposition marine = document.units().get(0);
        assertEquals(List.of("rifle", "rocket", "anti-materiel", "smoke", "satchel"),
                marine.variants().stream().map(variant -> variant.id()).toList());
        assertEquals(List.of("idle", "aiming", "walking", "firing"),
                marine.variants().get(0).animations().stream()
                        .map(AnimationDefinition::id).toList());
        assertEquals(AnimationDriver.LOCOMOTION_PHASE,
                marine.variants().get(0).animations().get(2).driver());
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
        LayerDefinition layer = firstFrame(editable).layers().get(0);
        layer.offset(-0.321, 0.456);
        layer.scale(1.25, 0.75);
        editable.save();

        AuthoringDocument reloaded = AuthoringDocument.load(temporary);
        LayerDefinition saved = firstFrame(reloaded).layers().get(0);
        assertEquals(-0.321, saved.offsetX(), 0.000001);
        assertEquals(0.456, saved.offsetY(), 0.000001);
        assertEquals(1.25, saved.scaleX(), 0.000001);
        assertEquals(0.75, saved.scaleY(), 0.000001);
        try (var siblings = Files.list(reloaded.sourcePath().getParent())) {
            assertFalse(siblings.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void unchangedSavePreservesSourceTextExactly(@TempDir Path temporary)
            throws Exception {
        AuthoringDocument source = AuthoringDocument.load(Path.of("."));
        copyFixture(source, temporary);
        AuthoringDocument editable = AuthoringDocument.load(temporary);
        String before = Files.readString(editable.sourcePath());

        editable.save();

        assertEquals(before, Files.readString(editable.sourcePath()));
    }

    @Test
    void scalarSavePreservesFormattingAndUnrelatedDefaults(@TempDir Path temporary)
            throws Exception {
        AuthoringDocument source = AuthoringDocument.load(Path.of("."));
        copyFixture(source, temporary);
        AuthoringDocument editable = AuthoringDocument.load(temporary);
        String before = Files.readString(editable.sourcePath());
        LayerDefinition layer = firstFrame(editable).layers().get(0);
        layer.offset(-0.321, layer.offsetY());

        editable.save();

        String after = Files.readString(editable.sourcePath());
        assertEquals(before.lines().count(), after.lines().count());
        assertEquals(1L, differingLines(before, after));
        assertTrue(after.contains("\"offset\": [-0.321, -0.2333]"));
        assertFalse(after.contains("\"visible\": true"));
        assertTrue(after.contains("\"visible\": false"));
        assertTrue(after.contains("Marine — army-green line kit"));
    }

    @Test
    void batchExporterWritesOneCombinedSheetPerUnit(@TempDir Path temporary)
            throws Exception {
        new SnapshotRunner().create(new SnapshotContext(Path.of("."), Path.of(".")),
                List.of(new LayerSnapshotSuite()), temporary, false);
        Path layers = temporary.resolve("layers");
        assertTrue(Files.size(layers.resolve("marine-line-sheet.png")) > 10_000L);
        assertTrue(Files.size(layers.resolve("armor-master-specter-heavy-sheet.png"))
                > 10_000L);
        assertTrue(Files.size(layers.resolve("armor-master-foundry-breaker-sheet.png"))
                > 10_000L);
        assertTrue(Files.size(layers.resolve("mech-bulwark-sheet.png")) > 10_000L);
        assertTrue(Files.size(layers.resolve("mech-hound-sheet.png")) > 10_000L);
        assertTrue(Files.size(layers.resolve("mech-sirocco-sheet.png")) > 10_000L);
    }

    @Test
    void duplicatedFramesOwnIndependentLayerTransforms() throws Exception {
        AuthoringDocument document = AuthoringDocument.load(Path.of("."));
        var source = firstFrame(document);
        var copy = source.copy("new-frame", "New frame");
        copy.layers().get(0).offset(0.9, 0.8);

        assertFalse(source.layers().get(0).offsetX() == copy.layers().get(0).offsetX());
        assertFalse(source.layers().get(0).offsetY() == copy.layers().get(0).offsetY());
    }

    @Test
    void historyRestoresWholeDocumentChangesAndSupportsRedo() throws Exception {
        AuthoringDocument document = AuthoringDocument.load(Path.of("."));
        DocumentHistory history = new DocumentHistory();
        LayerDefinition original = firstFrame(document).layers().get(0);
        double originalX = original.offsetX();

        history.begin(document);
        original.offset(0.4321, original.offsetY());
        assertTrue(history.commit(document));
        assertTrue(history.canUndo());

        document = history.undo(document);
        assertEquals(originalX,
                firstFrame(document).layers().get(0).offsetX(), 0.000001);
        assertTrue(history.canRedo());

        document = history.redo(document);
        assertEquals(0.4321,
                firstFrame(document).layers().get(0).offsetX(), 0.000001);
    }

    @Test
    void newEditAfterUndoClearsRedoHistory() throws Exception {
        AuthoringDocument document = AuthoringDocument.load(Path.of("."));
        DocumentHistory history = new DocumentHistory();
        LayerDefinition layer = firstFrame(document).layers().get(0);

        history.begin(document);
        layer.offset(0.2, layer.offsetY());
        history.commit(document);
        document = history.undo(document);

        history.begin(document);
        layer = firstFrame(document).layers().get(0);
        layer.offset(0.3, layer.offsetY());
        history.commit(document);

        assertFalse(history.canRedo());
    }

    @Test
    void clipSamplingBlendsArticulatedThighScaleBetweenWalkKeyframes() throws Exception {
        AuthoringDocument document = AuthoringDocument.load(Path.of("."));
        UnitComposition hound = document.units().stream()
                .filter(unit -> unit.id().equals("mech-hound")).findFirst().orElseThrow();
        AnimationDefinition walking = hound.variants().get(0).animations().stream()
                .filter(animation -> animation.id().equals("walking"))
                .findFirst().orElseThrow();

        FrameDefinition midpoint = new CompositionRenderer(Path.of("."))
                .sample(walking, 0, 0.5);
        LayerDefinition leftThigh = midpoint.layers().stream()
                .filter(layer -> layer.id().equals("left-thigh")).findFirst().orElseThrow();

        assertEquals(0.3365, leftThigh.scaleY(), 0.000001);
        assertEquals(118.25, leftThigh.angleDegrees(), 0.000001);
    }

    @Test
    void armorMasterPreviewsInheritedMarineWalkingWithMasteredArt() throws Exception {
        AuthoringDocument document = AuthoringDocument.load(Path.of("."));
        UnitComposition aegis = document.units().stream()
                .filter(unit -> unit.id().equals("armor-master-aegis"))
                .findFirst().orElseThrow();
        var rifle = aegis.previewVariants().stream()
                .filter(variant -> variant.id().equals("rifle"))
                .findFirst().orElseThrow();
        AnimationDefinition walking = rifle.animations().stream()
                .filter(animation -> animation.id().equals("walking"))
                .findFirst().orElseThrow();
        FrameDefinition source = walking.frames().get(0);

        FrameDefinition preview = new CompositionRenderer(Path.of("."))
                .composeAnimationPreview(aegis, source);
        LayerDefinition sourceHead = layer(source, "head");
        LayerDefinition previewHead = layer(preview, "head");
        LayerDefinition sourceFoot = layer(source, "left-foot");
        LayerDefinition previewFoot = layer(preview, "left-foot");

        assertTrue(aegis.isInheritedPreview(rifle));
        assertEquals("graphics/battle/marine-modular-topdown/variants/armor/aegis/head.png",
                previewHead.spritePath());
        assertEquals(0.8, sourceHead.scaleX(), 0.000001);
        assertEquals(1.5, previewHead.scaleX(), 0.000001);
        assertEquals(1.5, previewHead.scaleY(), 0.000001);
        assertEquals(sourceHead.offsetY() + 0.061, previewHead.offsetY(), 0.000001);
        assertEquals(sourceFoot.spritePath(), previewFoot.spritePath());
        assertEquals(sourceFoot.offsetX(), previewFoot.offsetX(), 0.000001);
        assertEquals(sourceFoot.offsetY(), previewFoot.offsetY(), 0.000001);
    }

    @Test
    void selectionOverlayRendersAboveHigherZLayers() throws Exception {
        AuthoringDocument document = AuthoringDocument.load(Path.of("."));
        UnitComposition marine = document.units().get(0);
        FrameDefinition frame = firstFrame(document);
        CompositionRenderer renderer = new CompositionRenderer(Path.of("."));
        int size = 500;
        BufferedImage plain = renderer.renderFrame(marine, frame, size, size,
                null, true);
        BufferedImage selected = renderer.renderFrame(marine, frame, size, size,
                "primary", true);
        BufferedImage geometry = new BufferedImage(size, size,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = geometry.createGraphics();
        List<RenderedLayer> layers = renderer.renderFrame(graphics, marine, frame,
                size, size, null, false);
        graphics.dispose();

        RenderedLayer primary = layers.stream()
                .filter(layer -> layer.layer().id().equals("primary"))
                .findFirst().orElseThrow();
        Shape selectedEdge = new BasicStroke(3f).createStrokedShape(primary.outline());
        List<RenderedLayer> higherLayers = layers.stream()
                .filter(layer -> layer.layer().z() > primary.layer().z()).toList();
        int overlappedEdgePixels = 0;
        int visibleOverlayPixels = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double sampleX = x + 0.5;
                double sampleY = y + 0.5;
                boolean covered = higherLayers.stream()
                        .anyMatch(layer -> layer.outline().contains(sampleX, sampleY));
                if (!covered || !selectedEdge.contains(sampleX, sampleY)) continue;
                overlappedEdgePixels++;
                if (plain.getRGB(x, y) != selected.getRGB(x, y)) visibleOverlayPixels++;
            }
        }

        assertTrue(overlappedEdgePixels > 0);
        assertTrue(visibleOverlayPixels > 0);
    }

    private static int[] pixels(BufferedImage image) {
        return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    private static long differingLines(String before, String after) {
        String[] beforeLines = before.split("\\R", -1);
        String[] afterLines = after.split("\\R", -1);
        assertEquals(beforeLines.length, afterLines.length);
        long different = 0L;
        for (int index = 0; index < beforeLines.length; index++) {
            if (!beforeLines[index].equals(afterLines[index])) different++;
        }
        return different;
    }

    private static FrameDefinition firstFrame(AuthoringDocument document) {
        return document.units().get(0).variants().get(0).animations().get(0)
                .frames().get(0);
    }

    private static LayerDefinition layer(FrameDefinition frame, String id) {
        return frame.layers().stream().filter(layer -> layer.id().equals(id))
                .findFirst().orElseThrow();
    }

    private static void copyFixture(AuthoringDocument source, Path target) throws Exception {
        Path targetJson = target.resolve(AuthoringDocument.RELATIVE_PATH);
        Files.createDirectories(targetJson.getParent());
        Files.copy(source.sourcePath(), targetJson);
        Path sourceMod = source.projectRoot().resolve("mod");
        for (UnitComposition unit : source.units()) {
            for (var variant : unit.variants()) {
                for (var animation : variant.animations()) {
                    for (var frame : animation.frames()) {
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
    }
}
