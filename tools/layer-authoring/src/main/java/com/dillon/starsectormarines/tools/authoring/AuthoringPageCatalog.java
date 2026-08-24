package com.dillon.starsectormarines.tools.authoring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/** Deterministically ordered registry of authoring pages discovered on the tool classpath. */
public final class AuthoringPageCatalog {

    private final List<AuthoringPageProvider> providers;

    public AuthoringPageCatalog(Collection<? extends AuthoringPageProvider> providers) {
        if (providers == null) throw new IllegalArgumentException("authoring page providers are required");
        List<AuthoringPageProvider> ordered = new ArrayList<>(providers);
        if (ordered.stream().anyMatch(provider -> provider == null)) {
            throw new IllegalArgumentException("authoring page provider may not be null");
        }
        for (AuthoringPageProvider provider : ordered) {
            requireId(provider.id());
            if (provider.label() == null || provider.label().isBlank()) {
                throw new IllegalArgumentException("authoring page '" + provider.id()
                        + "' requires a label");
            }
        }
        ordered.sort((left, right) -> left.id().compareTo(right.id()));
        Map<String, AuthoringPageProvider> indexed = new LinkedHashMap<>();
        for (AuthoringPageProvider provider : ordered) {
            AuthoringPageProvider previous = indexed.put(provider.id(), provider);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate authoring page id '"
                        + provider.id() + "'");
            }
        }
        this.providers = List.copyOf(ordered);
    }

    public static AuthoringPageCatalog discover() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return discover(loader != null ? loader : AuthoringPageCatalog.class.getClassLoader());
    }

    public static AuthoringPageCatalog discover(ClassLoader loader) {
        if (loader == null) throw new IllegalArgumentException("class loader is required");
        List<AuthoringPageProvider> discovered = new ArrayList<>();
        ServiceLoader.load(AuthoringPageProvider.class, loader).forEach(discovered::add);
        return new AuthoringPageCatalog(discovered);
    }

    public List<AuthoringPageProvider> providers() {
        return providers;
    }

    private static void requireId(String id) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9-]*")) {
            throw new IllegalArgumentException(
                    "authoring page id must be lowercase kebab-case: " + id);
        }
    }
}
