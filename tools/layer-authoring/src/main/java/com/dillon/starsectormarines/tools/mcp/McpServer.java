package com.dillon.starsectormarines.tools.mcp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * The Model Context Protocol, as far as a tools-only server needs it.
 *
 * <p>Deliberately a function from one request object to one response object
 * rather than a session with a transport attached. Everything interesting about
 * this server is the shape of what it answers, and keeping the transport out of
 * it means that shape can be asserted directly instead of through a pipe.
 *
 * <p>The handled set is closed: {@code initialize}, {@code notifications/initialized},
 * {@code ping}, {@code tools/list}, {@code tools/call}. Resources, prompts,
 * sampling, and completion are not advertised, so a conforming client never
 * asks for them; anything else answers "method not found" rather than being
 * quietly ignored.
 *
 * <p><b>A failing tool is not a failing request.</b> A JSON-RPC error means the
 * server could not process the call at all, and a client is entitled to treat
 * one as a session fault. "That sheet is not in the project" is an answer, so it
 * comes back as a result carrying {@code isError} — which the model can read and
 * act on — and only genuine protocol faults become errors.
 */
public final class McpServer {

    /** Newest revision this speaks. Older revisions a client asks for are echoed back. */
    public static final String LATEST_PROTOCOL_VERSION = "2025-06-18";

    private static final List<String> SUPPORTED_PROTOCOL_VERSIONS =
            List.of("2025-06-18", "2025-03-26", "2024-11-05");

    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    private final McpToolCatalog catalog;
    private final McpToolContext context;
    private final String serverName;
    private final String serverVersion;

    public McpServer(McpToolCatalog catalog, McpToolContext context,
                     String serverName, String serverVersion) {
        if (catalog == null || context == null) {
            throw new IllegalArgumentException("a tool catalog and a project context are required");
        }
        this.catalog = catalog;
        this.context = context;
        this.serverName = serverName == null ? "authoring" : serverName;
        this.serverVersion = serverVersion == null ? "0.0.1" : serverVersion;
    }

    /**
     * Answer one line of the stdio stream.
     *
     * @return the response line, or null when the message needs no reply
     */
    public String handleLine(String line) {
        if (line == null || line.isBlank()) return null;
        JSONObject request;
        try {
            request = new JSONObject(line);
        } catch (JSONException malformed) {
            // A parse failure has no id to answer under, and the spec's null-id
            // error response is exactly this case. It must not end the loop:
            // one corrupt line should cost one message, not the session.
            return error(JSONObject.NULL, PARSE_ERROR,
                    "invalid JSON: " + malformed.getMessage()).toString();
        }
        JSONObject response = handle(request);
        return response == null ? null : response.toString();
    }

    /** Answer one request. Null for a notification, which by definition has no reply. */
    public JSONObject handle(JSONObject request) {
        Object id = request.opt("id");
        String method = request.optString("method", "");
        boolean notification = id == null;

        if (method.isEmpty()) {
            return notification ? null
                    : error(id, INVALID_REQUEST, "request has no method");
        }
        JSONObject params = request.optJSONObject("params");
        if (params == null) params = new JSONObject();

        try {
            switch (method) {
                case "initialize":
                    return notification ? null : result(id, initialize(params));
                case "notifications/initialized":
                case "notifications/cancelled":
                    // Acknowledged by saying nothing, which is what a notification wants.
                    return null;
                case "ping":
                    return notification ? null : result(id, new JSONObject());
                case "tools/list":
                    return notification ? null : result(id, listTools());
                case "tools/call":
                    return notification ? null : result(id, callTool(params));
                default:
                    return notification ? null
                            : error(id, METHOD_NOT_FOUND, "unknown method: " + method);
            }
        } catch (IllegalArgumentException invalid) {
            return notification ? null : error(id, INVALID_PARAMS, String.valueOf(invalid.getMessage()));
        } catch (Exception failure) {
            return notification ? null
                    : error(id, INTERNAL_ERROR, failure.getClass().getSimpleName()
                    + ": " + failure.getMessage());
        }
    }

    /**
     * Negotiate a version and say what this server can do.
     *
     * <p>A client's requested revision is echoed when this server speaks it, so
     * an older client is not forced to upgrade for a tools-only server whose
     * payloads did not change between those revisions. An unrecognised revision
     * gets this server's newest, which is the spec's own instruction: the client
     * then decides whether it can live with it.
     */
    private JSONObject initialize(JSONObject params) throws JSONException {
        String requested = params.optString("protocolVersion", "");
        String agreed = SUPPORTED_PROTOCOL_VERSIONS.contains(requested)
                ? requested : LATEST_PROTOCOL_VERSION;

        JSONObject capabilities = new JSONObject();
        // An empty object is how the protocol says "supported, with no options".
        capabilities.put("tools", new JSONObject());

        JSONObject info = new JSONObject();
        info.put("name", serverName);
        info.put("version", serverVersion);

        JSONObject response = new JSONObject();
        response.put("protocolVersion", agreed);
        response.put("capabilities", capabilities);
        response.put("serverInfo", info);
        response.put("instructions",
                "Authoring tools for the Starsector Marines checkout at "
                        + context.projectRoot()
                        + ". Tileset tools follow the ingest procedure in "
                        + ".claude/skills/ingest-tileset/SKILL.md: measure a sheet, write its "
                        + "seed, slice it, annotate the pieces, then export. The judged half — "
                        + "what a piece is and what the sheet is for — is yours; these tools "
                        + "only do the measured half.");
        return response;
    }

    private JSONObject listTools() throws JSONException {
        JSONArray tools = new JSONArray();
        for (McpTool tool : catalog.tools()) {
            JSONObject described = new JSONObject();
            described.put("name", tool.name());
            described.put("description", tool.description());
            described.put("inputSchema", tool.inputSchema());
            tools.put(described);
        }
        return new JSONObject().put("tools", tools);
    }

    private JSONObject callTool(JSONObject params) throws JSONException {
        String name = params.optString("name", "");
        if (name.isEmpty()) throw new IllegalArgumentException("tools/call requires a tool name");
        McpTool tool = catalog.find(name);
        if (tool == null) {
            // Not METHOD_NOT_FOUND: tools/call was found, and the model asking
            // for a tool that is not there is something it can recover from by
            // reading the list again.
            return McpToolResult.failure("No tool named '" + name + "'. Available: "
                    + String.join(", ", catalog.names())).toJson();
        }
        JSONObject arguments = params.optJSONObject("arguments");
        if (arguments == null) arguments = new JSONObject();
        try {
            McpToolResult result = tool.call(arguments, context);
            if (result == null) {
                return McpToolResult.failure(name + " returned nothing").toJson();
            }
            return result.toJson();
        } catch (Exception failure) {
            String message = failure.getMessage();
            return McpToolResult.failure(name + " failed: " + failure.getClass().getSimpleName()
                    + (message == null ? "" : ": " + message)).toJson();
        }
    }

    // The two envelope builders wrap rather than declare: they are the only way
    // to report anything at all, so a checked throw here would leave a failing
    // request with no shape to fail in.

    private static JSONObject result(Object id, JSONObject payload) {
        try {
            JSONObject response = new JSONObject();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.put("result", payload);
            return response;
        } catch (JSONException impossible) {
            throw new IllegalStateException("could not build a JSON-RPC result", impossible);
        }
    }

    private static JSONObject error(Object id, int code, String message) {
        try {
            JSONObject error = new JSONObject();
            error.put("code", code);
            error.put("message", message);
            JSONObject response = new JSONObject();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.put("error", error);
            return response;
        } catch (JSONException impossible) {
            throw new IllegalStateException("could not build a JSON-RPC error", impossible);
        }
    }
}
