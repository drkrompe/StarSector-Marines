package com.dillon.starsectormarines.catalog;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.SettingsAPI;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Additive catalog contributions declared by one enabled mod.
 *
 * <p>The manifest path is deliberately fixed while every catalog path inside it
 * is explicit. This makes discovery cheap and deterministic without relying on
 * filesystem glob order. Enabled-mod order is retained for diagnostics and
 * iteration; duplicate stable ids remain errors rather than overrides.
 */
public record MarineCatalogManifest(
        List<CatalogFile> tilesets,
        List<CatalogFile> tileMappings,
        List<CatalogFile> weapons,
        List<CatalogFile> specialEquipment,
        List<CatalogFile> armor,
        List<CatalogFile> groundRosters,
        List<CatalogFile> equipmentTemplates,
        List<CatalogFile> factionEquipment) {

    public static final int SCHEMA_VERSION = 1;
    public static final String MANIFEST_PATH = "data/marines/starsector-marines.catalog.json";

    public MarineCatalogManifest {
        tilesets = List.copyOf(tilesets);
        tileMappings = List.copyOf(tileMappings);
        weapons = List.copyOf(weapons);
        specialEquipment = List.copyOf(specialEquipment);
        armor = List.copyOf(armor);
        groundRosters = List.copyOf(groundRosters);
        equipmentTemplates = List.copyOf(equipmentTemplates);
        factionEquipment = List.copyOf(factionEquipment);
    }

    /** Discovers the fixed manifest in every enabled mod, preserving game load order. */
    public static MarineCatalogManifest discoverEnabled() {
        SettingsAPI settings = Global.getSettings();
        List<String> enabledModIds = new ArrayList<>();
        for (ModSpecAPI mod : settings.getModManager().getEnabledModsCopy()) {
            enabledModIds.add(mod.getId());
        }
        return discoverEnabled(enabledModIds, settings::loadJSON);
    }

    static MarineCatalogManifest discoverEnabled(List<String> enabledModIds,
                                                  ManifestReader manifestReader) {
        Builder result = new Builder();
        for (String modId : enabledModIds) {
            try {
                result.add(parse(modId, manifestReader.load(MANIFEST_PATH, modId)));
            } catch (Exception failure) {
                if (isAbsentManifest(failure)) continue;
                throw new IllegalStateException("Failed to load marine catalog manifest for mod '"
                        + modId + "' at '" + MANIFEST_PATH + "'", failure);
            }
        }
        return result.build();
    }

    private static boolean isAbsentManifest(Exception failure) {
        if (failure instanceof FileNotFoundException) return true;
        // Starsector 0.98a's provider-scoped SettingsAPI loader declares IOException,
        // but its missing-resource path actually throws this untyped RuntimeException.
        // Match the complete resource-specific shape so malformed JSON and unrelated
        // runtime failures remain launch-blocking.
        String message = failure.getMessage();
        return failure.getClass() == RuntimeException.class
                && message != null
                && message.startsWith("Error loading [" + MANIFEST_PATH
                        + "] resource, not found in [")
                && message.endsWith("]");
    }

    @FunctionalInterface
    interface ManifestReader {
        JSONObject load(String path, String modId) throws IOException, JSONException;
    }

    /** Parses one manifest without touching game globals, for tests and authoring tools. */
    public static MarineCatalogManifest parse(String modId, JSONObject root) throws JSONException {
        int version = root.getInt("schemaVersion");
        if (version != SCHEMA_VERSION) {
            throw new JSONException("Unsupported marine catalog manifest schemaVersion " + version
                    + " in mod '" + modId + "'; expected " + SCHEMA_VERSION);
        }
        return new MarineCatalogManifest(
                paths(modId, root.optJSONArray("tilesets")),
                paths(modId, root.optJSONArray("tileMappings")),
                paths(modId, root.optJSONArray("weapons")),
                paths(modId, root.optJSONArray("specialEquipment")),
                paths(modId, root.optJSONArray("armor")),
                paths(modId, root.optJSONArray("groundRosters")),
                paths(modId, root.optJSONArray("equipmentTemplates")),
                paths(modId, root.optJSONArray("factionEquipment")));
    }

    private static List<CatalogFile> paths(String modId, JSONArray array) throws JSONException {
        if (array == null) return List.of();
        List<CatalogFile> result = new ArrayList<>(array.length());
        for (int index = 0; index < array.length(); index++) {
            String path = normalizePath(array.getString(index), modId);
            result.add(new CatalogFile(new CatalogSource(modId, path)));
        }
        return result;
    }

    private static String normalizePath(String path, String modId) throws JSONException {
        String normalized = path == null ? "" : path.trim().replace('\\', '/');
        if (normalized.isEmpty() || normalized.startsWith("/")
                || normalized.matches("^[A-Za-z]:.*")
                || normalized.equals("..") || normalized.startsWith("../")
                || normalized.contains("/../")) {
            throw new JSONException("Mod '" + modId
                    + "' declares an unsafe marine catalog path '" + path + "'");
        }
        return normalized;
    }

    /** One exact resource owned by one exact enabled mod. */
    public record CatalogFile(CatalogSource source) {
        public JSONObject loadJson() {
            try {
                return Global.getSettings().loadJSON(source.path(), source.modId());
            } catch (Exception failure) {
                throw new IllegalStateException("Failed to load catalog " + source.describe(), failure);
            }
        }
    }

    private static final class Builder {
        private final List<CatalogFile> tilesets = new ArrayList<>();
        private final List<CatalogFile> tileMappings = new ArrayList<>();
        private final List<CatalogFile> weapons = new ArrayList<>();
        private final List<CatalogFile> specialEquipment = new ArrayList<>();
        private final List<CatalogFile> armor = new ArrayList<>();
        private final List<CatalogFile> groundRosters = new ArrayList<>();
        private final List<CatalogFile> equipmentTemplates = new ArrayList<>();
        private final List<CatalogFile> factionEquipment = new ArrayList<>();

        void add(MarineCatalogManifest manifest) {
            tilesets.addAll(manifest.tilesets);
            tileMappings.addAll(manifest.tileMappings);
            weapons.addAll(manifest.weapons);
            specialEquipment.addAll(manifest.specialEquipment);
            armor.addAll(manifest.armor);
            groundRosters.addAll(manifest.groundRosters);
            equipmentTemplates.addAll(manifest.equipmentTemplates);
            factionEquipment.addAll(manifest.factionEquipment);
        }

        MarineCatalogManifest build() {
            return new MarineCatalogManifest(tilesets, tileMappings,
                    weapons, specialEquipment, armor,
                    groundRosters, equipmentTemplates, factionEquipment);
        }
    }
}
