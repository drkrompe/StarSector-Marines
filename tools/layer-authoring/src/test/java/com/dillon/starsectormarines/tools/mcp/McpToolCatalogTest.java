package com.dillon.starsectormarines.tools.mcp;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The catalog rejects at registration what a client could only discover at runtime. */
class McpToolCatalogTest {

    private record Stub(String name, String description, JSONObject schema) implements McpTool {

        @Override public String name() { return name; }

        @Override public String description() { return description; }

        @Override public JSONObject inputSchema() { return schema; }

        @Override public McpToolResult call(JSONObject arguments, McpToolContext context) {
            return McpToolResult.of("ok");
        }
    }

    private static Stub tool(String name) {
        return new Stub(name, "does a thing", McpSchema.object().build());
    }

    @Test
    void toolsAreOrderedByNameRatherThanByDiscovery() {
        // ServiceLoader order follows the classpath, which differs between
        // machines. A tool list that reshuffles makes transcripts undiffable.
        McpToolCatalog catalog = new McpToolCatalog(
                List.of(tool("zulu"), tool("alpha"), tool("mike")));

        assertEquals(List.of("alpha", "mike", "zulu"), catalog.names());
    }

    @Test
    void aDuplicateNameIsRefusedRatherThanBecomingLastOneWins() {
        assertThrows(IllegalArgumentException.class,
                () -> new McpToolCatalog(List.of(tool("same"), tool("same"))),
                "two tools answering to one name means a caller cannot say which it wants");
    }

    @Test
    void aToolWithNoDescriptionIsRefused() {
        // The description is the only documentation a model gets; a nameless
        // capability is one it will never correctly choose.
        assertThrows(IllegalArgumentException.class,
                () -> new McpToolCatalog(List.of(
                        new Stub("quiet", "  ", McpSchema.object().build()))));
    }

    @Test
    void aToolWithNoSchemaIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new McpToolCatalog(List.of(new Stub("bare", "does a thing", null))));
    }

    @Test
    void namesMustBeLowerSnakeCase() {
        // The convention is what makes an unfamiliar tool name guessable, and
        // a mixed-case name is a thing callers reliably get wrong.
        assertThrows(IllegalArgumentException.class,
                () -> new McpToolCatalog(List.of(tool("TilesetList"))));
        assertThrows(IllegalArgumentException.class,
                () -> new McpToolCatalog(List.of(tool("tileset-list"))));
    }

    @Test
    void anUnknownNameFindsNothingRatherThanFailing() {
        assertNull(new McpToolCatalog(List.of(tool("alpha"))).find("beta"),
                "reporting the miss is the caller's job, so it can list the alternatives");
    }
}
