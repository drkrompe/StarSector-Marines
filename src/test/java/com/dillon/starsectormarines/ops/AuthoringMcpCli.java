package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.tools.mcp.McpServer;
import com.dillon.starsectormarines.tools.mcp.McpStdioServer;
import com.dillon.starsectormarines.tools.mcp.McpToolCatalog;
import com.dillon.starsectormarines.tools.mcp.McpToolContext;

import java.nio.file.Path;

/**
 * Serves the authoring tools over stdio, for a client that spawns this process.
 *
 * <p>Beside {@code CreateSnapshotsCli} and taking the same two roots, because it
 * is the same kind of thing: a headless entry point onto the checkout that runs
 * on the tool classpath and never ships.
 *
 * <p><b>stdout is the protocol.</b> Nothing here may print to it, which is why
 * the banner goes to stderr — the client surfaces stderr as server logs, so a
 * line there is visible without being on the wire.
 */
public final class AuthoringMcpCli {

    private AuthoringMcpCli() {}

    public static void main(String[] args) throws Exception {
        Path projectRoot = Path.of(args.length > 0 ? args[0] : ".");
        Path starsectorCore = args.length > 1
                ? Path.of(args[1])
                : Path.of(System.getProperty("starsectorDir", "."), "starsector-core");

        McpToolContext context = new McpToolContext(projectRoot, starsectorCore);
        McpToolCatalog catalog = McpToolCatalog.discover();
        System.err.println("starsector-authoring MCP server: " + catalog.names().size()
                + " tools over " + context.projectRoot());

        McpServer server = new McpServer(catalog, context, "starsector-authoring", "0.0.1");
        new McpStdioServer(server).serve(System.in, System.out);
    }
}
