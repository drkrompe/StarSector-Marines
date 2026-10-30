package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.render2d.texture.ModTextures;
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
    /** The fortress battery: a multi-cell structure carrying ship-grade guns. Its art is still a placeholder. */
    public static final String FORTRESS_BATTERY_STRUCTURE_ID = "structure.fortress-battery-placeholder";
    /** The fort aspect cores: a console, a store and a plant, each a three-by-three structure that can be wrecked. */
    public static final String FORT_FIRE_CONTROL_STRUCTURE_ID = "structure.fort-fire-control";
    public static final String FORT_MAGAZINE_STRUCTURE_ID = "structure.fort-magazine";
    public static final String FORT_POWER_STRUCTURE_ID = "structure.fort-power";
    public static final String FORT_CONTROL_ROOM_STRUCTURE_ID = "structure.fort-control-room";
    /** The plotting console of a fort's optional anti-air yard. */
    public static final String FORT_AIR_CONTROL_STRUCTURE_ID = "structure.fort-air-control";
    public static final String ANTI_AIR_STRUCTURE_ID = "structure.turret-anti-air-flak";
    public static final String RIDGE_AA_STRUCTURE_ID = "structure.ridge-aa";
    /** The fort anti-air aspect's bastion batteries: long-reach, thirty-round missile salvos. */
    public static final String AA_LOCUST_STRUCTURE_ID = "structure.aa-locust";
    /** The fort anti-air aspect's close-in towers at the control room door: guided four-round salvos. */
    public static final String AA_SWARMER_STRUCTURE_ID = "structure.aa-swarmer";
    public static final String HEAVY_MG_STRUCTURE_ID = "structure.turret-heavy-mg";
    /** A boat's twin pulse laser: the hover scout's emitter on a door ring. Fires on flux, no magazine. */
    public static final String BOAT_PULSE_STRUCTURE_ID = "structure.boat-pulse";
    /** A boat's burst laser: the laser tank's emitter, small enough for a door ring; it tracks fliers. */
    public static final String BOAT_BURST_LASER_STRUCTURE_ID = "structure.boat-burst-laser";
    /** A boat's SRM-5: three salvos a sortie, a hard opening strike on the run in. */
    public static final String BOAT_SRM_STRUCTURE_ID = "structure.boat-srm";
    /** A boat's LRM-5: two long salvos a sortie, fired from well out. */
    public static final String BOAT_LRM_STRUCTURE_ID = "structure.boat-lrm";

    public static final String TANK_CANNON_STRUCTURE_ID = "structure.tank-twin-cannon";
    /** The laser tank's burst laser: a starship's point-defence emitter turned on the ground. */
    public static final String TANK_BURST_LASER_STRUCTURE_ID = "structure.tank-burst-laser";
    /** The PD laser tank's emitter: the plain PD laser, a steady lance where the burst laser fires in bursts. */
    /** The hover scout's twin pulse emitter, a vehicle mount of the Wisp drone-emitter family. */
    public static final String HOVER_PULSE_STRUCTURE_ID = "structure.hover-pulse";
    /** The LRM carrier's twin rail launcher: the mech anti-armor LRM rack on a vehicle turntable. */
    public static final String VEHICLE_LRM_STRUCTURE_ID = "structure.vehicle-lrm";
    /** The mortar carrier's roof mortar: an indirect-fire tube on a vehicle turntable, a steady bomb every few seconds. */
    public static final String VEHICLE_MORTAR_STRUCTURE_ID = "structure.vehicle-mortar";
    /** The SRM technical's pintle pod: the mech anti-armor SRM rack on a pickup's ring. */
    public static final String VEHICLE_SRM_STRUCTURE_ID = "structure.vehicle-srm";

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
        for (StructureDef structure : structuresById.values()) {
            for (String built : structure.builds.values()) {
                if (!structuresById.containsKey(built)) {
                    throw new IllegalStateException("Structure '" + structure.id + "' is built as unknown structure '"
                            + built + "'");
                }
            }
        }
        for (TurretMountDef mount : mountsById.values()) {
            validateAsset(mount.id, "sprite", mount.spritePath);
            validateAsset(mount.id, "recoilSprite", mount.recoilSpritePath);
            validateAsset(mount.weapon.id, "projectileSprite", mount.weapon.projectileSpritePath);
        }
        for (StructureDef structure : structuresById.values()) {
            if (structure.buildingArt != null) {
                for (String path : structure.buildingArt.spritePaths()) validateAsset(structure.id, "buildingArt", path);
            }
            if (structure.gatehouseArt != null) {
                for (String path : structure.gatehouseArt.spritePaths()) validateAsset(structure.id, "gatehouseArt", path);
            }
        }
    }

    private static void validateAsset(String ownerId, String field, String path) {
        if (path == null) return;
        try (InputStream ignored = Global.getSettings().openStream(ModTextures.file(path))) {
            // Opening the stream is the load-time existence check; rendering remains lazy.
        } catch (Exception e) {
            throw new IllegalStateException("Definition '" + ownerId + "' field '" + field
                    + "' references missing asset '" + path + "'", e);
        }
    }
}
