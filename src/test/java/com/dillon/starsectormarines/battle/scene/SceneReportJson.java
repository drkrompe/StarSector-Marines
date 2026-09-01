package com.dillon.starsectormarines.battle.scene;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and writes one {@link SceneReport} as JSON.
 *
 * <p>Written by hand rather than through {@link JSONObject#toString()} because
 * that class is backed by a hash map: the same report would serialise with its
 * keys in a different order on a different run, and a verdict file whose diff
 * is noise is a verdict file nobody reads. The order here is the order a reader
 * wants — what it is, then whether it passed, then why.
 */
public final class SceneReportJson {

    private SceneReportJson() {}

    public static void write(SceneReport report, Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(file, toJson(report), StandardCharsets.UTF_8);
    }

    public static SceneReport read(Path file) throws IOException {
        return fromJson(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** Stable-order JSON text for one report, newline-terminated. */
    public static String toJson(SceneReport report) {
        StringBuilder out = new StringBuilder(256);
        out.append("{\n");
        out.append("  \"sceneId\": ").append(JSONObject.quote(report.sceneId())).append(",\n");
        out.append("  \"loopId\": ").append(JSONObject.quote(report.loopId())).append(",\n");
        out.append("  \"ticks\": ").append(report.ticks()).append(",\n");
        out.append("  \"passed\": ").append(report.passed()).append(",\n");
        out.append("  \"verdicts\": [");
        List<Verdict> verdicts = report.verdicts();
        for (int i = 0; i < verdicts.size(); i++) {
            Verdict verdict = verdicts.get(i);
            out.append(i == 0 ? "\n" : ",\n");
            out.append("    {\"name\": ").append(JSONObject.quote(verdict.name()))
                    .append(", \"pass\": ").append(verdict.pass())
                    .append(", \"detail\": ").append(JSONObject.quote(verdict.detail()))
                    .append('}');
        }
        out.append(verdicts.isEmpty() ? "]," : "\n  ],").append('\n');
        out.append("  \"metrics\": {");
        boolean first = true;
        for (Map.Entry<String, Number> metric : report.metrics().entrySet()) {
            out.append(first ? "\n" : ",\n");
            first = false;
            out.append("    ").append(JSONObject.quote(metric.getKey())).append(": ")
                    .append(literal(metric.getValue()));
        }
        out.append(first ? "}" : "\n  }").append('\n');
        out.append("}\n");
        return out.toString();
    }

    public static SceneReport fromJson(String text) {
        try {
            JSONObject root = new JSONObject(text);
            List<Verdict> verdicts = new ArrayList<>();
            JSONArray array = root.getJSONArray("verdicts");
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.getJSONObject(i);
                verdicts.add(new Verdict(entry.getString("name"), entry.getBoolean("pass"),
                        entry.optString("detail", "")));
            }
            JSONObject metricsJson = root.getJSONObject("metrics");
            Map<String, Number> metrics = new LinkedHashMap<>();
            for (String key : metricKeysInFileOrder(text, metricsJson)) {
                metrics.put(key, (Number) metricsJson.get(key));
            }
            return new SceneReport(root.getString("sceneId"), root.getString("loopId"),
                    root.getInt("ticks"), verdicts, metrics);
        } catch (JSONException malformed) {
            throw new IllegalArgumentException("not a scene report: " + malformed.getMessage(),
                    malformed);
        }
    }

    /**
     * Metric names in the order the file lists them. The parsed object cannot
     * say — it is hash-ordered — so a read followed by a write would scramble
     * the readings a scene deliberately emitted in sequence. Scanning the text
     * is safe for this block alone: metric values are numbers, so every quoted
     * run inside it is a key.
     */
    private static List<String> metricKeysInFileOrder(String text, JSONObject metrics)
            throws JSONException {
        int start = text.lastIndexOf("\"metrics\"");
        String block = start < 0 ? "" : text.substring(start + "\"metrics\"".length());
        List<String> keys = new ArrayList<>(metrics.length());
        Matcher quoted = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(block);
        while (quoted.find()) {
            String key = new JSONObject("{\"k\":" + quoted.group() + "}").getString("k");
            if (metrics.has(key) && !keys.contains(key)) keys.add(key);
        }
        Iterator<?> remaining = metrics.keys();
        while (remaining.hasNext()) {
            String key = String.valueOf(remaining.next());
            if (!keys.contains(key)) keys.add(key);
        }
        return keys;
    }

    /** A JSON number literal; a metric that is not a finite number has nothing to record. */
    private static String literal(Number value) {
        double d = value.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException("metric is not a finite number: " + value);
        }
        return value.toString();
    }
}
