package com.dillon.starsectormarines.tools.layerauthoring;

import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.AnimationDefinition;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.AppearanceVariant;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.FrameDefinition;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.LayerDefinition;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.UnitComposition;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic Java2D renderer shared by the editor canvas and sheet exporter. */
public final class CompositionRenderer {

    private static final Color BACKGROUND = new Color(0x08, 0x0F, 0x18);
    private static final Color GRID = new Color(0x1B, 0x2A, 0x3A);
    private static final Color AXIS = new Color(0x3C, 0x5E, 0x78);
    private static final Color SELECTED = new Color(0xF1, 0xB8, 0x54);
    private final Path modRoot;
    private final Map<String, BufferedImage> images = new LinkedHashMap<>();

    public CompositionRenderer(Path projectRoot) {
        modRoot = projectRoot.resolve("mod").toAbsolutePath().normalize();
    }

    public BufferedImage renderFrame(UnitComposition unit, FrameDefinition frame,
                                     int width, int height, String selectedLayer,
                                     boolean guides) {
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        configure(graphics);
        renderFrame(graphics, unit, frame, width, height, selectedLayer, guides);
        graphics.dispose();
        return output;
    }

    public List<RenderedLayer> renderFrame(Graphics2D graphics, UnitComposition unit,
                                           FrameDefinition frame, int width, int height,
                                           String selectedLayer, boolean guides) {
        graphics.setColor(BACKGROUND);
        graphics.fillRect(0, 0, width, height);
        double pixelsPerUnit = pixelsPerUnit(width, height);
        double originX = width * 0.5;
        double originY = height * 0.54;
        if (guides) drawGuides(graphics, width, height, originX, originY, pixelsPerUnit);

        List<LayerDefinition> sorted = new ArrayList<>(frame.layers());
        sorted.sort(Comparator.comparingInt(LayerDefinition::z));
        List<RenderedLayer> rendered = new ArrayList<>();
        RenderedLayer selected = null;
        for (LayerDefinition layer : sorted) {
            if (!layer.visible()) continue;
            BufferedImage image = image(layer.spritePath());
            AffineTransform transform = transform(unit, layer, image,
                    originX, originY, pixelsPerUnit);
            graphics.drawImage(image, transform, null);
            Shape outline = transform.createTransformedShape(
                    new java.awt.Rectangle(0, 0, image.getWidth(), image.getHeight()));
            RenderedLayer renderedLayer = new RenderedLayer(layer, outline);
            rendered.add(renderedLayer);
            if (layer.id().equals(selectedLayer)) {
                selected = renderedLayer;
            }
        }
        if (selected != null) {
            drawSelectionOverlay(graphics, selected, originX, originY, pixelsPerUnit);
        }
        return rendered;
    }

    public BufferedImage renderSheet(UnitComposition unit, int cellWidth, int cellHeight) {
        List<SheetFrame> frames = new ArrayList<>();
        for (AppearanceVariant variant : unit.variants()) {
            for (AnimationDefinition animation : variant.animations()) {
                for (FrameDefinition frame : animation.frames()) {
                    frames.add(new SheetFrame(variant, animation, frame));
                }
            }
        }
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(frames.size())));
        int rows = (frames.size() + columns - 1) / columns;
        int labelHeight = 30;
        BufferedImage sheet = new BufferedImage(columns * cellWidth,
                rows * (cellHeight + labelHeight), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = sheet.createGraphics();
        configure(graphics);
        graphics.setColor(new Color(0x05, 0x09, 0x0E));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        for (int index = 0; index < frames.size(); index++) {
            SheetFrame item = frames.get(index);
            FrameDefinition frame = item.frame();
            int x = index % columns * cellWidth;
            int y = index / columns * (cellHeight + labelHeight);
            graphics.setColor(new Color(0xD9, 0xE8, 0xF1));
            graphics.drawString(item.variant().label() + " / " + item.animation().label()
                            + " / " + frame.label() + "  ·  " + frame.durationMs() + " ms",
                    x + 10, y + 20);
            BufferedImage cell = renderFrame(unit, frame, cellWidth, cellHeight,
                    null, false);
            graphics.drawImage(cell, x, y + labelHeight, null);
        }
        graphics.dispose();
        return sheet;
    }

    /** Samples one keyframe transition using smooth transform interpolation. */
    public FrameDefinition sample(AnimationDefinition animation, int frameIndex,
                                  double progress) {
        if (animation.frames().isEmpty()) {
            throw new IllegalArgumentException("Animation has no keyframes");
        }
        int currentIndex = Math.max(0, Math.min(frameIndex,
                animation.frames().size() - 1));
        FrameDefinition current = animation.frames().get(currentIndex);
        int nextIndex = currentIndex + 1;
        if (nextIndex >= animation.frames().size()) {
            if (!animation.loop() || animation.frames().size() == 1) return current;
            nextIndex = 0;
        }
        FrameDefinition next = animation.frames().get(nextIndex);
        double t = smoothstep(Math.max(0.0, Math.min(1.0, progress)));
        Map<String, LayerDefinition> nextLayers = new LinkedHashMap<>();
        for (LayerDefinition layer : next.layers()) nextLayers.put(layer.id(), layer);
        List<LayerDefinition> sampled = new ArrayList<>();
        for (LayerDefinition from : current.layers()) {
            LayerDefinition to = nextLayers.get(from.id());
            LayerDefinition layer = from.copy();
            if (to != null && from.spritePath().equals(to.spritePath())) {
                layer.offset(lerp(from.offsetX(), to.offsetX(), t),
                        lerp(from.offsetY(), to.offsetY(), t));
                layer.scale(lerp(from.scaleX(), to.scaleX(), t),
                        lerp(from.scaleY(), to.scaleY(), t));
                layer.angleDegrees(interpolateAngle(from.angleDegrees(),
                        to.angleDegrees(), t));
                layer.pivot(lerp(from.pivotX(), to.pivotX(), t),
                        lerp(from.pivotY(), to.pivotY(), t));
                layer.z(t < 0.5 ? from.z() : to.z());
                layer.visible(t < 0.5 ? from.visible() : to.visible());
            }
            sampled.add(layer);
        }
        return FrameDefinition.preview(current, sampled);
    }

    /** Samples the same duration-weighted normalized phase consumed by the game. */
    public FrameDefinition samplePhase(AnimationDefinition animation, double phase) {
        if (animation.frames().isEmpty()) {
            throw new IllegalArgumentException("Animation has no keyframes");
        }
        if (animation.frames().size() == 1) return animation.frames().get(0);
        double normalized = animation.loop()
                ? phase - Math.floor(phase) : Math.max(0.0, Math.min(1.0, phase));
        if (!animation.loop() && normalized >= 1.0) {
            return animation.frames().get(animation.frames().size() - 1);
        }
        int totalDuration = animation.frames().stream()
                .mapToInt(FrameDefinition::durationMs).sum();
        double timeMs = normalized * totalDuration;
        int frameIndex = 0;
        int elapsedMs = 0;
        while (frameIndex + 1 < animation.frames().size()
                && timeMs >= elapsedMs + animation.frames().get(frameIndex).durationMs()) {
            elapsedMs += animation.frames().get(frameIndex).durationMs();
            frameIndex++;
        }
        FrameDefinition frame = animation.frames().get(frameIndex);
        return sample(animation, frameIndex,
                (timeMs - elapsedMs) / frame.durationMs());
    }

    public static double pixelsPerUnit(int width, int height) {
        return Math.min(width, height) * 0.40;
    }

    private BufferedImage image(String spritePath) {
        return images.computeIfAbsent(spritePath, key -> {
            Path path = modRoot.resolve(key).normalize();
            if (!path.startsWith(modRoot)) {
                throw new IllegalArgumentException("Sprite escapes mod root: " + key);
            }
            try {
                BufferedImage image = ImageIO.read(path.toFile());
                if (image == null) throw new IOException("Unsupported image format");
                return image;
            } catch (IOException failure) {
                throw new IllegalStateException("Could not load sprite " + key, failure);
            }
        });
    }

    private static AffineTransform transform(UnitComposition unit, LayerDefinition layer,
                                             BufferedImage image, double originX,
                                             double originY, double pixelsPerUnit) {
        double sourceScale = pixelsPerUnit / unit.referencePixels();
        AffineTransform transform = new AffineTransform();
        transform.translate(originX + layer.offsetX() * pixelsPerUnit,
                originY - layer.offsetY() * pixelsPerUnit);
        transform.rotate(Math.toRadians(-layer.angleDegrees()));
        transform.scale(sourceScale * layer.scaleX(), sourceScale * layer.scaleY());
        transform.translate(-layer.pivotX() * image.getWidth(),
                -layer.pivotY() * image.getHeight());
        return transform;
    }

    private static Point pivotPoint(LayerDefinition layer, double originX,
                                    double originY, double pixelsPerUnit) {
        return new Point((int) Math.round(originX + layer.offsetX() * pixelsPerUnit),
                (int) Math.round(originY - layer.offsetY() * pixelsPerUnit));
    }

    private static void drawRotationHandle(Graphics2D graphics, Point pivot,
                                           double angleDegrees) {
        int radius = CompositionCanvas.ROTATION_HANDLE_RADIUS;
        Point handle = CompositionCanvas.rotationHandlePoint(pivot, angleDegrees);
        graphics.setColor(new Color(SELECTED.getRed(), SELECTED.getGreen(),
                SELECTED.getBlue(), 100));
        graphics.setStroke(new BasicStroke(1f));
        graphics.draw(new Ellipse2D.Double(pivot.x - radius, pivot.y - radius,
                radius * 2.0, radius * 2.0));
        graphics.setColor(SELECTED);
        graphics.drawLine(pivot.x, pivot.y, handle.x, handle.y);
        graphics.fill(new Ellipse2D.Double(handle.x - 5, handle.y - 5, 10, 10));
        graphics.setColor(BACKGROUND);
        graphics.setStroke(new BasicStroke(1.5f));
        graphics.draw(new Ellipse2D.Double(handle.x - 5, handle.y - 5, 10, 10));
    }

    private static void drawSelectionOverlay(Graphics2D graphics, RenderedLayer selected,
                                             double originX, double originY,
                                             double pixelsPerUnit) {
        graphics.setColor(SELECTED);
        graphics.setStroke(new BasicStroke(1.5f));
        graphics.draw(selected.outline());
        LayerDefinition layer = selected.layer();
        Point pivot = pivotPoint(layer, originX, originY, pixelsPerUnit);
        graphics.drawLine(pivot.x - 6, pivot.y, pivot.x + 6, pivot.y);
        graphics.drawLine(pivot.x, pivot.y - 6, pivot.x, pivot.y + 6);
        drawRotationHandle(graphics, pivot, layer.angleDegrees());
    }

    private static void drawGuides(Graphics2D graphics, int width, int height,
                                   double originX, double originY, double pixelsPerUnit) {
        graphics.setStroke(new BasicStroke(1f));
        graphics.setColor(GRID);
        double step = pixelsPerUnit * 0.25;
        for (double x = originX % step; x < width; x += step) {
            graphics.drawLine((int) Math.round(x), 0, (int) Math.round(x), height);
        }
        for (double y = originY % step; y < height; y += step) {
            graphics.drawLine(0, (int) Math.round(y), width, (int) Math.round(y));
        }
        graphics.setColor(AXIS);
        graphics.drawLine((int) Math.round(originX), 0, (int) Math.round(originX), height);
        graphics.drawLine(0, (int) Math.round(originY), width, (int) Math.round(originY));
    }

    private static void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static double lerp(double start, double end, double progress) {
        return start + (end - start) * progress;
    }

    private static double interpolateAngle(double start, double end, double progress) {
        double delta = (end - start + 540.0) % 360.0 - 180.0;
        return start + delta * progress;
    }

    private static double smoothstep(double progress) {
        return progress * progress * (3.0 - 2.0 * progress);
    }

    private record SheetFrame(AppearanceVariant variant, AnimationDefinition animation,
                              FrameDefinition frame) { }

    public record RenderedLayer(LayerDefinition layer, Shape outline) {
        public boolean contains(Point point) { return outline.contains(point); }
    }
}
