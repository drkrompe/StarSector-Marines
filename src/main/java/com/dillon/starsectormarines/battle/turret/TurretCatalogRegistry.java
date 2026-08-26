package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fail-loud registry for data-authored turret mounts and static platforms. */
public final class TurretCatalogRegistry {

    private static final Logger LOG = Global.getLogger(TurretCatalogRegistry.class);

    public static final List<String> BUILTIN_CATALOGS = List.of(
            "data/marines/turret-emplacements.turret.json");

    public static final String VULCAN_STRUCTURE_ID = "structure.turret-vulcan";
    public static final String ARBALEST_STRUCTURE_ID = "structure.turret-arbalest";
    public static final String HEAVY_MORTAR_STRUCTURE_ID = "structure.turret-heavy-mortar";
    public static final String DUAL_FLAK_STRUCTURE_ID = "structure.turret-dual-flak";
    public static final String HEPHAESTUS_STRUCTURE_ID = "structure.turret-hephaestus";
    public static final String GRENADE_LAUNCHER_STRUCTURE_ID =
            "structure.turret-grenade-launcher";
    public static final String LOCUST_STRUCTURE_ID = "structure.turret-locust";
    public static final String HEAVY_MG_STRUCTURE_ID = "structure.turret-heavy-mg";

    private static volatile TurretCatalogRegistry installed;

    private final Map<String, TurretMountDef> mountsById = new LinkedHashMap<>();
    private final Map<String, StructureDef> structuresById = new LinkedHashMap<>();

    public static TurretCatalogRegistry installed() { return installed; }

    public static void install(TurretCatalogRegistry registry) { installed = registry; }

    /** Loads after {@link WeaponRegistry}, because every mount resolves a weapon id. */
    public static void loadBuiltins() {
        WeaponRegistry weapons = WeaponRegistry.installed();
        if (weapons == null) {
            throw new IllegalStateException("Weapon registry must be installed before turret catalogs");
        }
        TurretCatalogRegistry registry = new TurretCatalogRegistry();
        for (String path : BUILTIN_CATALOGS) {
            try {
                registry.ingest(Global.getSettings().loadJSON(path, true), weapons);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load turret catalog " + path, e);
            }
        }
        registry.validateAssets();
        install(registry);
        LOG.info("Turret catalog installed with " + registry.mountCount()
                + " mounts and " + registry.structureCount() + " structures");
    }

    /** Adds one catalog, resolving every weapon and mount reference immediately. */
    public void ingest(JSONObject root, WeaponRegistry weapons) throws JSONException {
        JSONArray mounts = root.getJSONArray("mounts");
        for (int i = 0; i < mounts.length(); i++) {
            JSONObject json = mounts.getJSONObject(i);
            String id = TurretMountDef.requireText(json, "id");
            String weaponId = TurretMountDef.requireText(json, "weapon");
            WeaponDef weapon = weapons.get(weaponId);
            if (weapon == null) {
                throw new JSONException("Turret mount '" + id
                        + "' references unknown weapon '" + weaponId + "'");
            }
            TurretMountDef def = TurretMountDef.parse(json, weapon);
            if (mountsById.put(def.id, def) != null) {
                throw new JSONException("Duplicate turret mount id '" + def.id + "'");
            }
        }

        JSONArray structures = root.getJSONArray("structures");
        for (int i = 0; i < structures.length(); i++) {
            JSONObject json = structures.getJSONObject(i);
            String id = TurretMountDef.requireText(json, "id");
            String mountId = TurretMountDef.requireText(json, "mount");
            TurretMountDef mount = mountsById.get(mountId);
            if (mount == null) {
                throw new JSONException("Structure '" + id
                        + "' references unknown turret mount '" + mountId + "'");
            }
            StructureDef def = StructureDef.parse(json, mount);
            if (structuresById.put(def.id, def) != null) {
                throw new JSONException("Duplicate structure id '" + def.id + "'");
            }
        }
    }

    public static TurretMountDef requireMount(String id) {
        TurretCatalogRegistry registry = requireInstalled(id);
        TurretMountDef def = registry.mountsById.get(id);
        if (def == null) {
            throw new IllegalStateException("Unknown turret mount id '" + id
                    + "'. Known ids: " + registry.mountsById.keySet());
        }
        return def;
    }

    public static StructureDef requireStructure(String id) {
        TurretCatalogRegistry registry = requireInstalled(id);
        StructureDef def = registry.structuresById.get(id);
        if (def == null) {
            throw new IllegalStateException("Unknown structure id '" + id
                    + "'. Known ids: " + registry.structuresById.keySet());
        }
        return def;
    }

    private static TurretCatalogRegistry requireInstalled(String id) {
        TurretCatalogRegistry registry = installed;
        if (registry == null) {
            throw new IllegalStateException("Turret catalog is not installed; cannot resolve '" + id + "'");
        }
        return registry;
    }

    public TurretMountDef getMount(String id) { return mountsById.get(id); }

    public StructureDef getStructure(String id) { return structuresById.get(id); }

    public Collection<TurretMountDef> mounts() { return mountsById.values(); }

    public Collection<StructureDef> structures() { return structuresById.values(); }

    public int mountCount() { return mountsById.size(); }

    public int structureCount() { return structuresById.size(); }

    private void validateAssets() {
        for (TurretMountDef mount : mountsById.values()) {
            validateAsset(mount.id, "sprite", mount.spritePath);
            validateAsset(mount.id, "recoilSprite", mount.recoilSpritePath);
            validateAsset(mount.weapon.id, "projectileSprite", mount.weapon.projectileSpritePath);
        }
    }

    private static void validateAsset(String ownerId, String field, String path) {
        if (path == null) return;
        try (InputStream ignored = Global.getSettings().openStream(path)) {
            // Opening the stream is the load-time existence check; rendering remains lazy.
        } catch (Exception e) {
            throw new IllegalStateException("Definition '" + ownerId + "' field '" + field
                    + "' references missing asset '" + path + "'", e);
        }
    }
}
