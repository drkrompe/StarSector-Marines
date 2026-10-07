package com.dillon.starsectormarines.ui.retained.svg;

import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasMesh;
import com.dillon.starsectormarines.ui.retained.CanvasMeshBatch;
import com.dillon.starsectormarines.ui.retained.CanvasMeshBatch.Layer;
import com.fs.starfarer.api.Global;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable SVG drawing compiled once into canvas geometry.
 *
 * <p>This is an explicit authored subset: one viewBox, groups, paths (all SVG
 * path commands), polygons, polylines, lines, rectangles and ellipses; affine
 * transforms; solid/currentColor fills; fill rules; strokes, joins, caps and
 * dash arrays. The viewBox defines this asset's viewport and aspect ratio;
 * the default xMidYMid meet mapping is aspect-fitted and viewport-clipped.
 * Unsupported tags, attributes or CSS declarations fail at load with a
 * diagnostic. Gradients, references, masks, clipping and group opacity need
 * a richer compositor and are deliberately outside this subset.
 * Shape opacity with both fill and stroke likewise requires compositing;
 * separate fill-opacity and stroke-opacity remain supported.
 *
 * <p>The strict path scanner is adapted from MoonLight Engine. Its DOM,
 * cascade and GPU dependencies do not cross this boundary. Strokes expand in
 * user space before each shape's transform; the resulting mesh is the same
 * for Starsector and headless painting. Curves flatten to at most 1/2048 of
 * the viewBox's larger dimension in user units.
 */
public final class SvgAsset {

    private static final String NAMESPACE = "http://www.w3.org/2000/svg";
    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private static final Pattern TRANSFORM = Pattern.compile("([A-Za-z]+)\\s*\\(([^)]*)\\)");
    private static final Set<String> PRESENTATION = Set.of("fill", "stroke", "color", "fill-rule",
            "fill-opacity", "stroke-opacity", "opacity", "stroke-width", "stroke-linecap",
            "stroke-linejoin", "stroke-miterlimit", "stroke-dasharray", "stroke-dashoffset");
    private static final Map<String, SvgAsset> CACHE = new HashMap<>();

    private final double minX;
    private final double minY;
    private final double width;
    private final double height;
    private final CanvasMeshBatch batch;

    private SvgAsset(double[] viewBox, List<Layer> layers) {
        minX = viewBox[0];
        minY = viewBox[1];
        width = viewBox[2];
        height = viewBox[3];
        batch = new CanvasMeshBatch(layers);
    }

    /** One load per path; call while constructing a producer, outside paint. */
    public static synchronized SvgAsset load(String modPath) {
        if (modPath == null || modPath.isBlank()) throw new IllegalArgumentException("SVG path required");
        SvgAsset cached = CACHE.get(modPath);
        if (cached != null) return cached;
        try {
            String source = Global.getSettings() == null
                    ? Files.readString(Path.of("mod").resolve(modPath))
                    : Global.getSettings().loadText(modPath);
            SvgAsset asset = parse(source);
            CACHE.put(modPath, asset);
            return asset;
        } catch (Exception failure) {
            throw new IllegalArgumentException("Could not load SVG " + modPath + ": "
                    + failure.getMessage(), failure);
        }
    }

    public static SvgAsset parse(String source) {
        if (source == null) throw new IllegalArgumentException("SVG source required");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Element root = factory.newDocumentBuilder().parse(new InputSource(new StringReader(source)))
                    .getDocumentElement();
            if (!tag(root).equals("svg")) throw error(root, "root must be <svg>");
            attributes(root, Set.of("viewBox", "width", "height", "preserveAspectRatio", "version"));
            double[] viewBox = numbers(required(root, "viewBox"));
            if (viewBox.length != 4 || viewBox[2] <= 0 || viewBox[3] <= 0) {
                throw error(root, "viewBox needs min-x min-y and positive width height");
            }
            String aspect = root.getAttribute("preserveAspectRatio").trim();
            if (!aspect.isEmpty() && !aspect.equals("xMidYMid") && !aspect.equals("xMidYMid meet")) {
                throw error(root, "only preserveAspectRatio=\"xMidYMid meet\" is supported");
            }
            if (root.hasAttribute("width")) positive(length(root.getAttribute("width")), "svg width");
            if (root.hasAttribute("height")) positive(length(root.getAttribute("height")), "svg height");
            if (root.hasAttribute("width") && root.hasAttribute("height")) {
                double aspectRatio = length(root.getAttribute("width")) / length(root.getAttribute("height"));
                if (Math.abs(aspectRatio - viewBox[2] / viewBox[3]) > 0.000001) {
                    throw error(root, "width/height aspect must match the viewBox for an aspect-fitted asset");
                }
            }
            List<Layer> layers = new ArrayList<>();
            double flatness = Math.max(viewBox[2], viewBox[3]) / 2048;
            Style rootStyle = style(root, Style.DEFAULT);
            requireContainerOpacity(root, rootStyle);
            Rectangle2D viewport = new Rectangle2D.Double(viewBox[0], viewBox[1], viewBox[2], viewBox[3]);
            walkChildren(root, rootStyle, transform(root), flatness, viewport, layers);
            return new SvgAsset(viewBox, layers);
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalArgumentException("Could not parse SVG: " + failure.getMessage(), failure);
        }
    }

    /** Emits cached geometry, aspect-fitted and centered inside this canvas box. */
    public void draw(CanvasContext context, float x, float y, float boxWidth, float boxHeight, Color tint) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(tint, "tint");
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(boxWidth)
                || !Float.isFinite(boxHeight) || boxWidth < 0 || boxHeight < 0) {
            throw new IllegalArgumentException("SVG destination must be finite with nonnegative extent");
        }
        double scale = Math.min(boxWidth / width, boxHeight / height);
        if (scale <= 0) return;
        float offsetX = (float) (x + (boxWidth - width * scale) / 2 - minX * scale);
        float offsetY = (float) (y + (boxHeight - height * scale) / 2 - minY * scale);
        context.meshBatch(batch, offsetX, offsetY, (float) scale, tint);
    }

    private static void walkChildren(Element parent, Style inherited, AffineTransform transform,
                                      double flatness, Rectangle2D viewport, List<Layer> layers) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) {
                walk(element, inherited, transform, flatness, viewport, layers);
            } else if (node.getNodeType() == Node.TEXT_NODE && !node.getTextContent().isBlank()) {
                throw error(parent, "text is unsupported; author geometry as paths");
            }
        }
    }

    private static void walk(Element element, Style inherited, AffineTransform parentTransform,
                              double flatness, Rectangle2D viewport, List<Layer> layers) {
        String tag = tag(element);
        if (tag.equals("title") || tag.equals("desc")) {
            attributes(element, Set.of());
            return;
        }
        Set<String> geometry = switch (tag) {
            case "g" -> Set.of();
            case "path" -> Set.of("d");
            case "polygon", "polyline" -> Set.of("points");
            case "rect" -> Set.of("x", "y", "width", "height", "rx", "ry");
            case "circle" -> Set.of("cx", "cy", "r");
            case "ellipse" -> Set.of("cx", "cy", "rx", "ry");
            case "line" -> Set.of("x1", "y1", "x2", "y2");
            default -> throw error(element, "unsupported SVG element; use paths or solid shapes");
        };
        attributes(element, geometry);
        Style style = style(element, inherited);
        AffineTransform transform = new AffineTransform(parentTransform);
        transform.concatenate(transform(element));
        if (tag.equals("g")) {
            requireContainerOpacity(element, style);
            walkChildren(element, style, transform, flatness, viewport, layers);
            return;
        }
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element || node.getNodeType() == Node.TEXT_NODE
                    && !node.getTextContent().isBlank()) {
                throw error(element, "shape children are unsupported");
            }
        }
        Shape shape = shape(element, tag, style.winding());
        finite(shape);
        if (style.opacity() != 1 && !style.fill().equals("none")
                && !style.stroke().equals("none") && style.strokeWidth() > 0) {
            throw error(element, "opacity with both fill and stroke needs compositing; use fill-opacity/stroke-opacity");
        }
        if (!style.fill().equals("none")) {
            addLayer(layers, transform.createTransformedShape(shape),
                    paint(style.fill(), style.color(), style.opacity() * style.fillOpacity()), flatness, viewport);
        }
        if (!style.stroke().equals("none") && style.strokeWidth() > 0) {
            BasicStroke stroke = new BasicStroke((float) style.strokeWidth(), style.cap(), style.join(),
                    (float) style.miterLimit(), style.dash(), style.dashPhase());
            addLayer(layers, transform.createTransformedShape(stroke.createStrokedShape(shape)),
                    paint(style.stroke(), style.color(), style.opacity() * style.strokeOpacity()), flatness, viewport);
        }
    }

    private static void addLayer(List<Layer> layers, Shape shape, Color color,
                                 double flatness, Rectangle2D viewport) {
        finite(shape);
        if (color.getAlpha() == 0) return;
        CanvasMesh mesh = SvgTessellator.mesh(shape, flatness, viewport);
        if (mesh.vertexCount() > 0) layers.add(new Layer(mesh, color));
    }

    private static Shape shape(Element element, String tag, int winding) {
        switch (tag) {
            case "path" -> {
                Path2D.Double path = SvgPathData.parse(required(element, "d"));
                path.setWindingRule(winding);
                return path;
            }
            case "polygon", "polyline" -> {
                double[] points = numbers(required(element, "points"));
                if (points.length % 2 != 0) throw error(element, "points need XY pairs");
                Path2D.Double path = new Path2D.Double(winding);
                if (points.length == 0) return path;
                path.moveTo(points[0], points[1]);
                for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
                if (tag.equals("polygon")) path.closePath();
                return path;
            }
            case "line" -> {
                return new Line2D.Double(value(element, "x1", 0), value(element, "y1", 0),
                        value(element, "x2", 0), value(element, "y2", 0));
            }
            case "rect" -> {
                double x = value(element, "x", 0), y = value(element, "y", 0);
                double width = nonnegative(value(element, "width", 0), "rect width");
                double height = nonnegative(value(element, "height", 0), "rect height");
                if (width == 0 || height == 0) return new Path2D.Double();
                double rx = nonnegative(value(element, "rx", value(element, "ry", 0)), "rect rx");
                double ry = nonnegative(value(element, "ry", rx), "rect ry");
                if (rx == 0 || ry == 0) return new Rectangle2D.Double(x, y, width, height);
                return new RoundRectangle2D.Double(x, y, width, height,
                        Math.min(rx, width / 2) * 2, Math.min(ry, height / 2) * 2);
            }
            case "circle", "ellipse" -> {
                double rx = nonnegative(value(element, tag.equals("circle") ? "r" : "rx", 0), "radius");
                double ry = tag.equals("circle") ? rx : nonnegative(value(element, "ry", 0), "radius");
                if (rx == 0 || ry == 0) return new Path2D.Double();
                return new Ellipse2D.Double(value(element, "cx", 0) - rx,
                        value(element, "cy", 0) - ry, rx * 2, ry * 2);
            }
            default -> throw new IllegalStateException("unhandled SVG shape " + tag);
        }
    }

    private static Style style(Element element, Style inherited) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : PRESENTATION) {
            if (element.hasAttribute(key)) values.put(key, element.getAttribute(key).trim());
        }
        String css = element.getAttribute("style");
        for (String declaration : css.split(";")) {
            if (declaration.isBlank()) continue;
            String[] pair = declaration.split(":", 2);
            if (pair.length != 2 || !PRESENTATION.contains(pair[0].trim())) {
                throw error(element, "unsupported style declaration " + declaration);
            }
            values.put(pair[0].trim(), pair[1].trim());
        }
        String fill = values.getOrDefault("fill", inherited.fill());
        String stroke = values.getOrDefault("stroke", inherited.stroke());
        Color color = values.containsKey("color") ? color(values.get("color")) : inherited.color();
        paint(fill, color, 1);
        paint(stroke, color, 1);
        int winding = switch (values.getOrDefault("fill-rule", inherited.winding() == Path2D.WIND_EVEN_ODD ? "evenodd" : "nonzero")) {
            case "evenodd" -> Path2D.WIND_EVEN_ODD;
            case "nonzero" -> Path2D.WIND_NON_ZERO;
            default -> throw error(element, "fill-rule must be nonzero or evenodd");
        };
        int cap = values.containsKey("stroke-linecap") ? switch (values.get("stroke-linecap")) {
            case "butt" -> BasicStroke.CAP_BUTT;
            case "round" -> BasicStroke.CAP_ROUND;
            case "square" -> BasicStroke.CAP_SQUARE;
            default -> throw error(element, "unsupported stroke-linecap");
        } : inherited.cap();
        int join = values.containsKey("stroke-linejoin") ? switch (values.get("stroke-linejoin")) {
            case "miter" -> BasicStroke.JOIN_MITER;
            case "round" -> BasicStroke.JOIN_ROUND;
            case "bevel" -> BasicStroke.JOIN_BEVEL;
            default -> throw error(element, "unsupported stroke-linejoin");
        } : inherited.join();
        double strokeWidth = nonnegative(number(values, "stroke-width", inherited.strokeWidth()), "stroke-width");
        double miterLimit = scalar(values, "stroke-miterlimit", inherited.miterLimit());
        if (miterLimit < 1) throw error(element, "stroke-miterlimit must be at least 1");
        float[] dash = values.containsKey("stroke-dasharray") ? dash(values.get("stroke-dasharray")) : inherited.dash();
        double dashOffset = number(values, "stroke-dashoffset", inherited.dashOffset());
        return new Style(fill, stroke, color, winding,
                opacity(scalar(values, "fill-opacity", inherited.fillOpacity())),
                opacity(scalar(values, "stroke-opacity", inherited.strokeOpacity())),
                opacity(scalar(values, "opacity", 1)), strokeWidth, cap, join, miterLimit, dash, dashOffset);
    }

    private static float[] dash(String source) {
        if (source.equals("none")) return null;
        double[] values = numbers(source);
        if (values.length == 0) throw new IllegalArgumentException("SVG stroke-dasharray must have lengths");
        float[] dash = new float[values.length % 2 == 0 ? values.length : values.length * 2];
        double sum = 0;
        for (int i = 0; i < dash.length; i++) {
            dash[i] = (float) nonnegative(values[i % values.length], "dash length");
            sum += dash[i];
        }
        return sum == 0 ? null : dash;
    }

    private static void requireContainerOpacity(Element element, Style style) {
        if (style.opacity() != 1) throw error(element, "group opacity needs compositing; set opacity on shapes");
    }

    private static Color paint(String value, Color currentColor, double opacity) {
        Color color = switch (value) {
            case "none" -> new Color(0, 0, 0, 0);
            case "currentColor" -> currentColor;
            default -> color(value);
        };
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                (int) Math.round(color.getAlpha() * opacity));
    }

    private static Color color(String source) {
        return switch (source) {
            case "black" -> Color.BLACK;
            case "white" -> Color.WHITE;
            case "red" -> Color.RED;
            case "green" -> new Color(0, 128, 0);
            case "blue" -> Color.BLUE;
            case "gray", "grey" -> Color.GRAY;
            case "transparent" -> new Color(0, 0, 0, 0);
            default -> {
                if (!source.matches("#[0-9a-fA-F]{3}(?:[0-9a-fA-F])?|#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?")) {
                    throw new IllegalArgumentException("SVG solid color needs supported name or hex RGB: " + source);
                }
                String hex = source.substring(1);
                if (hex.length() < 5) {
                    StringBuilder expanded = new StringBuilder();
                    for (char c : hex.toCharArray()) expanded.append(c).append(c);
                    hex = expanded.toString();
                }
                long rgb = Long.parseLong(hex, 16);
                yield hex.length() == 6 ? new Color((int) rgb)
                        : new Color((int) (rgb >> 24) & 255, (int) (rgb >> 16) & 255,
                        (int) (rgb >> 8) & 255, (int) rgb & 255);
            }
        };
    }

    private static AffineTransform transform(Element element) {
        String source = element.getAttribute("transform").trim();
        AffineTransform result = new AffineTransform();
        int at = 0;
        Matcher match = TRANSFORM.matcher(source);
        while (match.find()) {
            if (!source.substring(at, match.start()).matches("[\\s,]*")) {
                throw error(element, "malformed transform " + source);
            }
            double[] args = numbers(match.group(2));
            AffineTransform next = switch (match.group(1)) {
                case "matrix" -> {
                    count(args, 6);
                    yield new AffineTransform(args);
                }
                case "translate" -> {
                    count(args, 1, 2);
                    yield AffineTransform.getTranslateInstance(args[0], args.length == 1 ? 0 : args[1]);
                }
                case "scale" -> {
                    count(args, 1, 2);
                    yield AffineTransform.getScaleInstance(args[0], args.length == 1 ? args[0] : args[1]);
                }
                case "rotate" -> {
                    count(args, 1, 3);
                    yield args.length == 1 ? AffineTransform.getRotateInstance(Math.toRadians(args[0]))
                            : AffineTransform.getRotateInstance(Math.toRadians(args[0]), args[1], args[2]);
                }
                case "skewX" -> {
                    count(args, 1);
                    yield AffineTransform.getShearInstance(Math.tan(Math.toRadians(args[0])), 0);
                }
                case "skewY" -> {
                    count(args, 1);
                    yield AffineTransform.getShearInstance(0, Math.tan(Math.toRadians(args[0])));
                }
                default -> throw error(element, "unsupported transform " + match.group(1));
            };
            result.concatenate(next);
            at = match.end();
        }
        if (!source.substring(at).isBlank()) throw error(element, "malformed transform " + source);
        return result;
    }

    private static void count(double[] args, int... accepted) {
        for (int count : accepted) if (args.length == count) return;
        throw new IllegalArgumentException("SVG transform has wrong argument count");
    }

    private static double[] numbers(String source) {
        List<Double> values = new ArrayList<>();
        Matcher match = NUMBER.matcher(source);
        int at = 0;
        while (match.find()) {
            String separator = source.substring(at, match.start());
            if (!separator.matches("[\\s,]*") || separator.chars().filter(c -> c == ',').count() > 1
                    || (values.isEmpty() && separator.contains(","))) {
                throw new IllegalArgumentException("Malformed SVG number list: " + source);
            }
            double value = Double.parseDouble(match.group());
            if (!Float.isFinite((float) value)) throw new IllegalArgumentException("SVG number must be finite");
            values.add(value);
            at = match.end();
        }
        if (!source.substring(at).isBlank()) throw new IllegalArgumentException("Malformed SVG number list: " + source);
        double[] result = new double[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i);
        return result;
    }

    private static double length(String source) {
        String numeric = source.endsWith("px") ? source.substring(0, source.length() - 2) : source;
        if (!NUMBER.matcher(numeric.trim()).matches()) throw new IllegalArgumentException("SVG length needs user units or px: " + source);
        double value = Double.parseDouble(numeric.trim());
        if (!Float.isFinite((float) value)) throw new IllegalArgumentException("SVG length must be finite");
        return value;
    }

    private static double value(Element element, String name, double fallback) {
        return element.hasAttribute(name) ? length(element.getAttribute(name)) : fallback;
    }

    private static double number(Map<String, String> values, String name, double fallback) {
        return values.containsKey(name) ? length(values.get(name)) : fallback;
    }

    private static double scalar(Map<String, String> values, String name, double fallback) {
        if (!values.containsKey(name)) return fallback;
        String source = values.get(name);
        if (!NUMBER.matcher(source).matches()) {
            throw new IllegalArgumentException("SVG " + name + " needs a unitless number: " + source);
        }
        return length(source);
    }

    private static double opacity(double value) { return Math.max(0, Math.min(1, value)); }
    private static double nonnegative(double value, String label) {
        if (value < 0) throw new IllegalArgumentException("SVG " + label + " cannot be negative");
        return value;
    }
    private static double positive(double value, String label) {
        if (value <= 0) throw new IllegalArgumentException("SVG " + label + " must be positive");
        return value;
    }

    private static String required(Element element, String name) {
        if (!element.hasAttribute(name)) throw error(element, "missing " + name);
        return element.getAttribute(name);
    }

    private static String tag(Element element) {
        String namespace = element.getNamespaceURI();
        if (namespace != null && !namespace.equals(NAMESPACE)) throw error(element, "unsupported namespace " + namespace);
        return element.getLocalName() == null ? element.getTagName() : element.getLocalName();
    }

    private static void attributes(Element element, Set<String> geometry) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String name = attribute.getNodeName();
            if (name.equals("xmlns") || name.startsWith("xmlns:")) continue;
            if (!geometry.contains(name) && !PRESENTATION.contains(name)
                    && !name.equals("id") && !name.equals("style") && !name.equals("transform")) {
                throw error(element, "unsupported attribute " + name);
            }
        }
    }

    private static void finite(Shape shape) {
        PathIterator path = shape.getPathIterator(null);
        double[] point = new double[6];
        while (!path.isDone()) {
            int count = switch (path.currentSegment(point)) {
                case PathIterator.SEG_MOVETO, PathIterator.SEG_LINETO -> 2;
                case PathIterator.SEG_QUADTO -> 4;
                case PathIterator.SEG_CUBICTO -> 6;
                default -> 0;
            };
            for (int i = 0; i < count; i++) {
                if (!Float.isFinite((float) point[i])) throw new IllegalArgumentException("SVG transformed geometry must be finite");
            }
            path.next();
        }
    }

    private static IllegalArgumentException error(Element element, String message) {
        return new IllegalArgumentException("SVG <" + element.getTagName() + ">: " + message);
    }

    private record Style(String fill, String stroke, Color color, int winding,
                         double fillOpacity, double strokeOpacity, double opacity,
                         double strokeWidth, int cap, int join, double miterLimit,
                         float[] dash, double dashOffset) {
        private static final Style DEFAULT = new Style("black", "none", Color.BLACK,
                Path2D.WIND_NON_ZERO, 1, 1, 1, 1, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER, 4, null, 0);

        float dashPhase() {
            if (dash == null) return 0;
            double period = 0;
            for (float length : dash) period += length;
            return (float) ((dashOffset % period + period) % period);
        }
    }
}
