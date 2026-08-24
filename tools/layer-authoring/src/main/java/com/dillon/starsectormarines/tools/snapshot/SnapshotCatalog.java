package com.dillon.starsectormarines.tools.snapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/** Deterministically ordered registry of snapshot suites discovered on the tool classpath. */
public final class SnapshotCatalog {

    private final List<SnapshotSuite> suites;
    private final Map<String, SnapshotSuite> byId;

    public SnapshotCatalog(Collection<? extends SnapshotSuite> suites) {
        if (suites == null) throw new IllegalArgumentException("snapshot suites are required");
        List<SnapshotSuite> ordered = new ArrayList<>(suites);
        if (ordered.stream().anyMatch(suite -> suite == null)) {
            throw new IllegalArgumentException("snapshot suite may not be null");
        }
        for (SnapshotSuite suite : ordered) {
            requireId(suite.id());
            if (suite.label() == null || suite.label().isBlank()) {
                throw new IllegalArgumentException("snapshot suite '" + suite.id()
                        + "' requires a label");
            }
        }
        ordered.sort((left, right) -> left.id().compareTo(right.id()));
        Map<String, SnapshotSuite> indexed = new LinkedHashMap<>();
        for (SnapshotSuite suite : ordered) {
            SnapshotSuite previous = indexed.put(suite.id(), suite);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate snapshot suite id '"
                        + suite.id() + "'");
            }
        }
        this.suites = List.copyOf(ordered);
        byId = Collections.unmodifiableMap(indexed);
    }

    public static SnapshotCatalog discover() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return discover(loader != null ? loader : SnapshotCatalog.class.getClassLoader());
    }

    public static SnapshotCatalog discover(ClassLoader loader) {
        if (loader == null) throw new IllegalArgumentException("class loader is required");
        List<SnapshotSuite> discovered = new ArrayList<>();
        ServiceLoader.load(SnapshotSuite.class, loader).forEach(discovered::add);
        return new SnapshotCatalog(discovered);
    }

    public List<SnapshotSuite> suites() {
        return suites;
    }

    /** Selects all suites or a comma-separated set while preserving catalog order. */
    public List<SnapshotSuite> select(String selector) {
        if (selector == null || selector.isBlank()
                || selector.trim().equalsIgnoreCase("all")) {
            return suites;
        }
        Set<String> requested = new LinkedHashSet<>();
        for (String token : selector.split(",")) {
            String id = token.trim().toLowerCase(Locale.ROOT);
            if (!id.isEmpty()) requested.add(id);
        }
        Set<String> unknown = new LinkedHashSet<>(requested);
        unknown.removeAll(byId.keySet());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unknown snapshot suites " + unknown
                    + "; available suites: " + byId.keySet());
        }
        return suites.stream().filter(suite -> requested.contains(suite.id())).toList();
    }

    private static void requireId(String id) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9-]*")) {
            throw new IllegalArgumentException("snapshot suite id must be lowercase kebab-case: "
                    + id);
        }
    }
}
