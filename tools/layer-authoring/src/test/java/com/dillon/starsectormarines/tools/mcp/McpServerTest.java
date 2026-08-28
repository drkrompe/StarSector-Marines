package com.dillon.starsectormarines.tools.mcp;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The protocol contract, asserted on the wire shape rather than on the
 * implementation, because the wire shape is the thing a client depends on.
 */
class McpServerTest {

    /** A tool whose only job is to be called, so the dispatch can be watched. */
    private static final class Echo implements McpTool {

        private final String name;
        private final RuntimeException throwOnCall;

        Echo(String name, RuntimeException throwOnCall) {
            this.name = name;
            this.throwOnCall = throwOnCall;
        }

        @Override public String name() { return name; }

        @Override public String description() { return "Echoes its argument back."; }

        @Override
        public JSONObject inputSchema() {
            return McpSchema.object().string("text", "anything").build();
        }

        @Override
        public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
            if (throwOnCall != null) throw throwOnCall;
            return McpToolResult.of("echo: " + arguments.optString("text", ""),
                    new JSONObject().put("text", arguments.optString("text", "")));
        }
    }

    private static McpServer server(Path root, McpTool... tools) throws Exception {
        return new McpServer(new McpToolCatalog(List.of(tools)),
                new McpToolContext(root, root.resolve("core")), "test-server", "1.2.3");
    }

    private static JSONObject request(String method, JSONObject params, Object id) throws Exception {
        JSONObject request = new JSONObject();
        request.put("jsonrpc", "2.0");
        request.put("method", method);
        if (id != null) request.put("id", id);
        if (params != null) request.put("params", params);
        return request;
    }

    @Test
    void initializeAgreesOnTheVersionTheClientAsksForWhenItIsSpoken(@TempDir Path root) throws Exception {
        // Echoing a supported older revision is what keeps an older client
        // working against a tools-only server whose payloads did not change.
        JSONObject response = server(root).handle(request("initialize",
                new JSONObject().put("protocolVersion", "2024-11-05"), 1));

        assertEquals("2024-11-05",
                response.getJSONObject("result").getString("protocolVersion"));
    }

    @Test
    void initializeFallsBackToItsOwnVersionForAnUnknownOne(@TempDir Path root) throws Exception {
        JSONObject response = server(root).handle(request("initialize",
                new JSONObject().put("protocolVersion", "1999-01-01"), 1));

        assertEquals(McpServer.LATEST_PROTOCOL_VERSION,
                response.getJSONObject("result").getString("protocolVersion"),
                "an unrecognised revision gets ours; the client then decides");
    }

    @Test
    void initializeAdvertisesToolsAndNothingElse(@TempDir Path root) throws Exception {
        JSONObject capabilities = server(root).handle(request("initialize", new JSONObject(), 1))
                .getJSONObject("result").getJSONObject("capabilities");

        assertTrue(capabilities.has("tools"), "a tools server must say it has tools");
        assertFalse(capabilities.has("resources"),
                "advertising a capability invites requests this server cannot answer");
        assertFalse(capabilities.has("prompts"));
    }

    @Test
    void aNotificationIsAnsweredWithSilence(@TempDir Path root) throws Exception {
        // A reply to a notification is a protocol violation, and a client that
        // is strict about it will fault the session over one stray line.
        assertNull(server(root).handle(request("notifications/initialized", null, null)));
        assertNull(server(root).handleLine(
                request("notifications/initialized", null, null).toString()));
    }

    @Test
    void toolsListReturnsEverySchemaTheCatalogHolds(@TempDir Path root) throws Exception {
        JSONObject result = server(root, new Echo("beta", null), new Echo("alpha", null))
                .handle(request("tools/list", null, 7)).getJSONObject("result");

        assertEquals(2, result.getJSONArray("tools").length());
        assertEquals("alpha", result.getJSONArray("tools").getJSONObject(0).getString("name"),
                "tools are name-ordered so a listing does not reshuffle between machines");
        assertNotNull(result.getJSONArray("tools").getJSONObject(0).getJSONObject("inputSchema"),
                "a tool without a schema cannot be called correctly by a model");
    }

    @Test
    void callingAToolReturnsItsTextAndItsStructuredPayload(@TempDir Path root) throws Exception {
        JSONObject params = new JSONObject()
                .put("name", "echo")
                .put("arguments", new JSONObject().put("text", "hello"));

        JSONObject result = server(root, new Echo("echo", null))
                .handle(request("tools/call", params, 9)).getJSONObject("result");

        assertFalse(result.getBoolean("isError"));
        assertEquals("echo: hello",
                result.getJSONArray("content").getJSONObject(0).getString("text"));
        assertEquals("hello", result.getJSONObject("structuredContent").getString("text"),
                "the model reads the text and acts on the structured half");
    }

    @Test
    void aToolThatThrowsReportsAnErrorResultRatherThanFaultingTheSession(@TempDir Path root) throws Exception {
        JSONObject params = new JSONObject().put("name", "boom");

        JSONObject response = server(root, new Echo("boom", new IllegalStateException("nope")))
                .handle(request("tools/call", params, 3));

        assertFalse(response.has("error"),
                "a JSON-RPC error means the server could not process the call at all; "
                        + "a failing tool has answered");
        assertTrue(response.getJSONObject("result").getBoolean("isError"));
        assertTrue(response.getJSONObject("result").getJSONArray("content")
                        .getJSONObject(0).getString("text").contains("nope"),
                "the model needs the failure's text to recover from it");
    }

    @Test
    void callingAToolThatIsNotThereListsTheOnesThatAre(@TempDir Path root) throws Exception {
        JSONObject params = new JSONObject().put("name", "tileset_lst");

        JSONObject result = server(root, new Echo("tileset_list", null))
                .handle(request("tools/call", params, 4)).getJSONObject("result");

        assertTrue(result.getBoolean("isError"));
        assertTrue(result.getJSONArray("content").getJSONObject(0).getString("text")
                        .contains("tileset_list"),
                "a typo is recoverable only if the answer says what the right name was");
    }

    @Test
    void anUnknownMethodIsMethodNotFound(@TempDir Path root) throws Exception {
        JSONObject response = server(root).handle(request("resources/list", null, 5));

        assertEquals(McpServer.METHOD_NOT_FOUND,
                response.getJSONObject("error").getInt("code"));
    }

    @Test
    void aMalformedLineCostsOneMessageRatherThanTheSession(@TempDir Path root) throws Exception {
        McpServer server = server(root, new Echo("echo", null));

        JSONObject parseError = new JSONObject(server.handleLine("{ not json"));
        assertEquals(McpServer.PARSE_ERROR, parseError.getJSONObject("error").getInt("code"));
        assertTrue(parseError.isNull("id"), "a line that would not parse has no id to answer under");

        // The point of the test: the server still works afterwards.
        assertNotNull(server.handleLine(request("ping", null, 2).toString()));
    }

    @Test
    void pingAnswersWithAnEmptyResult(@TempDir Path root) throws Exception {
        JSONObject response = server(root).handle(request("ping", null, 11));

        assertEquals(0, response.getJSONObject("result").length());
        assertEquals(11, response.getInt("id"), "the reply must carry the id it answers");
    }

    @Test
    void aBlankLineIsNotAMessage(@TempDir Path root) throws Exception {
        // Clients and pipes both emit them; answering one puts a spurious error
        // on a wire that is supposed to carry only replies to real requests.
        assertNull(server(root).handleLine(""));
        assertNull(server(root).handleLine("   "));
    }
}
