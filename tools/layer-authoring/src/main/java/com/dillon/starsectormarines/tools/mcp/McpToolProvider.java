package com.dillon.starsectormarines.tools.mcp;

import java.util.List;

/**
 * Discoverable contribution of tools, registered through {@code META-INF/services}.
 *
 * <p>A provider rather than the tool itself, following {@code AuthoringPageProvider}
 * rather than {@code SnapshotSuite}: a domain contributes a family of related
 * tools that share their loading and their helpers, and one service line per
 * tool would make adding an eighth tileset operation a two-file edit for no
 * gain.
 *
 * <p>This is also what keeps the generic host free of mod domain code. The host
 * module knows the SPI; the tools that know what a tileset is are registered
 * from the root test source set, which never reaches the shipped jar.
 */
public interface McpToolProvider {

    List<McpTool> tools();
}
