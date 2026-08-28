package com.dillon.starsectormarines.tools.mcp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * What one tool call produced.
 *
 * <p>Two payloads rather than one, because the caller is a language model and
 * the two things it needs are not the same thing. {@link #text} is the summary
 * it reads; {@link #structured} is the data it acts on. Collapsing them means
 * either a prose blob that has to be re-parsed or a JSON dump that has to be
 * re-read to find out whether anything went wrong.
 *
 * <p>A failed call is a <em>result</em>, not a JSON-RPC error. The protocol
 * reserves errors for the protocol; a tool that could not find a sheet has
 * answered the question, and the model needs to see the answer rather than have
 * the session fault.
 */
public record McpToolResult(String text, JSONObject structured, boolean error) {

    public McpToolResult {
        if (text == null) throw new IllegalArgumentException("a tool result needs text");
    }

    public static McpToolResult of(String text) {
        return new McpToolResult(text, null, false);
    }

    public static McpToolResult of(String text, JSONObject structured) {
        return new McpToolResult(text, structured, false);
    }

    public static McpToolResult failure(String text) {
        return new McpToolResult(text, null, true);
    }

    /**
     * The {@code tools/call} result payload.
     *
     * <p>Does not declare {@code JSONException}, because this is also called
     * from the path that reports a tool's own failure: a checked throw there
     * would turn a reportable failure into an unreportable one.
     */
    public JSONObject toJson() {
        try {
            JSONObject content = new JSONObject();
            content.put("type", "text");
            content.put("text", text);
            JSONObject result = new JSONObject();
            result.put("content", new JSONArray().put(content));
            result.put("isError", error);
            if (structured != null) result.put("structuredContent", structured);
            return result;
        } catch (JSONException impossible) {
            throw new IllegalStateException("could not serialize a tool result", impossible);
        }
    }
}
