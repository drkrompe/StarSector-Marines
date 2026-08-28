package com.dillon.starsectormarines.ui.retained.headless;

import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasMetrics;
import com.dillon.starsectormarines.ui.retained.CanvasHostPass;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiPaintTarget;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.fs.starfarer.api.graphics.SpriteAPI;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders any retained document to a deterministic Java2D image without a
 * Starsector process or OpenGL context.
 */
public final class HeadlessUiRenderer {

    private final ResourceStore resources;
    private final HeadlessHostPassRenderer hostPassRenderer;

    public HeadlessUiRenderer(Path... resourceRoots) {
        this(List.of(resourceRoots));
    }

    public HeadlessUiRenderer(List<Path> resourceRoots) {
        this(resourceRoots, null);
    }

    public HeadlessUiRenderer(List<Path> resourceRoots,
                              HeadlessHostPassRenderer hostPassRenderer) {
        resources = new ResourceStore(resourceRoots);
        this.hostPassRenderer = hostPassRenderer;
    }

    public HeadlessUiRenderer(HeadlessHostPassRenderer hostPassRenderer,
                              Path... resourceRoots) {
        this(List.of(resourceRoots), hostPassRenderer);
    }

    public BufferedImage render(UiDocument document, int width, int height) {
        return render(document, width, height, 1f);
    }

    public BufferedImage render(UiDocument document, int width, int height, float alphaMult) {
        return render(document, width, height, width, height, 1f, alphaMult);
    }

    /**
     * Render one canvas host pass straight to an image, with no document around it.
     *
     * <p>An embedded scene is the same renderer whether it is drawn into a Marine
     * Ops page, under vanilla combat, or into a PNG for review — only the drain
     * differs. A tool that wants to look at a scene should not have to build a
     * retained document to reach it, so the raster drain is exposed on its own:
     * hand it a pass and a size, get pixels back.
     *
     * <p>The surface is one-to-one, so a host pass resolves its camera directly
     * in the returned image's own pixels and nothing is rescaled on the way out.
     *
     * @throws IllegalStateException when this renderer was built without a
     *     host-pass backend, which is the only thing that can interpret the pass
     */
    public BufferedImage renderHostPass(CanvasHostPass pass, int width, int height) {
        return renderHostPass(pass, width, height, 1f);
    }

    public BufferedImage renderHostPass(CanvasHostPass pass, int width, int height,
                                        float alphaMult) {
        if (pass == null) throw new IllegalArgumentException("host pass is required");
        if (hostPassRenderer == null) {
            throw new IllegalStateException("No host-pass backend to draw " + pass);
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("viewport dimensions must be positive");
        }
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        configure(graphics);
        Rect surface = new Rect(0f, 0f, width, height);
        CanvasMetrics metrics = new CanvasMetrics(surface, width, height, 1f);
        new RasterCanvasContext(graphics, resources, metrics, surface, alphaMult,
                hostPassRenderer).hostPass(pass);
        graphics.dispose();
        return output;
    }

    /**
     * Renders the live resolution/UI-scale policy into a physical-pixel image.
     * Resolution fit may shrink the whole document, while {@code uiScale}
     * independently changes the logical workspace available to layout.
     */
    public BufferedImage renderRelative(UiDocument document, int width, int height,
                                        float uiScale,
                                        float referenceWidth, float referenceHeight) {
        UiViewport viewport = UiViewport.relative(
                0f, 0f, width / uiScale, height / uiScale,
                uiScale, referenceWidth, referenceHeight);
        float deviceScale = viewport.documentScale() * uiScale;
        return render(document, width, height,
                viewport.documentWidth(), viewport.documentHeight(),
                deviceScale, 1f);
    }

    private BufferedImage render(UiDocument document, int width, int height,
                                 float documentWidth, float documentHeight,
                                 float deviceScale, float alphaMult) {
        if (document == null) throw new IllegalArgumentException("document is required");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("viewport dimensions must be positive");
        }
        document.styles().resolve(document.root());
        installFontMetrics(document, document.root(), new IdentityHashMap<>());
        document.layout(documentWidth, documentHeight);

        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        configure(graphics);
        graphics.scale(deviceScale, deviceScale);
        document.render(new RasterTarget(graphics, resources, hostPassRenderer,
                deviceScale), alphaMult);
        graphics.dispose();
        return output;
    }

    private void installFontMetrics(UiDocument document, UiElement element,
                                    IdentityHashMap<BitmapFont, Boolean> installed) {
        BitmapFont font = document.styles().fontFor(element);
        if (font != null && installed.put(font, Boolean.TRUE) == null) {
            resources.installFontMetrics(font);
        }
        for (UiElement child : element.children()) {
            installFontMetrics(document, child, installed);
        }
    }

    private static void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
    }

    private static final class RasterTarget implements UiPaintTarget {
        private final Graphics2D graphics;
        private final ResourceStore resources;
        private final HeadlessHostPassRenderer hostPassRenderer;
        private final float devicePixelRatio;
        private Shape initialClip;

        private RasterTarget(Graphics2D graphics, ResourceStore resources,
                             HeadlessHostPassRenderer hostPassRenderer,
                             float devicePixelRatio) {
            this.graphics = graphics;
            this.resources = resources;
            this.hostPassRenderer = hostPassRenderer;
            this.devicePixelRatio = devicePixelRatio;
        }

        @Override
        public void begin() {
            initialClip = graphics.getClip();
        }

        @Override
        public void end() {
            graphics.setClip(initialClip);
        }

        @Override
        public float devicePixelRatio() {
            return devicePixelRatio;
        }

        @Override
        public void clip(Rect clip) {
            graphics.setClip(new Rectangle2D.Float(
                    clip.x(), clip.y(), clip.width(), clip.height()));
        }

        @Override
        public void fill(Rect rect, Color color, float alphaMult) {
            withAlpha(color, alphaMult, () -> {
                graphics.setColor(opaque(color));
                graphics.fill(new Rectangle2D.Float(
                        rect.x(), rect.y(), rect.width(), rect.height()));
            });
        }

        @Override
        public void outline(Rect rect, Color color, float width, float alphaMult) {
            withAlpha(color, alphaMult, () -> {
                graphics.setColor(opaque(color));
                graphics.setStroke(new BasicStroke(width));
                graphics.draw(new Rectangle2D.Float(
                        rect.x(), rect.y(), rect.width(), rect.height()));
            });
        }

        @Override
        public void text(BitmapFont font, String text, Rect lineBox,
                         Color color, float alphaMult) {
            resources.drawText(graphics, font, text, lineBox.x(), lineBox.y(),
                    1f, 1f, color, alphaMult);
        }

        @Override
        public CanvasContext canvasContext(CanvasMetrics metrics, Rect visibleBounds,
                                           float alphaMult) {
            return new RasterCanvasContext(graphics, resources, metrics,
                    visibleBounds, alphaMult, hostPassRenderer);
        }

        private void withAlpha(Color color, float alphaMult, Runnable draw) {
            var previous = graphics.getComposite();
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    clampAlpha(color.getAlpha() / 255f * alphaMult)));
            draw.run();
            graphics.setComposite(previous);
        }
    }

    private static final class RasterCanvasContext extends CanvasContext {
        private final Graphics2D graphics;
        private final ResourceStore resources;
        private final HeadlessHostPassRenderer hostPassRenderer;
        private final float originX;
        private final float originY;
        private final float coordinateScaleX;
        private final float coordinateScaleY;

        private RasterCanvasContext(Graphics2D graphics, ResourceStore resources,
                                    CanvasMetrics metrics, Rect visibleBounds,
                                    float alphaMult,
                                    HeadlessHostPassRenderer hostPassRenderer) {
            this(graphics, resources, metrics, visibleBounds, alphaMult,
                    hostPassRenderer, metrics.contentBox().x(), metrics.contentBox().y(),
                    metrics.scaleX(), metrics.scaleY());
        }

        private RasterCanvasContext(Graphics2D graphics, ResourceStore resources,
                                    CanvasMetrics metrics, Rect visibleBounds,
                                    float alphaMult,
                                    HeadlessHostPassRenderer hostPassRenderer,
                                    float originX, float originY,
                                    float coordinateScaleX, float coordinateScaleY) {
            super(metrics, visibleBounds, alphaMult);
            this.graphics = graphics;
            this.resources = resources;
            this.hostPassRenderer = hostPassRenderer;
            this.originX = originX;
            this.originY = originY;
            this.coordinateScaleX = coordinateScaleX;
            this.coordinateScaleY = coordinateScaleY;
        }

        @Override
        protected void drawFillRect(float x, float y, float width, float height, Color color) {
            var previous = graphics.getComposite();
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    clampAlpha(color.getAlpha() / 255f * alphaMult())));
            graphics.setColor(opaque(color));
            graphics.fill(new Rectangle2D.Float(documentX(x), documentY(y),
                    width * coordinateScaleX, height * coordinateScaleY));
            graphics.setComposite(previous);
        }

        @Override
        protected void drawFillQuad(float x0, float y0, float x1, float y1,
                                    float x2, float y2, float x3, float y3,
                                    Color color) {
            var previous = graphics.getComposite();
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    clampAlpha(color.getAlpha() / 255f * alphaMult())));
            graphics.setColor(opaque(color));
            Path2D.Float path = new Path2D.Float();
            path.moveTo(documentX(x0), documentY(y0));
            path.lineTo(documentX(x1), documentY(y1));
            path.lineTo(documentX(x2), documentY(y2));
            path.lineTo(documentX(x3), documentY(y3));
            path.closePath();
            graphics.fill(path);
            graphics.setComposite(previous);
        }

        @Override
        protected void drawLine(float x1, float y1, float x2, float y2,
                                Color color, float strokeWidth) {
            var previous = graphics.getComposite();
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    clampAlpha(color.getAlpha() / 255f * alphaMult())));
            graphics.setColor(opaque(color));
            graphics.setStroke(new BasicStroke(strokeWidth * strokeScale(
                    coordinateScaleX, coordinateScaleY, x2 - x1, y2 - y1)));
            graphics.drawLine(Math.round(documentX(x1)), Math.round(documentY(y1)),
                    Math.round(documentX(x2)), Math.round(documentY(y2)));
            graphics.setComposite(previous);
        }

        @Override
        protected void drawText(BitmapFont font, String text, float x, float y, Color color) {
            resources.drawText(graphics, font, text, documentX(x), documentY(y),
                    coordinateScaleX, coordinateScaleY,
                    color, alphaMult());
        }

        @Override
        protected void drawSprite(String sourcePath, SpriteAPI liveSprite,
                                  float centerX, float centerY, float width, float height,
                                  float angleDegrees, Color tint,
                                  CanvasSpriteRegion region, CanvasBlend blend) {
            if (sourcePath == null || sourcePath.isBlank()) {
                throw new IllegalArgumentException(
                        "Headless canvas sprites require their source path");
            }
            BufferedImage image = sourceRegion(resources.tintedSprite(sourcePath, tint), region);
            AffineTransform transform = graphics.getTransform();
            var composite = graphics.getComposite();
            graphics.translate(documentX(centerX), documentY(centerY));
            graphics.rotate(Math.toRadians(-angleDegrees));
            graphics.scale(width * coordinateScaleX / image.getWidth(),
                    height * coordinateScaleY / image.getHeight());
            graphics.scale(region.flipX() ? -1d : 1d, region.flipY() ? -1d : 1d);
            float opacity = clampAlpha(tint.getAlpha() / 255f * alphaMult());
            graphics.setComposite(blend == CanvasBlend.ADDITIVE
                    ? new AdditiveComposite(opacity)
                    : AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            graphics.drawImage(image, -image.getWidth() / 2, -image.getHeight() / 2, null);
            graphics.setComposite(composite);
            graphics.setTransform(transform);
        }

        @Override
        protected boolean drawHostPass(CanvasHostPass pass) {
            if (hostPassRenderer == null) return false;
            CanvasMetrics metrics = metrics();
            Rect content = metrics.contentBox();
            CanvasHostViewport viewport = new CanvasHostViewport(
                    0f, 0f, content.width(), content.height(),
                    metrics.surfaceWidth(), metrics.surfaceHeight());
            // Native host passes resolve their camera directly in the physical content
            // box. Replay those resolved coordinates without stretching them through the
            // canvas's authored surface a second time.
            CanvasContext direct = new RasterCanvasContext(graphics, resources, metrics,
                    visibleBounds(), alphaMult(), hostPassRenderer,
                    content.x(), content.y(), 1f, 1f);
            return hostPassRenderer.draw(pass, direct, viewport, alphaMult());
        }

        private float documentX(float x) { return originX + x * coordinateScaleX; }

        private float documentY(float y) { return originY + y * coordinateScaleY; }

        private static float strokeScale(float scaleX, float scaleY,
                                         float deltaX, float deltaY) {
            float length = (float) Math.hypot(deltaX, deltaY);
            if (!(length > 0f)) return (float) Math.sqrt(scaleX * scaleY);
            float normalX = -deltaY / length;
            float normalY = deltaX / length;
            return (float) Math.hypot(normalX * scaleX, normalY * scaleY);
        }

        private static BufferedImage sourceRegion(BufferedImage source,
                                                  CanvasSpriteRegion region) {
            if (region.equals(CanvasSpriteRegion.FULL)) return source;
            int left = Math.round(region.x() * source.getWidth());
            int top = Math.round(region.y() * source.getHeight());
            int right = Math.round((region.x() + region.width()) * source.getWidth());
            int bottom = Math.round((region.y() + region.height()) * source.getHeight());
            if (right <= left || bottom <= top) {
                throw new IllegalArgumentException("sprite region resolves to no source pixels");
            }
            return source.getSubimage(left, top, right - left, bottom - top);
        }
    }

    private static final class ResourceStore {
        private final List<Path> roots;
        private final Map<String, BufferedImage> images = new LinkedHashMap<>();
        private final Map<TintKey, BufferedImage> tintedFonts = new LinkedHashMap<>();
        private final Map<SpriteTintKey, BufferedImage> tintedSprites = new LinkedHashMap<>();
        private final IdentityHashMap<BitmapFont, Boolean> installedFonts =
                new IdentityHashMap<>();

        private ResourceStore(List<Path> roots) {
            if (roots == null || roots.isEmpty()) {
                throw new IllegalArgumentException("at least one resource root is required");
            }
            this.roots = new ArrayList<>(roots.size());
            for (Path root : roots) this.roots.add(root.toAbsolutePath().normalize());
        }

        private String readString(String resourcePath) {
            try {
                return Files.readString(resolve(resourcePath));
            } catch (IOException failure) {
                throw new IllegalStateException("Could not read " + resourcePath, failure);
            }
        }

        private BufferedImage image(String resourcePath) {
            return images.computeIfAbsent(resourcePath, path -> {
                try {
                    BufferedImage image = ImageIO.read(resolve(path).toFile());
                    if (image == null) throw new IOException("Unsupported image format");
                    return image;
                } catch (IOException failure) {
                    throw new IllegalStateException("Could not load " + path, failure);
                }
            });
        }

        private void installFontMetrics(BitmapFont font) {
            if (installedFonts.put(font, Boolean.TRUE) == null) {
                font.installMetrics(readString(font.sourcePath()));
            }
        }

        private BufferedImage tintedSprite(String resourcePath, Color tint) {
            int rgb = tint.getRGB() & 0x00FFFFFF;
            if (rgb == 0x00FFFFFF) return image(resourcePath);
            return tintedSprites.computeIfAbsent(new SpriteTintKey(resourcePath, rgb), key ->
                    modulate(image(key.path()), key.rgb()));
        }

        private void drawText(Graphics2D graphics, BitmapFont font, String text,
                              float x, float y, float scaleX, float scaleY,
                              Color color, float alphaMult) {
            if (text == null || text.isEmpty()) return;
            installFontMetrics(font);
            int alpha = Math.round(255f * clampAlpha(color.getAlpha() / 255f * alphaMult));
            TintKey key = new TintKey(font.pagePath(), color.getRGB() & 0x00FFFFFF, alpha);
            BufferedImage atlas = tintedFonts.computeIfAbsent(key,
                    ignored -> tint(image(font.pagePath()), color, alpha));
            float cursor = x;
            for (int index = 0; index < text.length(); index++) {
                BitmapFont.Glyph glyph = font.glyph(text.charAt(index));
                if (glyph == null) continue;
                if (glyph.w > 0 && glyph.h > 0) {
                    int dx1 = Math.round(cursor + glyph.xoffset * scaleX);
                    int dy1 = Math.round(y + glyph.yoffset * scaleY);
                    int dx2 = Math.round(dx1 + glyph.w * scaleX);
                    int dy2 = Math.round(dy1 + glyph.h * scaleY);
                    graphics.drawImage(atlas, dx1, dy1, dx2, dy2,
                            glyph.x, glyph.y, glyph.x + glyph.w, glyph.y + glyph.h, null);
                }
                cursor += glyph.xadvance * scaleX;
            }
        }

        private Path resolve(String resourcePath) {
            String local = resourcePath.replace('/', java.io.File.separatorChar);
            for (Path root : roots) {
                Path candidate = root.resolve(local).normalize();
                if (candidate.startsWith(root) && Files.isRegularFile(candidate)) return candidate;
            }
            throw new IllegalStateException("Resource not found in " + roots + ": " + resourcePath);
        }

        private static BufferedImage tint(BufferedImage source, Color color, int alpha) {
            BufferedImage tinted = new BufferedImage(
                    source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            int rgb = color.getRGB() & 0x00FFFFFF;
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    int sourcePixel = source.getRGB(x, y);
                    int sourceAlpha = sourcePixel >>> 24;
                    int coverage = Math.max(sourceAlpha, sourcePixel & 0xFF);
                    int tintedAlpha = coverage * alpha / 255;
                    tinted.setRGB(x, y, tintedAlpha << 24 | rgb);
                }
            }
            return tinted;
        }

        private static BufferedImage modulate(BufferedImage source, int tintRgb) {
            BufferedImage tinted = new BufferedImage(
                    source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            int tintRed = tintRgb >>> 16 & 0xff;
            int tintGreen = tintRgb >>> 8 & 0xff;
            int tintBlue = tintRgb & 0xff;
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    int sourcePixel = source.getRGB(x, y);
                    int alpha = sourcePixel >>> 24;
                    int red = (sourcePixel >>> 16 & 0xff) * tintRed / 255;
                    int green = (sourcePixel >>> 8 & 0xff) * tintGreen / 255;
                    int blue = (sourcePixel & 0xff) * tintBlue / 255;
                    tinted.setRGB(x, y, alpha << 24 | red << 16 | green << 8 | blue);
                }
            }
            return tinted;
        }
    }

    private record TintKey(String path, int rgb, int alpha) { }

    private record SpriteTintKey(String path, int rgb) { }

    /** Java2D equivalent of the live canvas's SRC_ALPHA, ONE sprite blend. */
    private record AdditiveComposite(float opacity) implements Composite {

        private AdditiveComposite {
            if (!Float.isFinite(opacity) || opacity < 0f || opacity > 1f) {
                throw new IllegalArgumentException("additive opacity must be between zero and one");
            }
        }

        @Override
        public CompositeContext createContext(ColorModel sourceColorModel,
                                              ColorModel destinationColorModel,
                                              RenderingHints hints) {
            return new AdditiveContext(sourceColorModel, destinationColorModel, opacity);
        }
    }

    private record AdditiveContext(ColorModel sourceColorModel,
                                   ColorModel destinationColorModel,
                                   float opacity) implements CompositeContext {

        @Override
        public void compose(Raster source, Raster destinationIn,
                            WritableRaster destinationOut) {
            int width = Math.min(source.getWidth(), destinationIn.getWidth());
            int height = Math.min(source.getHeight(), destinationIn.getHeight());
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int sourceArgb = sourceColorModel.getRGB(source.getDataElements(
                            source.getMinX() + x, source.getMinY() + y, null));
                    int destinationArgb = destinationColorModel.getRGB(
                            destinationIn.getDataElements(destinationIn.getMinX() + x,
                                    destinationIn.getMinY() + y, null));
                    int sourceAlpha = Math.round((sourceArgb >>> 24) * opacity);
                    int destinationAlpha = destinationArgb >>> 24;
                    int outputAlpha = Math.min(255, destinationAlpha
                            + sourceAlpha * sourceAlpha / 255);
                    int outputRed = additiveChannel(destinationArgb >>> 16 & 0xff,
                            sourceArgb >>> 16 & 0xff, sourceAlpha);
                    int outputGreen = additiveChannel(destinationArgb >>> 8 & 0xff,
                            sourceArgb >>> 8 & 0xff, sourceAlpha);
                    int outputBlue = additiveChannel(destinationArgb & 0xff,
                            sourceArgb & 0xff, sourceAlpha);
                    int outputArgb = outputAlpha << 24 | outputRed << 16
                            | outputGreen << 8 | outputBlue;
                    destinationOut.setDataElements(destinationOut.getMinX() + x,
                            destinationOut.getMinY() + y,
                            destinationColorModel.getDataElements(outputArgb, null));
                }
            }
        }

        @Override
        public void dispose() {
        }

        private static int additiveChannel(int destination, int source, int sourceAlpha) {
            return Math.min(255, destination + source * sourceAlpha / 255);
        }
    }

    private static Color opaque(Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue());
    }

    private static float clampAlpha(float alpha) {
        return Math.max(0f, Math.min(1f, alpha));
    }
}
