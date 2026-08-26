package com.dillon.starsectormarines.ui.retained.headless;

import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiLayout;
import com.dillon.starsectormarines.ui.retained.UiTag;
import com.dillon.starsectormarines.ui.retained.style.StyleSheet;
import com.dillon.starsectormarines.ui.retained.style.UiTheme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadlessUiRendererTest {

    @Test
    void rendersExactRetainedBoxesAndBitmapTextWithoutTheEngine() {
        UiElement button = new UiElement("action").tag(UiTag.BUTTON).text("OPEN ARMORY");
        UiElement root = new UiElement("root").layout(UiLayout.COLUMN).child(button);
        UiDocument document = new UiDocument(root).theme(new UiTheme(
                StyleSheet.parse("headless-test", """
                        :root { background-color: #080d15; font-family: body; padding: 12px; }
                        button {
                            width: 220px; height: 52px; padding: 8px 12px;
                            color: #e4eefa; background-color: #1e3045;
                            border-color: #6ed7ff; border-width: 2px; text-align: center;
                        }
                        """), Map.of("body", Fonts.INSIGNIA_LARGE)));
        HeadlessUiRenderer renderer = renderer();

        BufferedImage first = renderer.render(document, 320, 120);
        BufferedImage second = renderer.render(document, 320, 120);
        int[] firstPixels = ((DataBufferInt) first.getRaster().getDataBuffer()).getData();
        int[] secondPixels = ((DataBufferInt) second.getRaster().getDataBuffer()).getData();

        assertEquals(320, first.getWidth());
        assertEquals(120, first.getHeight());
        assertTrue(Arrays.equals(firstPixels, secondPixels));
        assertTrue(Arrays.stream(firstPixels).distinct().count() > 80,
                "boxes, borders, and the vanilla bitmap font should all be present");
    }

    @Test
    void relativeResolutionFitPreservesLayoutWhileUiScaleChangesIt() {
        UiElement lowResolutionRoot = new UiElement("low-resolution-root");
        UiDocument lowResolution = new UiDocument(lowResolutionRoot);
        UiElement scaledUiRoot = new UiElement("scaled-ui-root");
        UiDocument scaledUi = new UiDocument(scaledUiRoot);
        HeadlessUiRenderer renderer = renderer();

        BufferedImage compact = renderer.renderRelative(
                lowResolution, 160, 60, 1f, 320f, 120f);
        BufferedImage userScaled = renderer.renderRelative(
                scaledUi, 320, 120, 2f, 320f, 120f);

        assertEquals(160, compact.getWidth());
        assertEquals(320f, lowResolutionRoot.box().borderBox().width());
        assertEquals(120f, lowResolutionRoot.box().borderBox().height());
        assertEquals(160f, scaledUiRoot.box().borderBox().width());
        assertEquals(60f, scaledUiRoot.box().borderBox().height());
        assertEquals(320, userScaled.getWidth());
    }

    @Test
    void canvasSpritesShareAtlasTintAndBlendSemantics(@TempDir Path resourceRoot)
            throws Exception {
        Path asset = resourceRoot.resolve("graphics/test-atlas.png");
        Files.createDirectories(asset.getParent());
        BufferedImage atlas = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        atlas.setRGB(0, 0, new Color(240, 20, 220).getRGB());
        atlas.setRGB(1, 0, Color.WHITE.getRGB());
        ImageIO.write(atlas, "PNG", asset.toFile());

        UiElement canvas = new UiElement("canvas")
                .tag(UiTag.CANVAS)
                .canvasSize(40, 20);
        UiDocument document = new UiDocument(canvas);
        Color background = new Color(10, 10, 10);
        Color tint = new Color(120, 80, 40);
        CanvasSpriteRegion secondFrame = CanvasSpriteRegion.frame(2, 1, 1);
        document.canvases().set(canvas, context -> {
            context.fillRect(0f, 0f, 40f, 20f, background);
            context.sprite("graphics/test-atlas.png", null,
                    10f, 10f, 12f, 12f, 0f, tint,
                    secondFrame, CanvasBlend.NORMAL);
            context.sprite("graphics/test-atlas.png", null,
                    30f, 10f, 12f, 12f, 0f, tint,
                    secondFrame, CanvasBlend.ADDITIVE);
        });

        BufferedImage result = new HeadlessUiRenderer(resourceRoot).render(document, 40, 20);
        Color normal = new Color(result.getRGB(10, 10), true);
        Color additive = new Color(result.getRGB(30, 10), true);

        assertEquals(tint.getRed(), normal.getRed());
        assertEquals(tint.getGreen(), normal.getGreen());
        assertEquals(tint.getBlue(), normal.getBlue());
        assertEquals(background.getRed() + tint.getRed(), additive.getRed());
        assertEquals(background.getGreen() + tint.getGreen(), additive.getGreen());
        assertEquals(background.getBlue() + tint.getBlue(), additive.getBlue());
    }

    @Test
    void typedHostPassCanUseTheHeadlessCanvasDrain() {
        UiElement canvas = new UiElement("canvas").tag(UiTag.CANVAS).canvasSize(40, 20);
        UiDocument document = new UiDocument(canvas);
        document.canvases().set(canvas, context -> context.hostPass((viewport, alpha) -> {
            throw new AssertionError("native pass should not execute in headless rendering");
        }));
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(
                (pass, context, viewport, alpha) -> {
                    assertEquals(40f, viewport.width());
                    assertEquals(20f, viewport.height());
                    context.fillRect(0f, 0f, 40f, 20f, Color.MAGENTA);
                    return true;
                }, Path.of("mod"));

        BufferedImage result = renderer.render(document, 40, 20);

        assertEquals(Color.MAGENTA.getRGB(), result.getRGB(20, 10));
    }

    @Test
    void typedHostPassUsesThePhysicalContentBoxWithoutStretchingResolvedGeometry() {
        UiElement canvas = new UiElement("canvas").tag(UiTag.CANVAS).canvasSize(40, 20);
        UiDocument document = new UiDocument(canvas);
        document.canvases().set(canvas, context -> context.hostPass((viewport, alpha) -> {
            throw new AssertionError("native pass should not execute in headless rendering");
        }));
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(
                (pass, context, viewport, alpha) -> {
                    assertEquals(80f, viewport.width());
                    assertEquals(20f, viewport.height());
                    assertEquals(2f, viewport.scaleX());
                    assertEquals(1f, viewport.scaleY());
                    context.fillRect(10f, 5f, 10f, 10f, Color.MAGENTA);
                    return true;
                }, Path.of("mod"));

        BufferedImage result = renderer.render(document, 80, 20);

        assertEquals(Color.MAGENTA.getRGB(), result.getRGB(15, 10));
        assertEquals(0, result.getRGB(25, 10));
    }

    private static HeadlessUiRenderer renderer() {
        Path starsectorCore = Path.of(System.getProperty("starsectorDir"))
                .resolve("starsector-core");
        return new HeadlessUiRenderer(Path.of("mod"), starsectorCore);
    }
}
