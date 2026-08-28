package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.tools.mcp.McpTool;
import com.dillon.starsectormarines.tools.mcp.McpToolCatalog;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server is assembled from {@code META-INF/services} at launch, so a
 * provider that is written but not registered is a tool nobody can call and
 * nothing else notices.
 *
 * <p>This also proves the module boundary from the outside: the generic host in
 * {@code :layer-authoring} contributes the snapshot tools and knows nothing
 * about tilesets, while the tileset tools live in the root test source set and
 * reach the same catalog through the same SPI.
 */
class AuthoringMcpDiscoveryTest {

    @Test
    void bothProvidersAreRegisteredAndReachTheSameCatalog() throws Exception {
        List<String> names = McpToolCatalog.discover().names();

        assertTrue(names.contains("tileset_list"),
                "the tileset provider is registered from the root test source set: " + names);
        assertTrue(names.contains("snapshot_create"),
                "the snapshot provider is registered from :layer-authoring: " + names);
    }

    @Test
    void everyDiscoveredToolCanDescribeItselfToACaller() throws Exception {
        // A model chooses a tool from its name, description and schema alone.
        // Any of the three missing makes the tool effectively uncallable, and
        // the catalog's own construction is what refuses that.
        for (McpTool tool : McpToolCatalog.discover().tools()) {
            assertFalse(tool.description().isBlank(), tool.name() + " has no description");
            JSONObject schema = tool.inputSchema();
            assertEquals("object", schema.getString("type"), tool.name() + " schema");
            assertTrue(schema.has("properties"), tool.name() + " schema has no properties");
            assertFalse(schema.getBoolean("additionalProperties"),
                    tool.name() + " accepts unknown arguments, so a guessed one is "
                            + "silently ignored rather than reported");
        }
    }
}
