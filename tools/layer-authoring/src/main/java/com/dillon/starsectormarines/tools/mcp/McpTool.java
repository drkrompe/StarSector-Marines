package com.dillon.starsectormarines.tools.mcp;

import org.json.JSONObject;

/** One callable operation the authoring server exposes. */
public interface McpTool {

    /** Wire name, {@code lower_snake_case}. This is what a caller says. */
    String name();

    /**
     * What calling this does, written for a model that has never seen this
     * repository. It is the only documentation the caller gets, so it says what
     * the tool is for and what it writes, not merely what it is called.
     */
    String description();

    /** JSON Schema for the {@code arguments} object. */
    JSONObject inputSchema();

    McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception;
}
