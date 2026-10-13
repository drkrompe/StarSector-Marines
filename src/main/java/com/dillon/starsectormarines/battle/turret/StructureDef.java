package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.shield.DomeSpec;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Immutable static-emplacement platform definition. It owns durability and
 * physical footprint separately from the mount and gun installed on it.
 */
public final class StructureDef {

    public final String id;
    public final String displayName;
    public final float maxStructure;
    public final float armorCapacity;
    public final float armorRating;
    public final float radius;
    public final float hitHalfHeight;
    public final int footprintCellsX;
    public final int footprintCellsY;
    public final float forceScore;
    public final String mountId;
    public final TurretMountDef mount;
    /** The dome this structure projects, or null for one that is not a shield generator. */
    public final DomeSpec dome;
    /**
     * Id of the ship-grade gun set this structure carries as a fortress battery, or null for an ordinary
     * emplacement. A structure naming one is registered as a battery when it is spawned.
     */
    public final String batteryLoadoutId;
    /** Cells around the anchor within which standing marks the battery's fire control as taken; zero for a non-battery. */
    public final float fireControlRadius;
    /** The painted base and animated overlay layers, or null for a structure drawn as a flat pad under its mount. */
    public final StructureArt art;

    private StructureDef(String id, String displayName,
                         float maxStructure, float armorCapacity, float armorRating,
                         float radius, float hitHalfHeight,
                         int footprintCellsX, int footprintCellsY, float forceScore,
                         String mountId, TurretMountDef mount,
                         String batteryLoadoutId, float fireControlRadius, DomeSpec dome,
                         StructureArt art) {
        this.dome = dome;
        this.art = art;
        this.id = id;
        this.displayName = displayName;
        this.maxStructure = maxStructure;
        this.armorCapacity = armorCapacity;
        this.armorRating = armorRating;
        this.radius = radius;
        this.hitHalfHeight = hitHalfHeight;
        this.footprintCellsX = footprintCellsX;
        this.footprintCellsY = footprintCellsY;
        this.forceScore = forceScore;
        this.mountId = mountId;
        this.mount = mount;
        this.batteryLoadoutId = batteryLoadoutId;
        this.fireControlRadius = fireControlRadius;
    }

    /** Whether this structure is a fortress battery: it carries ship-grade guns and a fire-control point. */
    public boolean isBattery() {
        return batteryLoadoutId != null;
    }

    /** Whether this structure projects a planetary shield dome. */
    public boolean isShieldGenerator() {
        return dome != null;
    }

    /** Whether this structure stands on more than its anchor cell. */
    public boolean isMultiCell() {
        return footprintCellsX != 1 || footprintCellsY != 1;
    }

    static StructureDef parse(JSONObject json, TurretMountDef mount) throws JSONException {
        String id = TurretMountDef.requireText(json, "id");
        String mountId = TurretMountDef.requireText(json, "mount");
        if (!mountId.equals(mount.id)) {
            throw new JSONException("Structure '" + id + "' resolved the wrong mount '"
                    + mount.id + "' for reference '" + mountId + "'");
        }
        JSONObject catalog = json.getJSONObject("catalog");
        JSONObject durability = json.getJSONObject("durability");
        JSONObject physics = json.getJSONObject("physics");
        float maxStructure = (float) durability.getDouble("structure");
        float armorCapacity = (float) durability.getDouble("armorCapacity");
        float armorRating = (float) durability.getDouble("armorRating");
        float radius = (float) physics.getDouble("radius");
        float hitHalfHeight = (float) physics.getDouble("hitHalfHeight");
        float forceScore = (float) json.getDouble("forceScore");
        TurretMountDef.requirePositiveFinite(maxStructure, id, "structure");
        TurretMountDef.requirePositiveFinite(armorCapacity, id, "armorCapacity");
        TurretMountDef.requirePositiveFinite(armorRating, id, "armorRating");
        TurretMountDef.requirePositiveFinite(radius, id, "radius");
        TurretMountDef.requirePositiveFinite(hitHalfHeight, id, "hitHalfHeight");
        TurretMountDef.requirePositiveFinite(forceScore, id, "forceScore");
        JSONArray footprint = physics.getJSONArray("footprintCells");
        if (footprint.length() != 2) {
            throw new JSONException("Structure '" + id + "' footprintCells must contain [width, height]");
        }
        int footprintCellsX = footprint.getInt(0);
        int footprintCellsY = footprint.getInt(1);
        // Odd on each axis, so the anchor cell is the footprint's centre and the
        // body's circle shares it. One by one is the ordinary emplacement.
        if (footprintCellsX < 1 || footprintCellsY < 1
                || footprintCellsX % 2 == 0 || footprintCellsY % 2 == 0) {
            throw new JSONException("Structure '" + id
                    + "' footprintCells must be odd positive cell counts, got ["
                    + footprintCellsX + ", " + footprintCellsY + "]");
        }
        JSONObject domeJson = json.optJSONObject("dome");
        DomeSpec dome = domeJson != null ? DomeSpec.parse(domeJson) : null;
        String batteryLoadoutId = null;
        float fireControlRadius = 0f;
        JSONObject battery = json.optJSONObject("battery");
        if (battery != null) {
            batteryLoadoutId = TurretMountDef.requireText(battery, "loadout");
            fireControlRadius = (float) battery.getDouble("fireControlRadius");
            TurretMountDef.requirePositiveFinite(fireControlRadius, id, "fireControlRadius");
            if (footprintCellsX < 3 || footprintCellsY < 3) {
                throw new JSONException("Structure '" + id + "' is a battery and needs a footprint of at least 3x3");
            }
        }
        JSONObject artJson = json.optJSONObject("art");
        StructureArt art = artJson != null ? StructureArt.parse(id, artJson) : null;
        return new StructureDef(
                id, TurretMountDef.requireText(catalog, "displayName"),
                maxStructure, armorCapacity, armorRating, radius, hitHalfHeight,
                footprintCellsX, footprintCellsY, forceScore, mountId, mount,
                batteryLoadoutId, fireControlRadius, dome, art);
    }
}
