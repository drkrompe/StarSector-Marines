package com.dillon.starsectormarines.tools.layerauthoring;

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
        for (LayerDefinition layer : sorted) {
            if (!layer.visible()) continue;
            BufferedImage image = image(layer.spritePath());
            AffineTransform transform = transform(unit, layer, image,
                    originX, originY, pixelsPerUnit);
            graphics.drawImage(image, transform, null);
            Shape outline = transform.createTransformedShape(
                    new java.awt.Rectangle(0, 0, image.getWidth(), image.getHeight()));
            rendered.add(new RenderedLayer(layer, outline));
            if (layer.id().equals(selectedLayer)) {
                graphics.setColor(SELECTED);
                graphics.setStroke(new BasicStroke(1.5f));
                graphics.draw(outline);
                Point pivot = pivotPoint(layer, originX, originY, pixelsPerUnit);
                graphics.drawLine(pivot.x - 6, pivot.y, pivot.x + 6, pivot.y);
                graphics.drawLine(pivot.x, pivot.y - 6, pivot.x, pivot.y + 6);
            }
        }
        return rendered;
    }

    public BufferedImage renderSheet(UnitComposition unit, int cellWidth, int cellHeight) {
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(unit.frames().size())));
        int rows = (unit.frames().size() + columns - 1) / columns;
        int labelHeight = 30;
        BufferedImage sheet = new BufferedImage(columns * cellWidth,
                rows * (cellHeight + labelHeight), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = sheet.createGraphics();
        configure(graphics);
        graphics.setColor(new Color(0x05, 0x09, 0x0E));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        for (int index = 0; index < unit.frames().size(); index++) {
            FrameDefinition frame = unit.frames().get(index);
            int x = index % columns * cellWidth;
            int y = index / columns * (cellHeight + labelHeight);
            graphics.setColor(new Color(0xD9, 0xE8, 0xF1));
            graphics.drawString(frame.label() + "  ·  " + frame.durationMs() + " ms",
                    x + 10, y + 20);
            BufferedImage cell = renderFrame(unit, frame, cellWidth, cellHeight,
                    null, false);
            graphics.drawImage(cell, x, y + labelHeight, null);
        }
        graphics.dispose();
        return sheet;
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

    public record RenderedLayer(LayerDefinition layer, Shape outline) {
        public boolean contains(Point point) { return outline.contains(point); }
    }
}
