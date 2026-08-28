package com.dillon.starsectormarines.tools.mcp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the object schema a tool advertises for its arguments.
 *
 * <p>Small on purpose. A general JSON Schema builder would be a second library;
 * what a tool here needs is an object of flat, described properties and a list
 * of which are required, and spelling that out by hand in every tool is how a
 * property ends up documented in one place and not another.
 */
public final class McpSchema {

    private final JSONObject properties = new JSONObject();
    private final List<String> required = new ArrayList<>();

    public static McpSchema object() {
        return new McpSchema();
    }

    public McpSchema string(String name, String description) {
        return property(name, "string", description, false);
    }

    public McpSchema requiredString(String name, String description) {
        return property(name, "string", description, true);
    }

    public McpSchema integer(String name, String description) {
        return property(name, "integer", description, false);
    }

    public McpSchema bool(String name, String description) {
        return property(name, "boolean", description, false);
    }

    public McpSchema object(String name, String description, boolean isRequired) {
        return property(name, "object", description, isRequired);
    }

    private McpSchema property(String name, String type, String description, boolean isRequired) {
        try {
            JSONObject property = new JSONObject();
            property.put("type", type);
            property.put("description", description);
            properties.put(name, property);
        } catch (JSONException impossible) {
            // org.json throws only for a null key or a non-finite number, and a
            // schema is built from constant strings. Declaring it would make
            // McpTool.inputSchema() throw, and every caller of it after that.
            throw new IllegalStateException("could not build the schema for " + name, impossible);
        }
        if (isRequired) required.add(name);
        return this;
    }

    public JSONObject build() {
        try {
            JSONObject schema = new JSONObject();
            schema.put("type", "object");
            schema.put("properties", properties);
            schema.put("required", new JSONArray(required));
            // Callers guess argument names when a schema does not forbid it, and a
            // silently-ignored guess is worse than a rejected one.
            schema.put("additionalProperties", false);
            return schema;
        } catch (JSONException impossible) {
            throw new IllegalStateException("could not build the schema", impossible);
        }
    }
}
