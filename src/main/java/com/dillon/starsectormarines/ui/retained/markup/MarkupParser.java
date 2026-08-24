package com.dillon.starsectormarines.ui.retained.markup;

import com.dillon.starsectormarines.ui.retained.style.StyleSheet;
import com.dillon.starsectormarines.ui.retained.style.UiStyleException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict, positioned parser for the Marine Ops {@code .mlx} syntax subset. */
public final class MarkupParser {

    private static final String TEMPLATE = "template";
    private static final String STYLE = "style";
    private static final String PROPS = "props";
    private static final String EACH = "each";
    private static final String KEY = "key";

    private final String fileName;
    private final String source;
    private int cursor;
    private int line = 1;
    private int column = 1;

    private MarkupParser(String fileName, String source) {
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank");
        if (source == null) throw new IllegalArgumentException("source must not be null");
        this.fileName = fileName;
        this.source = source;
    }

    public static MarkupTemplate parse(String fileName, String source) {
        return new MarkupParser(fileName, source).parseFile();
    }

    private MarkupTemplate parseFile() {
        String componentName = componentName();
        MarkupElement root = null;
        List<String> props = List.of();
        StyleSheet sheet = null;

        skipTrivia();
        while (!atEnd()) {
            int openLine = line;
            int openColumn = column;
            expect('<', "a top-level <template> or <style>");
            String name = readName();
            if (TEMPLATE.equals(name)) {
                if (root != null) throw error(openLine, openColumn, "A file declares one <template>, and this is the second.");
                List<MarkupAttribute> attributes = readAttributes(name);
                props = templateProps(attributes);
                expectTagEnd(name, openLine, false);
                root = templateRoot(parseChildren(name, openLine, openColumn), openLine, openColumn);
            } else if (STYLE.equals(name)) {
                if (sheet != null) throw error(openLine, openColumn, "A file declares one <style>, and this is the second.");
                List<MarkupAttribute> attributes = readAttributes(name);
                if (!attributes.isEmpty()) throw error(attributes.get(0).line(), attributes.get(0).column(), "<style> takes no attributes.");
                expectTagEnd(name, openLine, false);
                String css = readRawUntilClose(name, openLine, openColumn);
                try {
                    sheet = StyleSheet.parse(fileName, css).scopedTo(MarkupTemplateScope.of(componentName));
                } catch (UiStyleException failure) {
                    throw error(openLine, openColumn, failure.getMessage());
                }
            } else {
                throw error(openLine, openColumn, "<" + name + "> is not allowed at the top level. "
                        + "A .mlx file holds one <template> and an optional <style>.");
            }
            skipTrivia();
        }

        if (root == null) throw error(1, 1, "The file has no <template>, so it defines no component.");
        rejectUndeclaredNames(root, props);
        if (sheet == null) sheet = new StyleSheet(fileName, List.of());
        return new MarkupTemplate(componentName, fileName, props, root, sheet);
    }

    private String componentName() {
        String base = fileName;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) base = base.substring(slash + 1);
        if (base.endsWith(".mlx")) base = base.substring(0, base.length() - 4);
        if (base.indexOf('-') < 0) {
            throw error(1, 1, "\"" + base + "\" cannot name a component: a custom element name needs a hyphen.");
        }
        return base;
    }

    private List<String> templateProps(List<MarkupAttribute> attributes) {
        List<String> result = new ArrayList<>();
        for (MarkupAttribute attribute : attributes) {
            if (!PROPS.equals(attribute.name())) {
                throw error(attribute.line(), attribute.column(), "<template> takes only \"props\", not \""
                        + attribute.name() + "\".");
            }
            if (attribute.isExpression()) {
                throw error(attribute.line(), attribute.column(), "\"props\" declares names and cannot be an expression.");
            }
            String[] names = attribute.value().split(",", -1);
            for (String candidate : names) {
                String name = candidate.trim();
                if (!isIdentifier(name)) {
                    throw error(attribute.line(), attribute.column(), "\"" + name
                            + "\" is not a valid prop name.");
                }
                if (result.contains(name)) {
                    throw error(attribute.line(), attribute.column(), "\"props\" declares \"" + name + "\" twice.");
                }
                result.add(name);
            }
        }
        return List.copyOf(result);
    }

    private MarkupElement templateRoot(List<MarkupNode> children, int openLine, int openColumn) {
        MarkupElement root = null;
        for (MarkupNode child : children) {
            if (child instanceof MarkupElement) {
                MarkupElement element = (MarkupElement) child;
                if (root != null) {
                    throw error(element.line(), element.column(), "A <template> has one root element, and this is the second.");
                }
                root = element;
            } else if (child instanceof MarkupHole) {
                throw error(child.line(), child.column(), "A <template> has one root element, so an expression must be inside it.");
            } else {
                MarkupText text = (MarkupText) child;
                if (!text.text().isBlank()) {
                    throw error(text.line(), text.column(), "A <template> has one root element, so it cannot hold loose text.");
                }
            }
        }
        if (root == null) throw error(openLine, openColumn, "The <template> is empty, so it builds nothing.");
        if (root.repeats()) {
            throw error(root.line(), root.column(), "A repeated element cannot be the template root because it has no parent child-list to own.");
        }
        return root;
    }

    private List<MarkupNode> parseChildren(String parentName, int parentLine, int parentColumn) {
        List<MarkupNode> children = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int textLine = line;
        int textColumn = column;
        while (true) {
            if (atEnd()) {
                throw error(parentLine, parentColumn, "<" + parentName + "> opened on line "
                        + parentLine + " is never closed.");
            }
            if (peek() == '<') {
                if (lookingAt("</")) {
                    flushText(children, text, textLine, textColumn);
                    readCloseTag(parentName, parentLine);
                    return normalizeWhitespace(children);
                }
                if (lookingAt("<!--")) {
                    skipComment();
                    continue;
                }
                flushText(children, text, textLine, textColumn);
                int openLine = line;
                int openColumn = column;
                advance();
                children.add(parseElement(readName(), openLine, openColumn));
                textLine = line;
                textColumn = column;
            } else if (peek() == '{') {
                flushText(children, text, textLine, textColumn);
                int expressionLine = line;
                int expressionColumn = column;
                children.add(new MarkupHole(readExpression(), expressionLine, expressionColumn));
                textLine = line;
                textColumn = column;
            } else {
                if (text.length() == 0) {
                    textLine = line;
                    textColumn = column;
                }
                text.append(readTextCharacter());
            }
        }
    }

    private MarkupElement parseElement(String tagName, int openLine, int openColumn) {
        requireKnownTag(tagName, openLine, openColumn);
        List<MarkupAttribute> declared = readAttributes(tagName);
        MarkupLoop loop = readLoop(tagName, declared);
        List<MarkupAttribute> attributes = withoutLoopAttributes(declared);
        boolean selfClosed = expectTagEnd(tagName, openLine, true);

        if ("canvas".equals(tagName) && !selfClosed) {
            throw error(openLine, openColumn, "<canvas> takes no children in this retained subset; write <canvas ... />.");
        }
        List<MarkupNode> children = selfClosed ? List.of() : parseChildren(tagName, openLine, openColumn);
        if (tagName.indexOf('-') >= 0 && !children.isEmpty()) {
            MarkupNode first = children.get(0);
            throw error(first.line(), first.column(), "<" + tagName + "> is a component and components take no children.");
        }
        rejectRepeatedSibling(tagName, children);
        return new MarkupElement(tagName, attributes, loop, children, openLine, openColumn);
    }

    private void requireKnownTag(String name, int errorLine, int errorColumn) {
        if ("div".equals(name) || "button".equals(name) || "canvas".equals(name) || name.indexOf('-') >= 0) return;
        throw error(errorLine, errorColumn, "Unknown element <" + name
                + ">. Built-in elements are div, button, and canvas; component names contain a hyphen.");
    }

    private MarkupLoop readLoop(String tagName, List<MarkupAttribute> attributes) {
        MarkupAttribute each = null;
        MarkupAttribute key = null;
        for (MarkupAttribute attribute : attributes) {
            if (EACH.equals(attribute.name())) each = attribute;
            if (KEY.equals(attribute.name())) key = attribute;
        }
        if (each == null && key == null) return null;
        if (each == null) throw error(key.line(), key.column(), "\"key\" requires an \"each\" repetition.");
        if (key == null) throw error(each.line(), each.column(), "<" + tagName + "> repeats, so it also needs a \"key\".");
        if (each.isExpression()) {
            throw error(each.line(), each.column(), "\"each\" is written \"item in items\", not as an expression.");
        }
        String[] words = each.value().trim().split("\\s+");
        if (words.length != 3 || !"in".equals(words[1]) || !isIdentifier(words[0])) {
            throw error(each.line(), each.column(), "\"each\" is written \"item in items\".");
        }
        MarkupExpression items = pathExpression(words[2], "each=\"" + each.value() + "\"", each.line(), each.column());
        if (!key.isExpression()) {
            throw error(key.line(), key.column(), "\"key\" must be a whole expression reading the repeated item.");
        }
        if (!words[0].equals(key.expression().rootName())) {
            throw error(key.line(), key.column(), key.expression() + " does not read the repeated item \"" + words[0] + "\".");
        }
        return new MarkupLoop(words[0], items, key.expression());
    }

    private static List<MarkupAttribute> withoutLoopAttributes(List<MarkupAttribute> attributes) {
        List<MarkupAttribute> result = new ArrayList<>();
        for (MarkupAttribute attribute : attributes) {
            if (!EACH.equals(attribute.name()) && !KEY.equals(attribute.name())) result.add(attribute);
        }
        return List.copyOf(result);
    }

    private void rejectRepeatedSibling(String parentName, List<MarkupNode> children) {
        if (children.size() < 2) return;
        for (MarkupNode child : children) {
            if (child instanceof MarkupElement && ((MarkupElement) child).repeats()) {
                MarkupElement repeated = (MarkupElement) child;
                throw error(repeated.line(), repeated.column(), "<" + repeated.tagName()
                        + "> repeats and therefore must be the only child of <" + parentName + ">.");
            }
        }
    }

    private void rejectUndeclaredNames(MarkupNode node, List<String> visible) {
        if (node instanceof MarkupHole) {
            requireVisible(((MarkupHole) node).expression(), visible);
            return;
        }
        if (!(node instanceof MarkupElement)) return;
        MarkupElement element = (MarkupElement) node;
        List<String> inner = visible;
        if (element.repeats()) {
            requireVisible(element.loop().items(), visible);
            inner = new ArrayList<>(visible);
            if (!inner.contains(element.loop().variable())) inner.add(element.loop().variable());
            requireVisible(element.loop().key(), inner);
        }
        for (MarkupAttribute attribute : element.attributes()) {
            if (attribute.isExpression()) requireVisible(attribute.expression(), inner);
        }
        for (MarkupNode child : element.children()) rejectUndeclaredNames(child, inner);
    }

    private void requireVisible(MarkupExpression expression, List<String> visible) {
        if (visible.contains(expression.rootName())) return;
        throw error(expression.line(), expression.column(), expression + " reads \""
                + expression.rootName() + "\", which is not in scope here. In scope: "
                + (visible.isEmpty() ? "nothing" : String.join(", ", visible)) + ".");
    }

    private List<MarkupAttribute> readAttributes(String tagName) {
        List<MarkupAttribute> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        while (true) {
            skipWhitespace();
            if (atEnd() || peek() == '>' || lookingAt("/>")) return result;
            int nameLine = line;
            int nameColumn = column;
            String name = readName();
            if (!names.add(name)) {
                throw error(nameLine, nameColumn, "<" + tagName + "> declares \"" + name + "\" twice.");
            }
            skipWhitespace();
            expect('=', "= after attribute \"" + name + "\"");
            skipWhitespace();
            result.add(readAttributeValue(name, nameLine, nameColumn));
        }
    }

    private MarkupAttribute readAttributeValue(String name, int nameLine, int nameColumn) {
        if (atEnd() || (peek() != '\"' && peek() != '\'')) {
            throw error(line, column, "Attribute \"" + name + "\" needs a quoted value.");
        }
        char quote = peek();
        advance();
        if (!atEnd() && peek() == '{') {
            MarkupExpression expression = readExpression();
            if (atEnd() || peek() != quote) {
                throw error(nameLine, nameColumn, "Attribute \"" + name
                        + "\" is either a literal or one whole expression; it cannot mix both.");
            }
            advance();
            return new MarkupAttribute(name, "", expression, nameLine, nameColumn);
        }
        StringBuilder value = new StringBuilder();
        while (!atEnd() && peek() != quote) {
            if (peek() == '{') {
                throw error(line, column, "Attribute \"" + name
                        + "\" is either a literal or one whole expression; it cannot mix both.");
            }
            value.append(readTextCharacter());
        }
        if (atEnd()) throw error(nameLine, nameColumn, "Attribute \"" + name + "\" is never closed.");
        advance();
        return new MarkupAttribute(name, value.toString(), nameLine, nameColumn);
    }

    private MarkupExpression readExpression() {
        int startLine = line;
        int startColumn = column;
        int startCursor = cursor;
        expect('{', "an expression");
        boolean negated = false;
        if (!atEnd() && peek() == '!') {
            negated = true;
            advance();
        }
        List<String> path = new ArrayList<>();
        path.add(readExpressionName(startLine, startColumn));
        while (!atEnd() && peek() == '.') {
            advance();
            path.add(readExpressionName(startLine, startColumn));
        }
        if (atEnd() || peek() != '}') {
            throw error(startLine, startColumn, "Expression \"" + source.substring(startCursor, cursor)
                    + "\" is not a dotted path; expressions support only optional ! and names.");
        }
        advance();
        return new MarkupExpression(negated, path, source.substring(startCursor, cursor), startLine, startColumn);
    }

    private String readExpressionName(int startLine, int startColumn) {
        int start = cursor;
        while (!atEnd() && isIdentifierCharacter(peek())) advance();
        String name = source.substring(start, cursor);
        if (!isIdentifier(name)) {
            throw error(startLine, startColumn, "An expression is a dotted path of names, with optional !.");
        }
        return name;
    }

    private MarkupExpression pathExpression(String text, String written, int expressionLine, int expressionColumn) {
        List<String> path = new ArrayList<>();
        for (String segment : text.split("\\.", -1)) {
            if (!isIdentifier(segment)) {
                throw error(expressionLine, expressionColumn, "\"" + text + "\" is not a dotted name path.");
            }
            path.add(segment);
        }
        return new MarkupExpression(false, path, written, expressionLine, expressionColumn);
    }

    private void readCloseTag(String expected, int openLine) {
        expect('<', "closing tag for <" + expected + ">");
        expect('/', "closing tag for <" + expected + ">");
        int closeLine = line;
        int closeColumn = column;
        String actual = readName();
        skipWhitespace();
        expect('>', "> after </" + actual);
        if (!expected.equals(actual)) {
            throw error(closeLine, closeColumn, "Expected </" + expected + "> for the element opened on line "
                    + openLine + ", not </" + actual + ">.");
        }
    }

    private boolean expectTagEnd(String tagName, int openLine, boolean allowSelfClose) {
        skipWhitespace();
        if (allowSelfClose && lookingAt("/>")) {
            advance();
            advance();
            return true;
        }
        if (atEnd() || peek() != '>') {
            throw error(openLine, column, "<" + tagName + "> needs a closing >.");
        }
        advance();
        return false;
    }

    private String readRawUntilClose(String tagName, int openLine, int openColumn) {
        String close = "</" + tagName + ">";
        int end = source.indexOf(close, cursor);
        if (end < 0) throw error(openLine, openColumn, "<" + tagName + "> is never closed.");
        int start = cursor;
        while (cursor < end) advance();
        String result = source.substring(start, end);
        for (int index = 0; index < close.length(); index++) advance();
        return result;
    }

    private static List<MarkupNode> normalizeWhitespace(List<MarkupNode> children) {
        List<MarkupNode> result = new ArrayList<>(children.size());
        boolean hasElement = false;
        for (MarkupNode child : children) if (child instanceof MarkupElement) hasElement = true;
        for (MarkupNode child : children) {
            if (!(child instanceof MarkupText)) {
                result.add(child);
                continue;
            }
            MarkupText text = (MarkupText) child;
            String collapsed = collapseWhitespace(text.text());
            if (hasElement && collapsed.isBlank()) continue;
            result.add(new MarkupText(collapsed, text.line(), text.column()));
        }
        if (!result.isEmpty() && result.get(0) instanceof MarkupText) {
            MarkupText first = (MarkupText) result.get(0);
            result.set(0, new MarkupText(first.text().stripLeading(), first.line(), first.column()));
        }
        if (!result.isEmpty() && result.get(result.size() - 1) instanceof MarkupText) {
            int index = result.size() - 1;
            MarkupText last = (MarkupText) result.get(index);
            result.set(index, new MarkupText(last.text().stripTrailing(), last.line(), last.column()));
        }
        result.removeIf(node -> node instanceof MarkupText && ((MarkupText) node).text().isEmpty());
        return List.copyOf(result);
    }

    private static String collapseWhitespace(String text) {
        StringBuilder result = new StringBuilder(text.length());
        boolean whitespace = false;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (Character.isWhitespace(value)) {
                whitespace = true;
            } else {
                if (whitespace && result.length() > 0) result.append(' ');
                result.append(value);
                whitespace = false;
            }
        }
        if (whitespace && result.length() > 0) result.append(' ');
        return result.toString();
    }

    private static void flushText(List<MarkupNode> children, StringBuilder text, int textLine, int textColumn) {
        if (text.length() == 0) return;
        children.add(new MarkupText(text.toString(), textLine, textColumn));
        text.setLength(0);
    }

    private char readTextCharacter() {
        if (peek() != '&') {
            char value = peek();
            advance();
            return value;
        }
        int entityLine = line;
        int entityColumn = column;
        advance();
        int start = cursor;
        while (!atEnd() && peek() != ';' && cursor - start <= 12) advance();
        if (atEnd() || peek() != ';') throw error(entityLine, entityColumn, "Character entity is never closed with ;.");
        String name = source.substring(start, cursor);
        advance();
        int decoded = decodeEntity(name);
        if (decoded < 0) {
            throw error(entityLine, entityColumn, "Unknown character entity &" + name
                    + ";. Supported: amp, lt, gt, quot, apos, and numeric forms.");
        }
        return (char) decoded;
    }

    private static int decodeEntity(String name) {
        if ("amp".equals(name)) return '&';
        if ("lt".equals(name)) return '<';
        if ("gt".equals(name)) return '>';
        if ("quot".equals(name)) return '\"';
        if ("apos".equals(name)) return '\'';
        if (!name.startsWith("#")) return -1;
        boolean hex = name.length() > 1 && (name.charAt(1) == 'x' || name.charAt(1) == 'X');
        try {
            int value = Integer.parseInt(name.substring(hex ? 2 : 1), hex ? 16 : 10);
            return Character.isBmpCodePoint(value) ? value : -1;
        } catch (NumberFormatException failure) {
            return -1;
        }
    }

    private void skipTrivia() {
        while (true) {
            skipWhitespace();
            if (!lookingAt("<!--")) return;
            skipComment();
        }
    }

    private void skipComment() {
        int commentLine = line;
        int commentColumn = column;
        for (int index = 0; index < 4; index++) advance();
        while (!atEnd() && !lookingAt("-->")) advance();
        if (atEnd()) throw error(commentLine, commentColumn, "Comment is never closed.");
        advance();
        advance();
        advance();
    }

    private void skipWhitespace() {
        while (!atEnd() && Character.isWhitespace(peek())) advance();
    }

    private String readName() {
        int start = cursor;
        while (!atEnd() && isNameCharacter(peek())) advance();
        if (start == cursor) throw error(line, column, "Expected a name.");
        return source.substring(start, cursor);
    }

    private static boolean isNameCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '-' || value == '_';
    }

    private static boolean isIdentifierCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private static boolean isIdentifier(String value) {
        if (value == null || value.isEmpty() || Character.isDigit(value.charAt(0))) return false;
        for (int index = 0; index < value.length(); index++) {
            if (!isIdentifierCharacter(value.charAt(index))) return false;
        }
        return true;
    }

    private void expect(char expected, String description) {
        if (atEnd() || peek() != expected) throw error(line, column, "Expected " + description + ".");
        advance();
    }

    private boolean lookingAt(String value) {
        return source.startsWith(value, cursor);
    }

    private char peek() {
        return source.charAt(cursor);
    }

    private boolean atEnd() {
        return cursor >= source.length();
    }

    private void advance() {
        if (atEnd()) return;
        char value = source.charAt(cursor++);
        if (value == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
    }

    private UiMarkupException error(int errorLine, int errorColumn, String message) {
        return UiMarkupException.at(fileName, errorLine, errorColumn, message);
    }

    /** Keeps the generated scoping convention local without exposing a second public API. */
    private static final class MarkupTemplateScope {
        private MarkupTemplateScope() { }

        static String of(String componentName) {
            return "mlx-" + componentName;
        }
    }
}
