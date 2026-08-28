package com.dillon.starsectormarines.tools.mcp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Deterministically ordered registry of the tools discovered on the tool
 * classpath, mirroring {@code SnapshotCatalog}.
 *
 * <p>Ordered by name rather than by discovery, because classpath order is not
 * stable across machines and a tool list that reshuffles between sessions makes
 * every diff of a transcript unreadable.
 */
public final class McpToolCatalog {

    private final List<McpTool> tools;
    private final Map<String, McpTool> byName;

    public McpToolCatalog(Collection<? extends McpTool> discovered) {
        if (discovered == null) throw new IllegalArgumentException("tools are required");
        List<McpTool> ordered = new ArrayList<>(discovered);
        for (McpTool tool : ordered) {
            if (tool == null) throw new IllegalArgumentException("tool may not be null");
            requireName(tool.name());
            if (tool.description() == null || tool.description().isBlank()) {
                throw new IllegalArgumentException("tool '" + tool.name()
                        + "' requires a description; it is the only documentation a caller gets");
            }
            if (tool.inputSchema() == null) {
                throw new IllegalArgumentException("tool '" + tool.name()
                        + "' requires an input schema");
            }
        }
        ordered.sort((left, right) -> left.name().compareTo(right.name()));
        Map<String, McpTool> indexed = new LinkedHashMap<>();
        for (McpTool tool : ordered) {
            if (indexed.put(tool.name(), tool) != null) {
                throw new IllegalArgumentException("duplicate tool name '" + tool.name() + "'");
            }
        }
        this.tools = List.copyOf(ordered);
        this.byName = Collections.unmodifiableMap(indexed);
    }

    public static McpToolCatalog discover() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return discover(loader != null ? loader : McpToolCatalog.class.getClassLoader());
    }

    public static McpToolCatalog discover(ClassLoader loader) {
        if (loader == null) throw new IllegalArgumentException("class loader is required");
        List<McpTool> discovered = new ArrayList<>();
        for (McpToolProvider provider : ServiceLoader.load(McpToolProvider.class, loader)) {
            List<McpTool> contributed = provider.tools();
            if (contributed == null) {
                throw new IllegalStateException(provider.getClass().getName()
                        + " contributed no tool list");
            }
            discovered.addAll(contributed);
        }
        return new McpToolCatalog(discovered);
    }

    public List<McpTool> tools() {
        return tools;
    }

    /** Null when nothing answers to that name; the caller reports it, not this. */
    public McpTool find(String name) {
        return name == null ? null : byName.get(name);
    }

    public List<String> names() {
        return List.copyOf(byName.keySet());
    }

    private static void requireName(String name) {
        if (name == null || !name.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException(
                    "tool name must be lower_snake_case: " + name);
        }
    }
}
