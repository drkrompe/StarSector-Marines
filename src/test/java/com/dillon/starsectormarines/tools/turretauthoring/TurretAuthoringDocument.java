package com.dillon.starsectormarines.tools.turretauthoring;

import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.DefensePostLayoutRegistry;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.tools.layerauthoring.JsonTextPatcher;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Mutable cross-catalog document used only by the standalone authoring UI. */
public final class TurretAuthoringDocument {

    public static final Path WEAPON_PATH =
            Path.of("mod/data/marines/turret-weapons.weapon.json");
    public static final Path TURRET_PATH =
            Path.of("mod/data/marines/turret-emplacements.turret.json");
    public static final Path LAYOUT_PATH =
            Path.of("mod/data/marines/defense-post-layouts.layout.json");

    private final Path projectRoot;
    private JSONObject weapons;
    private JSONObject turrets;
    private JSONObject layouts;
    private String weaponTemplate;
    private String turretTemplate;
    private String savedSnapshot;

    private TurretAuthoringDocument(Path projectRoot, JSONObject weapons,
                                    JSONObject turrets, JSONObject layouts,
                                    String weaponTemplate, String turretTemplate) throws JSONException {
        this.projectRoot = projectRoot;
        this.weapons = weapons;
        this.turrets = turrets;
        this.layouts = layouts;
        this.weaponTemplate = weaponTemplate;
        this.turretTemplate = turretTemplate;
        validateCatalogs();
        savedSnapshot = snapshot();
    }

    public static TurretAuthoringDocument load(Path projectRoot)
            throws IOException, JSONException {
        Path root = projectRoot.toAbsolutePath().normalize();
        String weaponSource = Files.readString(root.resolve(WEAPON_PATH), StandardCharsets.UTF_8);
        String turretSource = Files.readString(root.resolve(TURRET_PATH), StandardCharsets.UTF_8);
        return new TurretAuthoringDocument(root,
                new JSONObject(weaponSource), new JSONObject(turretSource),
                new JSONObject(Files.readString(root.resolve(LAYOUT_PATH), StandardCharsets.UTF_8)),
                weaponSource, turretSource);
    }

    static TurretAuthoringDocument parse(Path projectRoot, String weaponJson,
                                         String turretJson, String layoutJson) throws JSONException {
        return new TurretAuthoringDocument(projectRoot.toAbsolutePath().normalize(),
                new JSONObject(weaponJson), new JSONObject(turretJson),
                new JSONObject(layoutJson), weaponJson, turretJson);
    }

    public List<TurretSelection> selections() throws JSONException {
        List<TurretSelection> result = new ArrayList<>();
        JSONArray structures = turrets.getJSONArray("structures");
        for (int index = 0; index < structures.length(); index++) {
            JSONObject structure = structures.getJSONObject(index);
            JSONObject mount = find(turrets.getJSONArray("mounts"),
                    structure.getString("mount"));
            JSONObject weapon = find(weapons.getJSONArray("weapons"),
                    mount.getString("weapon"));
            result.add(new TurretSelection(structure.getString("id"),
                    structure.getJSONObject("catalog").getString("displayName"),
                    weapon, mount, structure));
        }
        return List.copyOf(result);
    }

    public TurretSelection selection(String structureId) throws JSONException {
        for (TurretSelection selection : selections()) {
            if (selection.structureId().equals(structureId)) return selection;
        }
        throw new JSONException("Unknown turret structure '" + structureId + "'");
    }

    public TurretMountDef previewMount(String structureId) throws JSONException {
        Catalogs catalogs = validateCatalogs();
        StructureDef structure = catalogs.turrets().getStructure(structureId);
        if (structure == null) throw new JSONException("Unknown turret structure '" + structureId + "'");
        return structure.mount;
    }

    public Catalogs validateCatalogs() throws JSONException {
        WeaponRegistry weaponRegistry = new WeaponRegistry();
        weaponRegistry.ingest(copy(weapons));
        TurretCatalogRegistry turretRegistry = new TurretCatalogRegistry();
        turretRegistry.ingest(copy(turrets), weaponRegistry);
        DefensePostLayoutRegistry layoutRegistry = new DefensePostLayoutRegistry();
        layoutRegistry.ingest(copy(layouts), turretRegistry);
        layoutRegistry.validateCompleteness();
        return new Catalogs(weaponRegistry, turretRegistry, layoutRegistry);
    }

    public List<String> validate() {
        try {
            validateCatalogs();
            return List.of();
        } catch (Exception failure) {
            return List.of(failure.getMessage() != null
                    ? failure.getMessage() : failure.getClass().getSimpleName());
        }
    }

    public String snapshot() throws JSONException {
        return new JSONObject().put("weapons", copy(weapons))
                .put("turrets", copy(turrets)).put("layouts", copy(layouts)).toString();
    }

    public void restore(String snapshot) throws JSONException {
        JSONObject root = new JSONObject(snapshot);
        weapons = copy(root.getJSONObject("weapons"));
        turrets = copy(root.getJSONObject("turrets"));
        layouts = copy(root.getJSONObject("layouts"));
    }

    public boolean dirty() throws JSONException {
        return !snapshot().equals(savedSnapshot);
    }

    public void markSaved() throws JSONException {
        savedSnapshot = snapshot();
    }

    public void save() throws IOException, JSONException {
        List<String> errors = validate();
        if (!errors.isEmpty()) throw new IllegalStateException(String.join("\n", errors));
        Prepared weapon = null;
        Prepared turret = null;
        Prepared layout = null;
        try {
            weapon = prepare(WEAPON_PATH, weapons, weaponTemplate);
            turret = prepare(TURRET_PATH, turrets, turretTemplate);
            layout = prepare(LAYOUT_PATH, layouts, null);
            weapon.replace();
            turret.replace();
            layout.replace();
        } catch (IOException failure) {
            rollback(failure, layout, turret, weapon);
            throw failure;
        } finally {
            cleanup(layout, turret, weapon);
        }
        weaponTemplate = weapon.output;
        turretTemplate = turret.output;
        markSaved();
    }

    private static void rollback(IOException failure, Prepared... prepared) {
        for (Prepared item : prepared) {
            if (item == null) continue;
            try {
                item.rollback();
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    private static void cleanup(Prepared... prepared) {
        for (Prepared item : prepared) {
            if (item == null) continue;
            try {
                item.cleanup();
            } catch (IOException ignored) {
                // A stale UUID temp is safer than reporting a completed save as failed.
            }
        }
    }

    public Path weaponPath() { return projectRoot.resolve(WEAPON_PATH); }

    public Path turretPath() { return projectRoot.resolve(TURRET_PATH); }

    public Path layoutPath() { return projectRoot.resolve(LAYOUT_PATH); }

    public List<JSONObject> layoutObjects() throws JSONException {
        JSONArray array = layouts.getJSONArray("layouts");
        List<JSONObject> result = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            result.add(array.getJSONObject(index));
        }
        return List.copyOf(result);
    }

    public List<String> structureIds() throws JSONException {
        JSONArray array = turrets.getJSONArray("structures");
        List<String> result = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            result.add(array.getJSONObject(index).getString("id"));
        }
        return List.copyOf(result);
    }

    public JSONObject layoutObject(String id) throws JSONException {
        return find(layouts.getJSONArray("layouts"), id);
    }

    public JSONObject duplicateLargeLayout(String sourceId, String variant)
            throws JSONException {
        if (variant == null || variant.isBlank()) throw new JSONException("variant is required");
        JSONObject source = layoutObject(sourceId);
        if (!"large".equalsIgnoreCase(source.getString("tier"))) {
            throw new JSONException("only LARGE supports multiple layout variants");
        }
        String cleanVariant = variant.trim().toLowerCase().replace(' ', '-');
        String id = "layout.defense-post-large-" + cleanVariant;
        for (JSONObject existing : layoutObjects()) {
            if (id.equals(existing.getString("id"))
                    || cleanVariant.equals(existing.getString("variant"))) {
                throw new JSONException("layout id or LARGE variant already exists: " + cleanVariant);
            }
        }
        JSONObject copy = new JSONObject(source.toString());
        copy.put("id", id).put("variant", cleanVariant);
        layouts.getJSONArray("layouts").put(copy);
        return copy;
    }

    private Prepared prepare(Path relative, JSONObject json, String template)
            throws IOException, JSONException {
        Path target = projectRoot.resolve(relative).normalize();
        if (!target.startsWith(projectRoot)) throw new IOException("Catalog escapes project root");
        byte[] original = Files.readAllBytes(target);
        Path temporary = target.resolveSibling(target.getFileName() + "."
                + UUID.randomUUID() + ".tmp");
        String output;
        if (template != null) {
            try {
                output = JsonTextPatcher.patch(template, json);
            } catch (IllegalArgumentException structuralChange) {
                output = json.toString(2) + System.lineSeparator();
            }
        } else {
            output = json.toString(2) + System.lineSeparator();
        }
        Files.writeString(temporary, output, StandardCharsets.UTF_8);
        return new Prepared(target, temporary, original, output);
    }

    private static JSONObject find(JSONArray array, String id) throws JSONException {
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.getJSONObject(index);
            if (id.equals(item.getString("id"))) return item;
        }
        throw new JSONException("Unknown referenced id '" + id + "'");
    }

    private static JSONObject copy(JSONObject source) throws JSONException {
        return new JSONObject(source.toString());
    }

    public record TurretSelection(String structureId, String label,
                                  JSONObject weapon, JSONObject mount,
                                  JSONObject structure) {
        @Override public String toString() { return label; }
    }

    public record Catalogs(WeaponRegistry weapons, TurretCatalogRegistry turrets,
                           DefensePostLayoutRegistry layouts) {}

    private final class Prepared {
        private final Path target;
        private final Path temporary;
        private final byte[] original;
        private final String output;
        private boolean replaced;

        private Prepared(Path target, Path temporary, byte[] original, String output) {
            this.target = target;
            this.temporary = temporary;
            this.original = original;
            this.output = output;
        }

        private void replace() throws IOException {
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            replaced = true;
        }

        private void rollback() throws IOException {
            if (!replaced) return;
            replaceWithBytes(original, ".rollback");
        }

        private void replaceWithBytes(byte[] bytes, String suffix) throws IOException {
            Path staged = target.resolveSibling(target.getFileName() + "."
                    + UUID.randomUUID() + suffix);
            try {
                Files.write(staged, bytes);
                try {
                    Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(staged);
            }
        }

        private void cleanup() throws IOException {
            Files.deleteIfExists(temporary);
        }
    }
}
