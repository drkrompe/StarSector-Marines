package com.dillon.starsectormarines.ui.retained.markup;

import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiTag;
import com.dillon.starsectormarines.ui.retained.reactive.ElementBindings;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import com.dillon.starsectormarines.ui.retained.style.StyleSheet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds ordinary retained elements from a parsed template. */
final class MarkupBuilder {
    private final Reactor reactor;
    private final ElementBindings bindings;
    private final MarkupRegistry registry;
    private final Map<String, UiElement> ids = new LinkedHashMap<>();
    private final Map<String, StyleSheet> styles = new LinkedHashMap<>();

    MarkupBuilder(Reactor reactor, MarkupRegistry registry) {
        this.reactor = Objects.requireNonNull(reactor, "reactor");
        this.bindings = new ElementBindings(reactor);
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    BuildResult build(MarkupTemplate template, MarkupScope scope) {
        requireProps(template, scope, template.root().line(), template.root().column());
        UiElement root = element(template.root(), new Frame(template, scope, null));
        return new BuildResult(root, List.copyOf(styles.values()), Map.copyOf(ids));
    }

    private UiElement element(MarkupElement source, Frame frame) {
        if (source.isComponent()) return component(source, frame);
        UiTag tag = switch (source.tagName()) {
            case "div" -> UiTag.DIV;
            case "button" -> UiTag.BUTTON;
            case "canvas" -> UiTag.CANVAS;
            default -> throw error(frame, source, "Unknown built-in element <" + source.tagName() + ">.");
        };

        MarkupAttribute idAttribute = source.attribute("id");
        if (idAttribute == null) {
            throw error(frame, source, "<" + source.tagName()
                    + "> needs an id in this first retained subset so Java and markup share one stable node identity.");
        }
        String id = valueOnce(idAttribute, frame);
        if (id == null || id.isBlank()) throw error(frame, idAttribute, "id must not be blank.");
        UiElement result = new UiElement(id).tag(tag);
        if (!idAttribute.isExpression()) {
            UiElement previous = ids.putIfAbsent(id, result);
            if (previous != null) throw error(frame, idAttribute, "id \"" + id + "\" occurs more than once.");
        }

        for (MarkupAttribute attribute : source.attributes()) {
            if (!"id".equals(attribute.name())) attribute(result, tag, attribute, frame);
        }
        result.addClass(frame.template().scopeClass());
        styles.putIfAbsent(frame.template().name(), frame.template().style());

        MarkupElement repeated = repeatedChild(source);
        if (repeated != null) {
            repeat(result, repeated, frame);
        } else if (containsElement(source.children())) {
            for (MarkupNode child : source.children()) {
                if (!(child instanceof MarkupElement)) {
                    throw error(frame, child, "Text and child elements cannot be mixed because UiElement owns one text value.");
                }
                result.child(element((MarkupElement) child, frame));
            }
        } else {
            bindText(result, source.children(), frame);
        }
        return result;
    }

    private void attribute(UiElement target, UiTag tag, MarkupAttribute attribute, Frame frame) {
        String name = attribute.name();
        try {
            switch (name) {
                case "class" -> bindClasses(target, attribute, frame);
                case "style" -> bindStyle(target, attribute, frame);
                case "tabindex" -> target.tabIndex(parseInteger(valueOnce(attribute, frame), attribute, frame));
                case "disabled" -> {
                    requireTag(tag, UiTag.BUTTON, attribute, frame);
                    if (attribute.isExpression()) {
                        bindings.disabled(target, () -> booleanValue(attribute, frame));
                    } else {
                        target.disabled(true);
                    }
                }
                case "onclick" -> {
                    requireTag(tag, UiTag.BUTTON, attribute, frame);
                    target.onClick(handler(attribute, frame));
                }
                case "width", "height" -> {
                    requireTag(tag, UiTag.CANVAS, attribute, frame);
                    if (attribute.isExpression()) {
                        throw error(frame, attribute, "Canvas surface " + name + " is structural and cannot be rebound.");
                    }
                    int width = "width".equals(name) ? parseNonNegative(attribute.value(), attribute, frame)
                            : target.canvasWidth();
                    int height = "height".equals(name) ? parseNonNegative(attribute.value(), attribute, frame)
                            : target.canvasHeight();
                    target.canvasSize(width, height);
                }
                default -> throw error(frame, attribute, "<" + tag.name().toLowerCase()
                        + "> does not accept attribute \"" + name + "\".");
            }
        } catch (UiMarkupException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw error(frame, attribute, failure.getMessage());
        }
    }

    private void bindClasses(UiElement target, MarkupAttribute attribute, Frame frame) {
        if (!attribute.isExpression()) {
            for (String token : tokens(attribute.value(), attribute, frame)) target.addClass(token);
            return;
        }
        List<String> owned = new ArrayList<>();
        reactor.bind(() -> {
            for (String token : owned) target.removeClass(token);
            owned.clear();
            String value = attribute.expression().asAttribute(frame.scope(), frame.fileName());
            if (value != null) {
                owned.addAll(tokens(value, attribute, frame));
                for (String token : owned) target.addClass(token);
            }
        });
    }

    private void bindStyle(UiElement target, MarkupAttribute attribute, Frame frame) {
        if (!attribute.isExpression()) {
            applyStyle(target, attribute.value(), attribute, frame);
        } else {
            reactor.bind(() -> {
                String value = attribute.expression().asAttribute(frame.scope(), frame.fileName());
                applyStyle(target, value == null ? "" : value, attribute, frame);
            });
        }
    }

    private static void applyStyle(UiElement target, String value,
                                   MarkupAttribute attribute, Frame frame) {
        try {
            target.style(value);
        } catch (RuntimeException failure) {
            throw error(frame, attribute, failure.getMessage());
        }
    }

    private Runnable handler(MarkupAttribute attribute, Frame frame) {
        if (!attribute.isExpression()) {
            throw error(frame, attribute, "onclick takes a {handler} prop; inline script is not supported.");
        }
        Object value = attribute.expression().evaluate(frame.scope(), frame.fileName());
        if (value instanceof Runnable) return (Runnable) value;
        throw error(frame, attribute, attribute.expression() + " resolved to "
                + (value == null ? "null" : value.getClass().getName()) + ", not Runnable.");
    }

    private void bindText(UiElement target, List<MarkupNode> nodes, Frame frame) {
        boolean reactive = false;
        for (MarkupNode node : nodes) if (node instanceof MarkupHole) reactive = true;
        if (reactive) reactor.bind(() -> target.text(text(nodes, frame)));
        else target.text(text(nodes, frame));
    }

    private String text(List<MarkupNode> nodes, Frame frame) {
        StringBuilder result = new StringBuilder();
        for (MarkupNode node : nodes) {
            if (node instanceof MarkupText) result.append(((MarkupText) node).text());
            else if (node instanceof MarkupHole) {
                result.append(((MarkupHole) node).expression().asText(frame.scope(), frame.fileName()));
            }
        }
        return result.toString();
    }

    private UiElement component(MarkupElement source, Frame parent) {
        MarkupTemplate template = registry.template(source.tagName());
        if (template == null) {
            throw error(parent, source, "<" + source.tagName() + "> is not registered. Available: "
                    + (registry.names().isEmpty() ? "none" : String.join(", ", registry.names())) + ".");
        }
        if (parent.instantiating(template.name())) {
            throw error(parent, source, "Recursive component path " + parent.chain(template.name()) + ".");
        }
        Map<String, Object> props = new LinkedHashMap<>();
        for (MarkupAttribute attribute : source.attributes()) {
            if (!template.props().contains(attribute.name())) {
                throw error(parent, attribute, "<" + source.tagName() + "> has no \""
                        + attribute.name() + "\" prop.");
            }
            Object value = attribute.isExpression()
                    ? (attribute.expression().isName()
                        ? attribute.expression().lookup(parent.scope(), parent.fileName())
                        : reactor.computed(() -> attribute.expression().evaluate(parent.scope(), parent.fileName())))
                    : attribute.value();
            props.put(attribute.name(), value);
        }
        MarkupScope scope = MarkupScope.of(props);
        requireProps(template, scope, source.line(), source.column());
        return element(template.root(), new Frame(template, scope, parent));
    }

    private void repeat(UiElement target, MarkupElement source, Frame frame) {
        MarkupLoop loop = source.loop();
        bindings.<Object>children(target,
                () -> items(loop, frame),
                item -> key(loop, frame, item),
                row -> element(source, frame.with(loop.variable(), row)));
    }

    private List<Object> items(MarkupLoop loop, Frame frame) {
        Object value = loop.items().evaluate(frame.scope(), frame.fileName());
        if (!(value instanceof List<?>)) {
            throw error(frame, loop.items().line(), loop.items().column(), loop.items() + " must resolve to a List.");
        }
        return new ArrayList<>((List<?>) value);
    }

    private String key(MarkupLoop loop, Frame frame, Object item) {
        Object value = loop.key().evaluate(frame.scope().with(loop.variable(), item), frame.fileName());
        if (value == null) throw error(frame, loop.key().line(), loop.key().column(), "A repeated key is null.");
        return String.valueOf(value);
    }

    private void requireProps(MarkupTemplate template, MarkupScope scope, int line, int column) {
        List<String> missing = new ArrayList<>();
        for (String prop : template.props()) if (!scope.has(prop)) missing.add(prop);
        if (!missing.isEmpty()) throw UiMarkupException.at(template.fileName(), line, column,
                "Missing props: " + String.join(", ", missing) + ".");
    }

    private String valueOnce(MarkupAttribute attribute, Frame frame) {
        return attribute.isExpression()
                ? attribute.expression().asAttribute(frame.scope(), frame.fileName())
                : attribute.value();
    }

    private static Boolean booleanValue(MarkupAttribute attribute, Frame frame) {
        Object value = attribute.expression().evaluate(frame.scope(), frame.fileName());
        if (value instanceof Boolean) return (Boolean) value;
        throw error(frame, attribute, attribute.expression() + " must resolve to a boolean.");
    }

    private static boolean containsElement(List<MarkupNode> nodes) {
        for (MarkupNode node : nodes) if (node instanceof MarkupElement) return true;
        return false;
    }

    private static MarkupElement repeatedChild(MarkupElement source) {
        if (source.children().size() != 1 || !(source.children().get(0) instanceof MarkupElement)) return null;
        MarkupElement child = (MarkupElement) source.children().get(0);
        return child.repeats() ? child : null;
    }

    private static List<String> tokens(String value, MarkupAttribute attribute, Frame frame) {
        List<String> result = new ArrayList<>();
        for (String token : value.trim().split("\\s+")) {
            if (!token.isBlank()) result.add(token);
        }
        if (result.stream().distinct().count() != result.size()) {
            throw error(frame, attribute, "class contains a duplicate token.");
        }
        return result;
    }

    private static int parseInteger(String value, MarkupAttribute attribute, Frame frame) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException failure) { throw error(frame, attribute, "Expected an integer, got \"" + value + "\"."); }
    }

    private static int parseNonNegative(String value, MarkupAttribute attribute, Frame frame) {
        int parsed = parseInteger(value, attribute, frame);
        if (parsed < 0) throw error(frame, attribute, "Expected a non-negative integer.");
        return parsed;
    }

    private static void requireTag(UiTag actual, UiTag expected, MarkupAttribute attribute, Frame frame) {
        if (actual != expected) throw error(frame, attribute, "Attribute \"" + attribute.name()
                + "\" is accepted only by <" + expected.name().toLowerCase() + ">.");
    }

    private static UiMarkupException error(Frame frame, MarkupNode node, String message) {
        return error(frame, node.line(), node.column(), message);
    }

    private static UiMarkupException error(Frame frame, MarkupAttribute attribute, String message) {
        return error(frame, attribute.line(), attribute.column(), message);
    }

    private static UiMarkupException error(Frame frame, int line, int column, String message) {
        return UiMarkupException.at(frame.fileName(), line, column, message);
    }

    record BuildResult(UiElement root, List<StyleSheet> styles, Map<String, UiElement> ids) { }

    private record Frame(MarkupTemplate template, MarkupScope scope, Frame parent) {
        String fileName() { return template.fileName(); }
        Frame with(String name, Signal<?> value) { return new Frame(template, scope.with(name, value), parent); }
        boolean instantiating(String name) {
            for (Frame frame = this; frame != null; frame = frame.parent) {
                if (frame.template.name().equals(name)) return true;
            }
            return false;
        }
        String chain(String name) {
            List<String> values = new ArrayList<>();
            for (Frame frame = this; frame != null; frame = frame.parent) values.add(0, frame.template.name());
            values.add(name);
            return String.join(" -> ", values);
        }
    }
}
