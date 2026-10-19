package com.dillon.starsectormarines.battle.warship;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looks up how a vanilla weapon is drawn, by weapon id.
 *
 * <p>The spec comes through {@link VanillaData}, so in the game a mod's own
 * weapon or a re-tuned vanilla one resolves the same way; an unresolvable weapon
 * is simply absent rather than an error, and a beam that cannot be looked up
 * draws as the plain fallback.
 */
public final class VanillaWeaponLooks {

    private static final Logger LOG = Global.getLogger(VanillaWeaponLooks.class);

    private static final VanillaBeamLook ABSENT = new VanillaBeamLook(
            "", null, null, null, 0f, null, null, 0f, 0f, null, null, null, null);

    private static final Map<String, VanillaBeamLook> CACHE = new ConcurrentHashMap<>();

    private static final VanillaMountLook NO_MOUNT = VanillaMountLook.fromSpec("", new JSONObject(), null);
    private static final Map<String, VanillaMountLook> MOUNTS = new ConcurrentHashMap<>();

    private VanillaWeaponLooks() {}

    /** Forgets what was cached, for a tool that has just changed where data is read from. */
    public static void clearCache() {
        CACHE.clear();
        MOUNTS.clear();
    }

    /** The beam look of {@code weaponId}, or {@code null} for a non-beam or unknown weapon. */
    public static VanillaBeamLook beam(String weaponId) {
        if (weaponId == null || weaponId.isEmpty()) return null;
        VanillaBeamLook look = CACHE.computeIfAbsent(weaponId, VanillaWeaponLooks::resolve);
        return look == ABSENT ? null : look;
    }

    private static VanillaBeamLook resolve(String weaponId) {
        try {
            VanillaBeamLook look = VanillaBeamLook.fromSpec(weaponId, VanillaData.json("data/weapons/" + weaponId + ".wpn"));
            return look != null ? look : ABSENT;
        } catch (Exception e) {
            LOG.warn("VanillaWeaponLooks: " + weaponId + " — "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return ABSENT;
        }
    }

    /** How {@code weaponId} is drawn on its mount, every sprite layer its spec names; {@code null} when it cannot be read. */
    public static VanillaMountLook mount(String weaponId) {
        if (weaponId == null || weaponId.isEmpty()) return null;
        VanillaMountLook look = MOUNTS.computeIfAbsent(weaponId, VanillaWeaponLooks::resolveMount);
        return look == NO_MOUNT ? null : look;
    }

    private static VanillaMountLook resolveMount(String weaponId) {
        try {
            JSONObject wpn = VanillaData.json("data/weapons/" + weaponId + ".wpn");
            String projectileId = wpn == null ? null : wpn.optString("projectileSpecId", null);
            JSONObject proj = projectileId == null || projectileId.isBlank() ? null : projectile(projectileId);
            VanillaMountLook look = VanillaMountLook.fromSpec(weaponId, wpn, proj);
            return look != null ? look : NO_MOUNT;
        } catch (Exception e) {
            LOG.warn("VanillaWeaponLooks: mount " + weaponId + " — "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return NO_MOUNT;
        }
    }

    /** A projectile spec by id, as the weapon table resolves it (some missiles are filed under {@code <id>_srm}). */
    private static JSONObject projectile(String id) {
        JSONObject proj = optional("data/weapons/proj/" + id + ".proj");
        return proj != null ? proj : optional("data/weapons/proj/" + id + "_srm.proj");
    }

    private static JSONObject optional(String path) {
        try {
            return VanillaData.json(path);
        } catch (Exception e) {
            return null;
        }
    }
}
