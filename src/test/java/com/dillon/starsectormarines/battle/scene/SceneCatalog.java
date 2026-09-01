package com.dillon.starsectormarines.battle.scene;

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

/**
 * Deterministically ordered registry of the behaviour scenes on the test
 * classpath.
 *
 * <p>Discovery is the same shape the snapshot catalog uses, deliberately: a
 * scene is registered once through {@code META-INF/services} and is thereafter
 * reachable from the evidence task and from a snapshot suite without a second
 * integration path.
 */
public final class SceneCatalog {

    private final List<BehaviorScene> scenes;
    private final Map<String, BehaviorScene> byId;

    public SceneCatalog(Collection<? extends BehaviorScene> scenes) {
        if (scenes == null) throw new IllegalArgumentException("behaviour scenes are required");
        List<BehaviorScene> ordered = new ArrayList<>(scenes);
        if (ordered.stream().anyMatch(scene -> scene == null)) {
            throw new IllegalArgumentException("behaviour scene may not be null");
        }
        for (BehaviorScene scene : ordered) {
            requireId(scene.id());
            if (scene.label() == null || scene.label().isBlank()) {
                throw new IllegalArgumentException("behaviour scene '" + scene.id()
                        + "' requires a label");
            }
        }
        ordered.sort((left, right) -> left.id().compareTo(right.id()));
        Map<String, BehaviorScene> indexed = new LinkedHashMap<>();
        for (BehaviorScene scene : ordered) {
            BehaviorScene previous = indexed.put(scene.id(), scene);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate behaviour scene id '"
                        + scene.id() + "'");
            }
        }
        this.scenes = List.copyOf(ordered);
        byId = Collections.unmodifiableMap(indexed);
    }

    public static SceneCatalog discover() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return discover(loader != null ? loader : SceneCatalog.class.getClassLoader());
    }

    public static SceneCatalog discover(ClassLoader loader) {
        if (loader == null) throw new IllegalArgumentException("class loader is required");
        List<BehaviorScene> discovered = new ArrayList<>();
        ServiceLoader.load(BehaviorScene.class, loader).forEach(discovered::add);
        return new SceneCatalog(discovered);
    }

    /** Every registered scene, ordered by id. */
    public List<BehaviorScene> all() {
        return scenes;
    }

    /** Selects all scenes or a comma-separated set while preserving catalog order. */
    public List<BehaviorScene> select(String selector) {
        if (selector == null || selector.isBlank()
                || selector.trim().equalsIgnoreCase("all")) {
            return scenes;
        }
        Set<String> requested = new LinkedHashSet<>();
        for (String token : selector.split(",")) {
            String id = token.trim().toLowerCase(Locale.ROOT);
            if (!id.isEmpty()) requested.add(id);
        }
        Set<String> unknown = new LinkedHashSet<>(requested);
        unknown.removeAll(byId.keySet());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unknown behaviour scenes " + unknown
                    + "; available scenes: " + byId.keySet());
        }
        return scenes.stream().filter(scene -> requested.contains(scene.id())).toList();
    }

    private static void requireId(String id) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9-]*")) {
            throw new IllegalArgumentException("behaviour scene id must be lowercase kebab-case: "
                    + id);
        }
    }
}
