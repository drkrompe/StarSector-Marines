package com.dillon.starsectormarines.tools.layerauthoring;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Replaces changed JSON scalars while retaining the source document's exact layout. */
public final class JsonTextPatcher {

    private final String source;
    private final StringBuilder output;
    private int position;

    private JsonTextPatcher(String source) {
        this.source = source;
        output = new StringBuilder(source.length());
    }

    public static String patch(String source, JSONObject desired) throws JSONException {
        JsonTextPatcher patcher = new JsonTextPatcher(source);
        patcher.patchValue(desired);
        patcher.copyWhitespace();
        if (patcher.position != source.length()) {
            throw new IllegalArgumentException("Unexpected content after JSON document");
        }
        return patcher.output.toString();
    }

    private void patchValue(Object desired) throws JSONException {
        copyWhitespace();
        if (desired instanceof JSONObject object) {
            patchObject(object);
        } else if (desired instanceof JSONArray array) {
            patchArray(array);
        } else {
            patchScalar(desired);
        }
    }

    private void patchObject(JSONObject desired) throws JSONException {
        copy('{');
        copyWhitespace();
        Set<String> sourceKeys = new HashSet<>();
        if (peek('}')) {
            copy('}');
        } else {
            while (true) {
                String rawKey = readStringToken();
                output.append(rawKey);
                String key = new JSONArray("[" + rawKey + "]").getString(0);
                sourceKeys.add(key);
                copyWhitespace();
                copy(':');
                if (!desired.has(key)) {
                    throw new IllegalArgumentException("JSON structure removed key: " + key);
                }
                patchValue(desired.get(key));
                copyWhitespace();
                if (peek('}')) {
                    copy('}');
                    break;
                }
                copy(',');
                copyWhitespace();
            }
        }

        Iterator<?> keys = desired.keys();
        while (keys.hasNext()) {
            String key = String.valueOf(keys.next());
            if (!sourceKeys.contains(key) && !isImplicitDefault(key, desired.get(key))) {
                throw new IllegalArgumentException("JSON structure added key: " + key);
            }
        }
    }

    private void patchArray(JSONArray desired) throws JSONException {
        copy('[');
        copyWhitespace();
        int index = 0;
        if (peek(']')) {
            copy(']');
        } else {
            while (true) {
                if (index >= desired.length()) {
                    throw new IllegalArgumentException("JSON array item was removed");
                }
                patchValue(desired.get(index++));
                copyWhitespace();
                if (peek(']')) {
                    copy(']');
                    break;
                }
                copy(',');
                copyWhitespace();
            }
        }
        if (index != desired.length()) {
            throw new IllegalArgumentException("JSON array item was added");
        }
    }

    private void patchScalar(Object desired) throws JSONException {
        String raw = peek('"') ? readStringToken() : readLiteralToken();
        Object current = new JSONArray("[" + raw + "]").get(0);
        output.append(sameScalar(current, desired) ? raw : scalarJson(desired));
    }

    private String readStringToken() {
        int start = position;
        copyPosition('"');
        boolean escaped = false;
        while (position < source.length()) {
            char character = source.charAt(position++);
            if (escaped) {
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '"') {
                return source.substring(start, position);
            }
        }
        throw new IllegalArgumentException("Unterminated JSON string");
    }

    private String readLiteralToken() {
        int start = position;
        while (position < source.length()) {
            char character = source.charAt(position);
            if (Character.isWhitespace(character) || character == ','
                    || character == ']' || character == '}') {
                break;
            }
            position++;
        }
        if (start == position) throw new IllegalArgumentException("Expected JSON scalar");
        return source.substring(start, position);
    }

    private void copyWhitespace() {
        while (position < source.length()
                && Character.isWhitespace(source.charAt(position))) {
            output.append(source.charAt(position++));
        }
    }

    private void copy(char expected) {
        copyPosition(expected);
        output.append(expected);
    }

    private void copyPosition(char expected) {
        if (!peek(expected)) {
            throw new IllegalArgumentException("Expected '" + expected
                    + "' at character " + position);
        }
        position++;
    }

    private boolean peek(char expected) {
        return position < source.length() && source.charAt(position) == expected;
    }

    private static boolean sameScalar(Object current, Object desired) {
        if (current == JSONObject.NULL || desired == JSONObject.NULL) {
            return current == desired;
        }
        if (current instanceof Number currentNumber
                && desired instanceof Number desiredNumber) {
            return new BigDecimal(currentNumber.toString())
                    .compareTo(new BigDecimal(desiredNumber.toString())) == 0;
        }
        return current.equals(desired);
    }

    private static String scalarJson(Object value) {
        String array = new JSONArray().put(value).toString();
        return array.substring(1, array.length() - 1);
    }

    private static boolean isImplicitDefault(String key, Object value) {
        return "visible".equals(key) && Boolean.TRUE.equals(value);
    }
}
