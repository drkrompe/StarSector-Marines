package com.dillon.starsectormarines.catalog;

/** Exact provider and resource path for one contributed catalog. */
public record CatalogSource(String modId, String path) {

    public CatalogSource {
        if (modId == null || modId.isBlank() || path == null || path.isBlank()) {
            throw new IllegalArgumentException("Catalog sources require a mod id and path");
        }
    }

    public static CatalogSource unspecified(String path) {
        return new CatalogSource("<direct>", path);
    }

    public String describe() {
        return "mod '" + modId + "' at '" + path + "'";
    }
}
