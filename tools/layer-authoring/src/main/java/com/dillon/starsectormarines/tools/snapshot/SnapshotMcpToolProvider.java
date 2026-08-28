package com.dillon.starsectormarines.tools.snapshot;

import com.dillon.starsectormarines.tools.mcp.McpSchema;
import com.dillon.starsectormarines.tools.mcp.McpTool;
import com.dillon.starsectormarines.tools.mcp.McpToolContext;
import com.dillon.starsectormarines.tools.mcp.McpToolProvider;
import com.dillon.starsectormarines.tools.mcp.McpToolResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.List;

/**
 * The visual-evidence catalog, exposed to a session.
 *
 * <p>Lives beside the catalog rather than with the mod-domain tools because the
 * catalog is what it drives, and the catalog is generic: the suites themselves
 * are discovered from whatever is on the classpath, so this contributes no
 * knowledge of what a marine or a tileset is.
 */
public final class SnapshotMcpToolProvider implements McpToolProvider {

    @Override
    public List<McpTool> tools() {
        return List.of(new ListSuites(), new CreateSnapshots());
    }

    /** Reported per suite so a caller can pick one without guessing an id. */
    private static JSONObject describe(SnapshotSuite suite) throws JSONException {
        JSONObject described = new JSONObject();
        described.put("id", suite.id());
        described.put("label", suite.label());
        return described;
    }

    private static final class ListSuites implements McpTool {

        @Override public String name() { return "snapshot_list_suites"; }

        @Override
        public String description() {
            return "List the deterministic visual-snapshot suites available in this checkout. "
                    + "Each renders evidence PNGs (and sometimes GIFs) without launching "
                    + "Starsector or creating an OpenGL context. Use the returned ids with "
                    + "snapshot_create.";
        }

        @Override
        public JSONObject inputSchema() {
            return McpSchema.object().build();
        }

        @Override
        public McpToolResult call(JSONObject arguments, McpToolContext context) throws JSONException {
            SnapshotCatalog catalog = SnapshotCatalog.discover();
            JSONArray suites = new JSONArray();
            StringBuilder text = new StringBuilder();
            for (SnapshotSuite suite : catalog.suites()) {
                suites.put(describe(suite));
                text.append(suite.id()).append(" — ").append(suite.label()).append('\n');
            }
            if (catalog.suites().isEmpty()) {
                return McpToolResult.failure(
                        "No snapshot suites are on the classpath. The server was probably "
                                + "launched without the root test runtime classpath, which is "
                                + "where the mod's suite providers are registered.");
            }
            return McpToolResult.of(text.toString().trim(),
                    new JSONObject().put("suites", suites));
        }
    }

    private static final class CreateSnapshots implements McpTool {

        @Override public String name() { return "snapshot_create"; }

        @Override
        public String description() {
            return "Render visual-snapshot suites to PNG/GIF files and return their paths. "
                    + "Equivalent to `gradlew.bat createSnapshots`. Existing files with the "
                    + "same names are replaced; stale files from earlier runs are not removed. "
                    + "Rendering every suite takes a while, so name one when you know which "
                    + "you want.";
        }

        @Override
        public JSONObject inputSchema() {
            return McpSchema.object()
                    .string("suites", "Comma-separated suite ids, or 'all' (the default). "
                            + "Get ids from snapshot_list_suites.")
                    .string("outputDir", "Where to write, project-relative. "
                            + "Defaults to build/snapshots. Must stay inside the project.")
                    .build();
        }

        @Override
        public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
            String selector = arguments.optString("suites", "all");
            String requestedDir = arguments.optString("outputDir", "");
            Path outputRoot = requestedDir.isBlank()
                    ? context.projectRoot().resolve("build/snapshots")
                    : context.resolveInsideProject(requestedDir);

            SnapshotCatalog catalog = SnapshotCatalog.discover();
            List<SnapshotSuite> selected = catalog.select(selector);
            List<Path> outputs = new SnapshotRunner().create(
                    new SnapshotContext(context.projectRoot(), context.starsectorCore()),
                    selected, outputRoot, true);

            JSONArray files = new JSONArray();
            for (Path output : outputs) files.put(output.toString());
            JSONObject structured = new JSONObject();
            structured.put("outputRoot", outputRoot.toString());
            structured.put("files", files);
            return McpToolResult.of(
                    "Wrote " + outputs.size() + " artifact(s) from " + selected.size()
                            + " suite(s) under " + outputRoot,
                    structured);
        }
    }
}
