package com.dillon.starsectormarines.ui.retained.headless;

import com.dillon.starsectormarines.ops.MarineOpsUiViewport;
import com.dillon.starsectormarines.render2d.texture.FittedTextureOverlay;
import com.dillon.starsectormarines.tools.snapshot.SnapshotWorkers;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasMesh;
import com.dillon.starsectormarines.ui.retained.CanvasMetrics;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.CanvasHostPass;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.dillon.starsectormarines.ui.retained.LayoutLint;
import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiImage;
import com.dillon.starsectormarines.ui.retained.UiPaintTarget;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Paint;
import java.awt.CompositeContext;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Renders any retained document to a deterministic Java2D image without a
 * Starsector process or OpenGL context.
 */
public final class HeadlessUiRenderer {

    /** Test-backend access to the existing decoded-resource cache, without registering transient textures. */
    public static BufferedImage sourceImage(CanvasContext context, String path) {
        if (!(context instanceof RasterCanvasContext raster)) throw new IllegalArgumentException("Not a raster canvas");
        return raster.resources.image(path);
    }

    /** Draws a transient raster through the same transform, host alpha and additive blend as ordinary sprites. */
    public static void additiveImage(CanvasContext context, BufferedImage image, float centerX, float centerY,
                                     float width, float height, float angleDegrees, float alpha) {
        if (!(context instanceof RasterCanvasContext raster)) throw new IllegalArgumentException("Not a raster canvas");
        var graphics = raster.graphics;
        var transform = graphics.getTransform();
        var composite = graphics.getComposite();
        try {
            graphics.translate(raster.documentX(centerX), raster.documentY(centerY));
            graphics.rotate(Math.toRadians(-angleDegrees));
            graphics.scale(width * raster.coordinateScaleX / image.getWidth(), height * raster.coordinateScaleY / image.getHeight());
            graphics.setComposite(new AdditiveComposite(clampAlpha(alpha * raster.alphaMult())));
            graphics.translate(-image.getWidth()/2d, -image.getHeight()/2d);
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.setComposite(composite);
            graphics.setTransform(transform);
        }
    }

    /**
     * Draws a transient raster, unscaled and unturned, with its top-left at canvas ({@code x}, {@code y}): its
     * colour multiplies the surface under it where its alpha is set ({@code multiply}), or it blends over it as an
     * ordinary straight-alpha image. The live counterpart of the multiply is a {@code DST_COLOR, ZERO} blend.
     */
    public static void blendImage(CanvasContext context, BufferedImage image, float x, float y, boolean multiply) {
        if (!(context instanceof RasterCanvasContext raster)) throw new IllegalArgumentException("Not a raster canvas");
        var graphics = raster.graphics;
        var transform = graphics.getTransform();
        var composite = graphics.getComposite();
        try {
            graphics.translate(raster.documentX(x), raster.documentY(y));
            graphics.scale(raster.coordinateScaleX, raster.coordinateScaleY);
            graphics.setComposite(multiply ? new MultiplyComposite(clampAlpha(raster.alphaMult()))
                    : AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clampAlpha(raster.alphaMult())));
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.setComposite(composite);
            graphics.setTransform(transform);
        }
    }

    /** Draws one horizontal band of a canvas; {@code top}/{@code bottom} are the band's rows in canvas coordinates. */
    @FunctionalInterface
    public interface BandDrawer {
        void draw(CanvasContext band, int top, int bottom) throws Exception;
    }

    /**
     * Draws {@code drawer} into {@code context} as horizontal bands of {@code bandRows} rows, side by side on
     * {@link SnapshotWorkers#raster()} (one after another when there is one worker), and returns once every band is
     * drawn. Returns false, drawing nothing, when the canvas cannot be split — then the caller draws it whole.
     *
     * <p><b>A band is the whole canvas with a clip</b>, not a smaller surface: every band draws through the same
     * transform, so every primitive is placed at exactly the device coordinates it always was and the clip only
     * decides which rows it may write. Bands are disjoint, so each pixel is written by one band only, in the order
     * the drawer issues its draws. Only a direct, unscaled host-pass canvas (the review drain) is split: elsewhere the
     * device transform is not the identity, and a band boundary would not be a pixel boundary.
     *
     * <p><b>Where the bands fall is part of the picture</b>, which is why their height is the caller's constant and
     * not a function of the worker count. Java2D's transformed-image loop walks its sample position down a clip span
     * in fixed point from an exact transform of the span's first row, so a boundary through a scaled or turned sprite
     * restarts that walk there, and the band's first row can sample a hair away from where the uninterrupted walk
     * had drifted to: a level or two in a channel, in about one pixel in five hundred along that row. The same walk
     * places a turned sprite's edges, so very rarely an edge pixel is taken in or left out (seen once in 3.7
     * billion pixels of the broadside scene, sixteen levels). Fixed
     * bands make that the same on every machine and at every worker count, so a frame stays a function of the
     * state it draws.
     *
     * <p>The drawer runs concurrently with itself: it must only read what it shares.
     */
    public static boolean drawInBands(CanvasContext context, int bandRows, BandDrawer drawer) {
        if (bandRows <= 0 || !(context instanceof RasterCanvasContext raster)) return false;
        if (!raster.graphics.getTransform().isIdentity()
                || raster.coordinateScaleX != 1f || raster.coordinateScaleY != 1f) return false;
        if (raster.surfaceRows <= 0 || raster.surfaceColumns <= 0) return false;
        Rectangle clip = raster.graphics.getClipBounds();
        int top = Math.max(0, clip == null ? 0 : clip.y);
        int bottom = Math.min(raster.surfaceRows, clip == null ? raster.surfaceRows : clip.y + clip.height);
        if (bottom - top <= bandRows) return false;
        List<Graphics2D> graphics = new ArrayList<>();
        List<Callable<Void>> tasks = new ArrayList<>();
        try {
            // Bands start on multiples of bandRows from the surface's own top, wherever the clip begins.
            for (int y0 = top; y0 < bottom; y0 = (y0 / bandRows + 1) * bandRows) {
                int y1 = Math.min(bottom, (y0 / bandRows + 1) * bandRows);
                Graphics2D bandGraphics = (Graphics2D) raster.graphics.create();
                bandGraphics.clipRect(0, y0, raster.surfaceColumns, y1 - y0);
                graphics.add(bandGraphics);
                CanvasContext bandContext = new RasterCanvasContext(bandGraphics, raster.resources, raster.metrics(),
                        raster.visibleBounds(), raster.alphaMult(), raster.hostPassRenderer,
                        raster.originX, raster.originY, 1f, 1f);
                int localTop = (int) Math.floor(y0 - raster.originY);
                int localBottom = (int) Math.ceil(y1 - raster.originY);
                tasks.add(() -> {
                    drawer.draw(bandContext, localTop, localBottom);
                    return null;
                });
            }
            if (SnapshotWorkers.threads() > 1) {
                SnapshotWorkers.runAll(tasks);
            } else {
                for (Callable<Void> task : tasks) {
                    try {
                        task.call();
                    } catch (RuntimeException failure) {
                        throw failure;
                    } catch (Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                }
            }
        } finally {
            for (Graphics2D bandGraphics : graphics) bandGraphics.dispose();
        }
        return true;
    }

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
        // A full-screen page painted 1:1 is the host's relative viewport at the shipping reference.
        LayoutLint.Policy policy = width == 1920 && height == 1080
                ? new LayoutLint.Policy(1f, MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT)
                : null;
        return render(document, width, height, width, height, 1f, alphaMult, policy);
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
        resources.beginImage();
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        configure(graphics);
        Rect surface = new Rect(0f, 0f, width, height);
        CanvasMetrics metrics = new CanvasMetrics(surface, width, height, 1f);
        RasterCanvasContext canvas = new RasterCanvasContext(graphics, resources, metrics, surface, alphaMult,
                hostPassRenderer);
        canvas.surfaceColumns = width;
        canvas.surfaceRows = height;
        canvas.hostPass(pass);
        graphics.dispose();
        return output;
    }

    /**
     * Render one canvas producer straight to an image at a one-to-one surface, with no
     * document around it: the way to look at a producer such as a banner at the pixel
     * size it has on a page.
     */
    public BufferedImage renderCanvas(CanvasProducer producer, int width, int height) {
        if (producer == null) throw new IllegalArgumentException("canvas producer is required");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("canvas dimensions must be positive");
        }
        resources.beginImage();
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        configure(graphics);
        Rect surface = new Rect(0f, 0f, width, height);
        CanvasMetrics metrics = new CanvasMetrics(surface, width, height, 1f);
        RasterCanvasContext canvas = new RasterCanvasContext(graphics, resources, metrics, surface, 1f,
                hostPassRenderer);
        canvas.surfaceColumns = width;
        canvas.surfaceRows = height;
        producer.draw(canvas);
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
                deviceScale, 1f, new LayoutLint.Policy(uiScale, referenceWidth, referenceHeight));
    }

    private BufferedImage render(UiDocument document, int width, int height,
                                 float documentWidth, float documentHeight,
                                 float deviceScale, float alphaMult, LayoutLint.Policy lintPolicy) {
        if (document == null) throw new IllegalArgumentException("document is required");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("viewport dimensions must be positive");
        }
        document.styles().resolve(document.root());
        installFontMetrics(document, document.root(), new IdentityHashMap<>());
        document.layout(documentWidth, documentHeight);

        resources.beginImage();
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        configure(graphics);
        graphics.scale(deviceScale, deviceScale);
        document.render(new RasterTarget(graphics, resources, hostPassRenderer,
                deviceScale), alphaMult);
        graphics.dispose();
        LayoutLint.observe(document, documentWidth, documentHeight, lintPolicy, output);
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

        /**
         * Intrinsic size straight from the file on disk. The live sprite stays
         * null on purpose: this backend draws from the asset path, and the
         * painter only needs the aspect ratio from here.
         */
        @Override
        public UiImage image(String path) {
            BufferedImage source = resources.imageOrNull(path);
            return source == null ? null
                    : new UiImage(null, source.getWidth(), source.getHeight());
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
        /** Pixel size of the image under this canvas when it is known to be a whole, untransformed surface; else -1. */
        private int surfaceColumns = -1;
        private int surfaceRows = -1;

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
        protected void drawFillQuadGradient(float x0, float y0, float x1, float y1,
                                            float x2, float y2, float x3, float y3,
                                            Color c0, Color c1, Color c2, Color c3) {
            // Exact when the gradient runs between opposite edges with one colour per edge.
            boolean across = c0.equals(c1) && c2.equals(c3);
            boolean along = c0.equals(c3) && c1.equals(c2);
            if (!across && !along) {
                super.drawFillQuadGradient(x0, y0, x1, y1, x2, y2, x3, y3, c0, c1, c2, c3);
                return;
            }
            // The gradient runs perpendicular to its starting edge, out to the opposite edge. Joining
            // the two edges' midpoints instead tilts it on any trapezoid (a chamfered band), and a
            // tilted gradient shades the band's ends with colours from above or below it.
            float ex = across ? x1 - x0 : x3 - x0, ey = across ? y1 - y0 : y3 - y0;
            float length = (float) Math.hypot(ex, ey);
            float nx = length > 0f ? -ey / length : 0f, ny = length > 0f ? ex / length : 1f;
            float ox = across ? x3 - x0 : x1 - x0, oy = across ? y3 - y0 : y1 - y0;
            float reach = ox * nx + oy * ny;
            float fromX = x0, fromY = y0;
            float toX = x0 + nx * reach, toY = y0 + ny * reach;
            Color to = across ? c3 : c1;
            Composite previousComposite = graphics.getComposite();
            Paint previousPaint = graphics.getPaint();
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    clampAlpha(alphaMult())));
            graphics.setPaint(new GradientPaint(documentX(fromX), documentY(fromY), c0,
                    documentX(toX), documentY(toY), to));
            Path2D.Float path = new Path2D.Float();
            path.moveTo(documentX(x0), documentY(y0));
            path.lineTo(documentX(x1), documentY(y1));
            path.lineTo(documentX(x2), documentY(y2));
            path.lineTo(documentX(x3), documentY(y3));
            path.closePath();
            graphics.fill(path);
            graphics.setPaint(previousPaint);
            graphics.setComposite(previousComposite);
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
        protected void drawMesh(CanvasMesh mesh, float x, float y, float scale, Color color) {
            var previous = graphics.getComposite();
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    clampAlpha(color.getAlpha() / 255f * alphaMult())));
            graphics.setColor(opaque(color));
            // Fill the triangle set as one shape. Antialiasing individual
            // pieces would make their shared boundaries visible as seams.
            Path2D.Float path = new Path2D.Float();
            for (int i = 0; i < mesh.vertexCount(); i += 3) {
                path.moveTo(documentX(x + mesh.x(i) * scale), documentY(y + mesh.y(i) * scale));
                path.lineTo(documentX(x + mesh.x(i + 1) * scale), documentY(y + mesh.y(i + 1) * scale));
                path.lineTo(documentX(x + mesh.x(i + 2) * scale), documentY(y + mesh.y(i + 2) * scale));
                path.closePath();
            }
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
        public void spritePatch(String path,float cx,float cy,float width,float height,
                                Color tint,CanvasSpriteRegion region,float[] vertices) {
            // Match the adjacent whole tiles' shared destination pixel boundaries.
            float left=(float)Math.floor(cx-width/2+.5001d),right=(float)Math.floor(cx+width/2+.5001d);
            float top=(float)Math.floor(cy-height/2+.5001d),bottom=(float)Math.floor(cy+height/2+.5001d);
            cx=(left+right)/2;cy=(top+bottom)/2;width=right-left;height=bottom-top;
            var clip=graphics.getClip();
            var polygon=new Path2D.Float();
            for(int i=0;i<4;i++) {
                float x=documentX(cx+(vertices[i*2]-.5f)*width);
                float y=documentY(cy+(.5f-vertices[i*2+1])*height);
                if(i==0)polygon.moveTo(x,y);else polygon.lineTo(x,y);
            }
            polygon.closePath();graphics.clip(polygon);
            drawSprite(path,null,cx,cy,width,height,0,tint,region,CanvasBlend.NORMAL);
            graphics.setClip(clip);
        }

        @Override
        public float[] imageSize(String path) {
            BufferedImage image = path == null || path.isBlank() ? null : resources.imageOrNull(path);
            return image == null ? null : new float[]{image.getWidth(), image.getHeight()};
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
            BufferedImage image = resources.tintedRegion(sourcePath, tint, region);
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
            graphics.translate(-image.getWidth() / 2d, -image.getHeight() / 2d);
            graphics.drawImage(image, 0, 0, null);
            graphics.setComposite(composite);
            graphics.setTransform(transform);
        }

        @Override
        public CanvasHostViewport hostViewport() {
            if (hostPassRenderer == null) return null;
            CanvasMetrics metrics = metrics();
            Rect content = metrics.contentBox();
            return new CanvasHostViewport(
                    0f, 0f, content.width(), content.height(),
                    metrics.surfaceWidth(), metrics.surfaceHeight());
        }

        @Override
        protected boolean drawHostPass(CanvasHostPass pass) {
            if (hostPassRenderer == null) return false;
            CanvasMetrics metrics = metrics();
            Rect content = metrics.contentBox();
            CanvasHostViewport viewport = hostViewport();
            // Native host passes resolve their camera directly in the physical content
            // box. Replay those resolved coordinates without stretching them through the
            // canvas's authored surface a second time.
            RasterCanvasContext direct = new RasterCanvasContext(graphics, resources, metrics,
                    visibleBounds(), alphaMult(), hostPassRenderer,
                    content.x(), content.y(), 1f, 1f);
            direct.surfaceColumns = surfaceColumns;
            direct.surfaceRows = surfaceRows;
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

    }

    /** Identifies one cut of one decoded image; the image is compared by identity, which is what a record does with it. */
    private record RegionKey(BufferedImage source, int left, int top, int right, int bottom) { }

    private static final class ResourceStore {
        private final List<Path> roots;
        private final Map<String, BufferedImage> images = new ConcurrentHashMap<>();
        private final Map<TintKey, BufferedImage> tintedFonts = new ConcurrentHashMap<>();
        /**
         * Tinted cuts this image has drawn, and the ones the image before it drew. A cut neither of the last two
         * images drew is let go: a tint that moves every frame (a burning tree's char) is never asked for again, and
         * with only a size cap those dead shades filled 256 MB on every long burn and stayed there. A tint the scene
         * keeps drawing moves forward frame to frame and is cut once.
         */
        private volatile Map<SpriteTintKey, BufferedImage> tintedSprites = new ConcurrentHashMap<>();
        private volatile Map<SpriteTintKey, BufferedImage> previousTints = new ConcurrentHashMap<>();
        /** Source rectangles already cut, by image identity: a fresh subimage per draw cost Java2D a new surface and a disposer record every time. */
        private final Map<RegionKey, BufferedImage> regions = new ConcurrentHashMap<>();
        private final IdentityHashMap<BitmapFont, Boolean> installedFonts =
                new IdentityHashMap<>();

        /** Cuts beyond this many are dropped wholesale; a cut is a view onto its image, so it costs a header, not pixels. */
        private static final int MAX_REGIONS = 200_000;
        /** Pixels of tinted cuts one image may build before they are all dropped: 64 M pixels is 256 MB. */
        private static final long MAX_TINTED_PIXELS = 64L * 1024 * 1024;
        private final AtomicLong tintedPixels = new AtomicLong();

        /** Called once per image, before anything draws into it, never from a band. */
        private void beginImage() {
            previousTints = tintedSprites;
            tintedSprites = new ConcurrentHashMap<>();
            tintedPixels.set(0L);
        }

        private BufferedImage region(BufferedImage source, CanvasSpriteRegion region) {
            if (region.equals(CanvasSpriteRegion.FULL)) return source;
            int left = Math.round(region.x() * source.getWidth());
            int top = Math.round(region.y() * source.getHeight());
            int right = Math.round((region.x() + region.width()) * source.getWidth());
            int bottom = Math.round((region.y() + region.height()) * source.getHeight());
            if (right <= left || bottom <= top) {
                throw new IllegalArgumentException("sprite region resolves to no source pixels");
            }
            if (regions.size() > MAX_REGIONS) regions.clear();
            return regions.computeIfAbsent(new RegionKey(source, left, top, right, bottom),
                    key -> source.getSubimage(left, top, right - left, bottom - top));
        }

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
                    BufferedImage image = FittedTextureOverlay.read(path, resolve(path));
                    if (image == null) throw new IOException("Unsupported image format");
                    return image;
                } catch (IOException failure) {
                    throw new IllegalStateException("Could not load " + path, failure);
                }
            });
        }

        /** The same load, but a missing or unreadable asset is an answer rather than a failure. */
        private BufferedImage imageOrNull(String resourcePath) {
            try {
                return image(resourcePath);
            } catch (RuntimeException failure) {
                return null;
            }
        }

        private void installFontMetrics(BitmapFont font) {
            if (installedFonts.put(font, Boolean.TRUE) == null) {
                font.installMetrics(readString(font.sourcePath()));
            }
        }

        /**
         * The cut a draw takes from a sprite, multiplied by its tint. Only the cut is tinted and kept: a tint that
         * moves every frame (a burning tree's char) on a large sheet made a whole-sheet copy per shade, and a big
         * fire ran a 2 GB review heap out.
         */
        private BufferedImage tintedRegion(String resourcePath, Color tint, CanvasSpriteRegion region) {
            int rgb = tint.getRGB() & 0x00FFFFFF;
            BufferedImage cut = region(image(resourcePath), region);
            if (rgb == 0x00FFFFFF) return cut;
            if (tintedPixels.get() > MAX_TINTED_PIXELS) {
                tintedSprites.clear();
                tintedPixels.set(0L);
            }
            Map<SpriteTintKey, BufferedImage> previous = previousTints;
            return tintedSprites.computeIfAbsent(new SpriteTintKey(cut, rgb), key -> {
                BufferedImage carried = previous.get(key);
                BufferedImage tinted = carried != null ? carried : modulate(key.cut(), key.rgb());
                tintedPixels.addAndGet((long) tinted.getWidth() * tinted.getHeight());
                return tinted;
            });
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

    /** One cut of a decoded image (by identity, as {@link RegionKey} holds it) in one tint. */
    private record SpriteTintKey(BufferedImage cut, int rgb) { }

    /** Multiplies the destination's colour by the source's where the source has alpha, by that alpha times {@code opacity}. */
    private record MultiplyComposite(float opacity) implements Composite {
        @Override
        public CompositeContext createContext(ColorModel sourceColorModel, ColorModel destinationColorModel,
                                              RenderingHints hints) {
            return new CompositeContext() {
                @Override
                public void compose(Raster source, Raster destinationIn, WritableRaster destinationOut) {
                    int width = Math.min(source.getWidth(), destinationIn.getWidth());
                    int height = Math.min(source.getHeight(), destinationIn.getHeight());
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int s = sourceColorModel.getRGB(source.getDataElements(source.getMinX() + x, source.getMinY() + y, null));
                            int d = destinationColorModel.getRGB(destinationIn.getDataElements(destinationIn.getMinX() + x, destinationIn.getMinY() + y, null));
                            float a = (s >>> 24) / 255f * opacity;
                            if (a <= 0f) {
                                destinationOut.setDataElements(destinationOut.getMinX() + x, destinationOut.getMinY() + y,
                                        destinationColorModel.getDataElements(d, null));
                                continue;
                            }
                            int r = multiplyChannel(d >>> 16 & 0xff, s >>> 16 & 0xff, a);
                            int g = multiplyChannel(d >>> 8 & 0xff, s >>> 8 & 0xff, a);
                            int b = multiplyChannel(d & 0xff, s & 0xff, a);
                            destinationOut.setDataElements(destinationOut.getMinX() + x, destinationOut.getMinY() + y,
                                    destinationColorModel.getDataElements((d & 0xff000000) | r << 16 | g << 8 | b, null));
                        }
                    }
                }

                @Override
                public void dispose() { }
            };
        }

        private static int multiplyChannel(int dst, int src, float a) {
            float multiplied = dst * src / 255f;
            return Math.round(dst + (multiplied - dst) * a);
        }
    }

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
            if (packedArgb(sourceColorModel, source) && packedArgb(destinationColorModel, destinationIn)
                    && !destinationColorModel.isAlphaPremultiplied()
                    && destinationOut.getDataBuffer().getDataType() == DataBuffer.TYPE_INT) {
                composePacked(source, destinationIn, destinationOut, width, height,
                        sourceColorModel.isAlphaPremultiplied());
                return;
            }
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

        /** An 8-bit-per-channel 0xAARRGGBB pixel held in one int, the layout of every surface this backend draws. */
        private static boolean packedArgb(ColorModel model, Raster raster) {
            return model instanceof DirectColorModel direct
                    && direct.getTransferType() == DataBuffer.TYPE_INT
                    && raster.getDataBuffer().getDataType() == DataBuffer.TYPE_INT
                    && direct.getAlphaMask() == 0xff000000 && direct.getRedMask() == 0x00ff0000
                    && direct.getGreenMask() == 0x0000ff00 && direct.getBlueMask() == 0x000000ff
                    && direct.getColorSpace().isCS_sRGB();
        }

        /**
         * The per-pixel arithmetic of the general path above, a row at a time on packed ints. A premultiplied source
         * is un-premultiplied exactly as {@link DirectColorModel#getRGB(int)} does it, float for float, so the two
         * paths produce the same bits; the general path asked the colour model for every channel of every pixel and
         * was a seventh of a recording's time.
         */
        private void composePacked(Raster source, Raster destinationIn, WritableRaster destinationOut,
                                   int width, int height, boolean premultiplied) {
            int[] sourceRow = new int[width];
            int[] destinationRow = new int[width];
            for (int y = 0; y < height; y++) {
                source.getDataElements(source.getMinX(), source.getMinY() + y, width, 1, sourceRow);
                destinationIn.getDataElements(destinationIn.getMinX(), destinationIn.getMinY() + y, width, 1,
                        destinationRow);
                for (int x = 0; x < width; x++) {
                    int sourceArgb = sourceRow[x];
                    int alpha = sourceArgb >>> 24;
                    int red = sourceArgb >>> 16 & 0xff, green = sourceArgb >>> 8 & 0xff, blue = sourceArgb & 0xff;
                    if (premultiplied) {
                        red = unpremultiply(red, alpha);
                        green = unpremultiply(green, alpha);
                        blue = unpremultiply(blue, alpha);
                    }
                    int destinationArgb = destinationRow[x];
                    int sourceAlpha = Math.round(alpha * opacity);
                    int outputAlpha = Math.min(255, (destinationArgb >>> 24) + sourceAlpha * sourceAlpha / 255);
                    destinationRow[x] = outputAlpha << 24
                            | additiveChannel(destinationArgb >>> 16 & 0xff, red, sourceAlpha) << 16
                            | additiveChannel(destinationArgb >>> 8 & 0xff, green, sourceAlpha) << 8
                            | additiveChannel(destinationArgb & 0xff, blue, sourceAlpha);
                }
                destinationOut.setDataElements(destinationOut.getMinX(), destinationOut.getMinY() + y, width, 1,
                        destinationRow);
            }
        }

        private static int additiveChannel(int destination, int source, int sourceAlpha) {
            return Math.min(255, destination + source * sourceAlpha / 255);
        }
    }

    /** {@code DirectColorModel}'s own un-premultiply for an 8-bit sRGB channel, operation for operation. */
    static int unpremultiply(int channel, int alpha) {
        return alpha == 0 ? 0 : (int) (((channel * 1.0f) * 255.0f / (alpha * 1.0f)) + 0.5f);
    }

    private static Color opaque(Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue());
    }

    private static float clampAlpha(float alpha) {
        return Math.max(0f, Math.min(1f, alpha));
    }
}
